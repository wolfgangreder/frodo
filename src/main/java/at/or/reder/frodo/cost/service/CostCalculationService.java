package at.or.reder.frodo.cost.service;

import at.or.reder.frodo.cost.entity.GridFeeEntity;
import at.or.reder.frodo.cost.entity.HourlyEnergyEntity;
import at.or.reder.frodo.cost.entity.HourlyCostEntity;
import at.or.reder.frodo.cost.entity.MonthlyCostEntity;
import at.or.reder.frodo.cost.entity.TariffWindowEntity;
import at.or.reder.frodo.cost.repository.EnergyPriceRepository;
import at.or.reder.frodo.cost.repository.FixedCostRepository;
import at.or.reder.frodo.cost.repository.GridFeeRepository;
import at.or.reder.frodo.cost.repository.HourlyEnergyRepository;
import at.or.reder.frodo.cost.repository.HourlyCostRepository;
import at.or.reder.frodo.cost.repository.MonthlyCostRepository;
import at.or.reder.frodo.cost.repository.TariffWindowRepository;
import at.or.reder.frodo.cost.entity.EnergyPriceEntity;
import at.or.reder.frodo.cost.spi.FeeAppliesTo;
import at.or.reder.frodo.cost.spi.FeeType;
import at.or.reder.frodo.cost.spi.PriceDirection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Calculates hourly and monthly energy costs from grid energy data, provider prices,
 * tariff windows, and grid fees.
 *
 * <h3>Price resolution order (per direction)</h3>
 * <ol>
 *   <li>Matching {@link TariffWindowEntity} (highest priority wins) → source = "TARIFF_WINDOW"</li>
 *   <li>Provider price from {@code FroEnergyPrice} for the hour → source = provider id</li>
 *   <li>No price found → warn, use 0.0, source = "UNKNOWN"</li>
 * </ol>
 *
 * <h3>Monthly update</h3>
 * <p>After every hourly upsert, all {@code FroHourlyCost} rows for the calendar month
 * are re-aggregated and the {@code FroMonthlyCost} row is updated in the same transaction.</p>
 */
@ApplicationScoped
public class CostCalculationService {

  private static final Logger LOG = Logger.getLogger(CostCalculationService.class);
  private static final String SOURCE_TARIFF_WINDOW = "TARIFF_WINDOW";
  private static final String SOURCE_UNKNOWN = "UNKNOWN";
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final BigDecimal HOURS_PER_MONTH = new BigDecimal("730");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  @Inject
  HourlyEnergyRepository hourlyEnergyRepository;

  @Inject
  HourlyCostRepository hourlyCostRepository;

  @Inject
  MonthlyCostRepository monthlyCostRepository;

  @Inject
  EnergyPriceRepository energyPriceRepository;

  @Inject
  TariffWindowRepository tariffWindowRepository;

  @Inject
  GridFeeRepository gridFeeRepository;

  @Inject
  FixedCostRepository fixedCostRepository;

  @Inject
  MeterRegistry meterRegistry;

  private Counter calcSuccess;
  private Counter calcFailure;

  void onStart(@Observes StartupEvent event) {
    calcSuccess = meterRegistry.counter(
      "frodo.cost.hourly_calculations_total", "status", "success");
    calcFailure = meterRegistry.counter(
      "frodo.cost.hourly_calculations_total", "status", "failure");
  }

  /**
   * Calculates and persists the hourly cost for the given hour.
   * Triggers a real-time monthly update after the hourly upsert.
   *
   * @param hourStart start of the hour to calculate
   */
  @Transactional
  public void calculateHourlyCost(LocalDateTime hourStart) {
    try {
      doCalculate(hourStart);
      calcSuccess.increment();
    } catch (Exception ex) {
      calcFailure.increment();
      LOG.errorf(ex, "Hourly cost calculation failed for %s", hourStart);
      throw ex;
    }
  }

  /**
   * Forces recalculation of all hours within a calendar month.
   * Used by the REST endpoint {@code POST /monthly/recalculate/{yearMonth}}.
   *
   * @param yearMonth year-month string (e.g. "2026-05")
   */
  @Transactional
  public void recalculateMonth(String yearMonth) {
    LocalDateTime from = LocalDate.parse(yearMonth + "-01").atStartOfDay();
    LocalDateTime to = from.plusMonths(1);
    List<HourlyEnergyEntity> hours = hourlyEnergyRepository.findByDateRange(from, to);
    LOG.infof("Recalculating %d hourly costs for %s", hours.size(), yearMonth);
    for (HourlyEnergyEntity energy : hours) {
      try {
        doCalculate(energy.hourStart);
      } catch (Exception ex) {
        LOG.errorf(ex, "Recalculation failed for hour %s", energy.hourStart);
      }
    }
  }

  // ---- internal ----------------------------------------------------------

  private void doCalculate(LocalDateTime hourStart) {
    Optional<HourlyEnergyEntity> energyOpt = hourlyEnergyRepository.findByHourStart(hourStart);
    if (energyOpt.isEmpty()) {
      LOG.debugf("No hourly energy data for %s, skipping cost calculation", hourStart);
      return;
    }
    HourlyEnergyEntity energy = energyOpt.get();

    // Resolve effective prices per direction
    PriceResolution importResolution = resolvePrice(PriceDirection.IMPORT, hourStart);
    PriceResolution exportResolution = resolvePrice(PriceDirection.EXPORT, hourStart);

    // Base costs: kWh * ct/kWh / 100 → EUR (scale 4)
    BigDecimal importCostEur = energy.importKwh
      .multiply(importResolution.priceCt())
      .divide(HUNDRED, 4, RoundingMode.HALF_UP);
    BigDecimal exportIncomeEur = energy.exportKwh
      .multiply(exportResolution.priceCt())
      .divide(HUNDRED, 4, RoundingMode.HALF_UP);

    // Grid fees
    List<GridFeeEntity> fees = gridFeeRepository.findActiveFeesForTime(hourStart);
    BigDecimal feeEur = fees.stream()
      .map(fee -> calcFee(fee, energy, importResolution.priceCt(), exportResolution.priceCt()))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal netCostEur = importCostEur.subtract(exportIncomeEur).add(feeEur);

    // Upsert FroHourlyCost
    HourlyCostEntity cost = new HourlyCostEntity();
    cost.hourStart = hourStart;
    cost.hourEnd = energy.hourEnd;
    cost.importKwh = energy.importKwh;
    cost.exportKwh = energy.exportKwh;
    cost.priceImportCt = importResolution.priceCt();
    cost.priceExportCt = exportResolution.priceCt();
    cost.importPriceSource = importResolution.source();
    cost.exportPriceSource = exportResolution.source();
    cost.importCostEur = importCostEur;
    cost.exportIncomeEur = exportIncomeEur;
    cost.feeEur = feeEur;
    cost.netCostEur = netCostEur;

    hourlyCostRepository.upsert(cost);

    // Real-time monthly update
    updateMonthlyCost(hourStart);
  }

  private PriceResolution resolvePrice(PriceDirection direction, LocalDateTime hourStart) {
    // 1. Tariff window overrides all
    Optional<TariffWindowEntity> window =
      tariffWindowRepository.findMatchingWindow(direction, hourStart);
    if (window.isPresent()) {
      return new PriceResolution(window.get().priceCt, SOURCE_TARIFF_WINDOW);
    }

    // 2. Provider price
    Optional<EnergyPriceEntity> priceRow =
      energyPriceRepository.findForTime(hourStart);
    if (priceRow.isPresent()) {
      EnergyPriceEntity row = priceRow.get();
      if (direction == PriceDirection.IMPORT && row.priceImportCt != null) {
        return new PriceResolution(row.priceImportCt, row.importSource != null ? row.importSource : SOURCE_UNKNOWN);
      }
      if (direction == PriceDirection.EXPORT && row.priceExportCt != null) {
        return new PriceResolution(row.priceExportCt, row.exportSource != null ? row.exportSource : SOURCE_UNKNOWN);
      }
    }

    // 3. Fallback
    LOG.warnf("No %s price found for %s, using 0.0", direction, hourStart);
    return new PriceResolution(BigDecimal.ZERO, SOURCE_UNKNOWN);
  }

  private static BigDecimal calcFee(
      GridFeeEntity fee, HourlyEnergyEntity energy,
      BigDecimal importPriceCt, BigDecimal exportPriceCt) {
    return switch (fee.feeType) {
      case PERCENT -> {
        BigDecimal base = switch (fee.appliesTo) {
          case EXPORT -> energy.exportKwh.multiply(exportPriceCt)
            .divide(HUNDRED, 6, RoundingMode.HALF_UP);
          case IMPORT -> energy.importKwh.multiply(importPriceCt)
            .divide(HUNDRED, 6, RoundingMode.HALF_UP);
          case BOTH -> energy.exportKwh.multiply(exportPriceCt)
            .add(energy.importKwh.multiply(importPriceCt))
            .divide(HUNDRED, 6, RoundingMode.HALF_UP);
        };
        yield base.multiply(fee.feeValue).divide(HUNDRED, 4, RoundingMode.HALF_UP);
      }
      case ABSOLUTE_ENERGY -> {
        BigDecimal kwh = switch (fee.appliesTo) {
          case EXPORT -> energy.exportKwh;
          case IMPORT -> energy.importKwh;
          case BOTH -> energy.exportKwh.add(energy.importKwh);
        };
        yield kwh.multiply(fee.feeValue).divide(HUNDRED, 4, RoundingMode.HALF_UP); // ct/kWh → EUR
      }
      case ABSOLUTE_TIME ->
        fee.feeValue.divide(HOURS_PER_MONTH, 4, RoundingMode.HALF_UP); // EUR/month → EUR/hour
    };
  }

  private void updateMonthlyCost(LocalDateTime anyHourInMonth) {
    String yearMonth = anyHourInMonth.format(YEAR_MONTH_FMT);
    LocalDateTime from = anyHourInMonth.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    // Workaround: use plusMonths on date part
    LocalDate fromDate = from.toLocalDate();
    LocalDateTime to = fromDate.plusMonths(1).atStartOfDay();

    List<HourlyCostEntity> hours = hourlyCostRepository.findByDateRange(from, to);

    BigDecimal totalImportKwh = BigDecimal.ZERO;
    BigDecimal totalExportKwh = BigDecimal.ZERO;
    BigDecimal totalImportCost = BigDecimal.ZERO;
    BigDecimal totalExportIncome = BigDecimal.ZERO;
    BigDecimal totalFee = BigDecimal.ZERO;
    BigDecimal totalNet = BigDecimal.ZERO;

    for (HourlyCostEntity h : hours) {
      totalImportKwh = totalImportKwh.add(h.importKwh);
      totalExportKwh = totalExportKwh.add(h.exportKwh);
      totalImportCost = totalImportCost.add(h.importCostEur);
      totalExportIncome = totalExportIncome.add(h.exportIncomeEur);
      totalFee = totalFee.add(h.feeEur);
      totalNet = totalNet.add(h.netCostEur);
    }

    BigDecimal fixedCost = fixedCostRepository
      .findActiveForDate(anyHourInMonth.toLocalDate())
      .stream()
      .map(fc -> fc.monthlyCostEur)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    MonthlyCostEntity monthly = new MonthlyCostEntity();
    monthly.yearMonth = yearMonth;
    monthly.totalImportKwh = totalImportKwh;
    monthly.totalExportKwh = totalExportKwh;
    monthly.totalImportCostEur = totalImportCost;
    monthly.totalExportIncomeEur = totalExportIncome;
    monthly.totalFeeEur = totalFee;
    monthly.fixedCostEur = fixedCost;
    monthly.netCostEur = totalNet.add(fixedCost);
    monthly.hoursCalculated = hours.size();

    monthlyCostRepository.upsert(monthly);
  }

  /** Price resolution result: effective price in ct/kWh + source label. */
  record PriceResolution(BigDecimal priceCt, String source) {}
}
