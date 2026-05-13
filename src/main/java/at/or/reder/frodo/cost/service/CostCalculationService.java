/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.cost.service;

import at.or.reder.frodo.cost.entity.DailyCostEntity;
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
import at.or.reder.frodo.cost.repository.DailyCostRepository;
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
 * <h3>Hourly formula</h3>
 * <pre>
 *   effectiveImportPrice = marketImportPrice − ABSOLUTE_ENERGY_fees − PERCENT_fees (ct/kWh)
 *   effectiveExportPrice = marketExportPrice − ABSOLUTE_ENERGY_fees − PERCENT_fees (ct/kWh)
 *   importCostEur   = max(0, effectiveImportPrice) × importKwh  / 100
 *   exportIncomeEur = max(0, effectiveExportPrice) × exportKwh / 100
 *   feeEur          = sum(ABSOLUTE_TIME fees) / 730  [EUR/month amortized per hour]
 *   netCostEur      = importCostEur + feeEur          [import cost only, no saldo]
 * </pre>
 * Import and export are calculated independently — no saldo is built at the hourly level.
 *
 * <h3>Price resolution order (per direction)</h3>
 * <ol>
 *   <li>Matching {@link TariffWindowEntity} (highest priority wins) → source = "TARIFF_WINDOW"</li>
 *   <li>Provider price from {@code FroEnergyPrice} for the hour → source = provider id</li>
 *   <li>No price found → warn, use 0.0, source = "UNKNOWN"</li>
 * </ol>
 *
 * <h3>Daily update</h3>
 * <p>After every hourly upsert, all {@code FroHourlyCost} rows for the calendar day are
 * re-aggregated. Fixed costs are not included at the daily level (monthly concept only).</p>
 *
 * <h3>Monthly update</h3>
 * <p>After every hourly upsert, all {@code FroHourlyCost} rows for the calendar month are
 * re-aggregated. Fixed costs are added on top of the summed import costs + time fees.</p>
 */
@ApplicationScoped
public class CostCalculationService {

  private static final Logger LOG = Logger.getLogger(CostCalculationService.class);
  private static final String SOURCE_TARIFF_WINDOW = "TARIFF_WINDOW";
  private static final String SOURCE_UNKNOWN = "UNKNOWN";
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final BigDecimal HOURS_PER_MONTH = new BigDecimal("730");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  @Inject
  HourlyEnergyRepository hourlyEnergyRepository;

  @Inject
  HourlyCostRepository hourlyCostRepository;

  @Inject
  DailyCostRepository dailyCostRepository;

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

    // Resolve raw market prices per direction
    PriceResolution importResolution = resolvePrice(PriceDirection.IMPORT, hourStart);
    PriceResolution exportResolution = resolvePrice(PriceDirection.EXPORT, hourStart);

    // Apply grid fees: ABSOLUTE_ENERGY/PERCENT reduce the effective price per direction;
    // ABSOLUTE_TIME is amortized as a separate hourly EUR charge.
    List<GridFeeEntity> fees = gridFeeRepository.findActiveFeesForTime(hourStart);
    EffectivePrices effective = applyFees(importResolution.priceCt(), exportResolution.priceCt(), fees);

    // cost = max(0, effective_price_ct) * kWh / 100  (ct/kWh → EUR)
    // Import and export are independent — no saldo.
    BigDecimal importCostEur = effective.importPriceCt().max(BigDecimal.ZERO)
      .multiply(energy.importKwh)
      .divide(HUNDRED, 4, RoundingMode.HALF_UP);
    BigDecimal exportIncomeEur = effective.exportPriceCt().max(BigDecimal.ZERO)
      .multiply(energy.exportKwh)
      .divide(HUNDRED, 4, RoundingMode.HALF_UP);

    // feeEur = ABSOLUTE_TIME per-hour standing charge only (energy fees are in the price)
    BigDecimal feeEur = effective.timeFeeEur();

    // netCostEur = import cost + standing fees; export income is separate
    BigDecimal netCostEur = importCostEur.add(feeEur);

    // Upsert FroHourlyCost — store effective (after-fee) prices for display consistency
    HourlyCostEntity cost = new HourlyCostEntity();
    cost.hourStart = hourStart;
    cost.hourEnd = energy.hourEnd;
    cost.importKwh = energy.importKwh;
    cost.exportKwh = energy.exportKwh;
    cost.priceImportCt = effective.importPriceCt();
    cost.priceExportCt = effective.exportPriceCt();
    cost.importPriceSource = importResolution.source();
    cost.exportPriceSource = exportResolution.source();
    cost.importCostEur = importCostEur;
    cost.exportIncomeEur = exportIncomeEur;
    cost.feeEur = feeEur;
    cost.netCostEur = netCostEur;

    hourlyCostRepository.upsert(cost);

    // Real-time daily and monthly updates
    updateDailyCost(hourStart);
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

  /**
   * Applies all active grid fees to the raw market prices, returning effective prices.
   *
   * <ul>
   *   <li>{@link FeeType#ABSOLUTE_ENERGY} (ct/kWh): subtracted directly from the market price
   *       per applicable direction.</li>
   *   <li>{@link FeeType#PERCENT} (%): {@code rawPrice × percent / 100} subtracted from the
   *       effective price per applicable direction.</li>
   *   <li>{@link FeeType#ABSOLUTE_TIME} (EUR/month): amortized per hour (÷ 730) and returned
   *       as a separate standing charge; does not affect per-kWh prices.</li>
   * </ul>
   *
   * <p>Package-private and static for unit testability.</p>
   *
   * @param rawImportCt  raw market import price in ct/kWh
   * @param rawExportCt  raw market export price in ct/kWh
   * @param fees         active grid fees for the hour
   * @return effective prices and per-hour time fee
   */
  static EffectivePrices applyFees(BigDecimal rawImportCt, BigDecimal rawExportCt, List<GridFeeEntity> fees) {
    BigDecimal importCt = rawImportCt;
    BigDecimal exportCt = rawExportCt;
    BigDecimal timeFeeEur = BigDecimal.ZERO;

    for (GridFeeEntity fee : fees) {
      switch (fee.feeType) {
        case ABSOLUTE_ENERGY -> {
          // ct/kWh subtracted from market price per direction
          if (fee.appliesTo == FeeAppliesTo.IMPORT || fee.appliesTo == FeeAppliesTo.BOTH) {
            importCt = importCt.subtract(fee.feeValue);
          }
          if (fee.appliesTo == FeeAppliesTo.EXPORT || fee.appliesTo == FeeAppliesTo.BOTH) {
            exportCt = exportCt.subtract(fee.feeValue);
          }
        }
        case PERCENT -> {
          // Percentage of the raw market price subtracted per direction
          if (fee.appliesTo == FeeAppliesTo.IMPORT || fee.appliesTo == FeeAppliesTo.BOTH) {
            importCt = importCt.subtract(
              rawImportCt.multiply(fee.feeValue).divide(HUNDRED, 6, RoundingMode.HALF_UP));
          }
          if (fee.appliesTo == FeeAppliesTo.EXPORT || fee.appliesTo == FeeAppliesTo.BOTH) {
            exportCt = exportCt.subtract(
              rawExportCt.multiply(fee.feeValue).divide(HUNDRED, 6, RoundingMode.HALF_UP));
          }
        }
        case ABSOLUTE_TIME ->
          // EUR/month amortized per hour; separate standing charge
          timeFeeEur = timeFeeEur.add(fee.feeValue.divide(HOURS_PER_MONTH, 4, RoundingMode.HALF_UP));
      }
    }
    return new EffectivePrices(importCt, exportCt, timeFeeEur);
  }

  private void updateDailyCost(LocalDateTime anyHourInDay) {
    String day = anyHourInDay.format(DAY_FMT);
    LocalDateTime from = anyHourInDay.toLocalDate().atStartOfDay();
    LocalDateTime to = from.plusDays(1);

    List<HourlyCostEntity> hours = hourlyCostRepository.findByDateRange(from, to);

    BigDecimal totalImportKwh = BigDecimal.ZERO;
    BigDecimal totalExportKwh = BigDecimal.ZERO;
    BigDecimal totalImportCost = BigDecimal.ZERO;
    BigDecimal totalExportIncome = BigDecimal.ZERO;
    BigDecimal totalFee = BigDecimal.ZERO;

    for (HourlyCostEntity h : hours) {
      totalImportKwh = totalImportKwh.add(h.importKwh);
      totalExportKwh = totalExportKwh.add(h.exportKwh);
      totalImportCost = totalImportCost.add(h.importCostEur);
      totalExportIncome = totalExportIncome.add(h.exportIncomeEur);
      totalFee = totalFee.add(h.feeEur);
    }

    DailyCostEntity daily = new DailyCostEntity();
    daily.costDay = day;
    daily.totalImportKwh = totalImportKwh;
    daily.totalExportKwh = totalExportKwh;
    daily.totalImportCostEur = totalImportCost;
    daily.totalExportIncomeEur = totalExportIncome;
    daily.totalFeeEur = totalFee;
    // netCostEur = import cost + time-based fees; fixed costs are monthly only
    daily.netCostEur = totalImportCost.add(totalFee);
    daily.hoursCalculated = hours.size();

    dailyCostRepository.upsert(daily);
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

    for (HourlyCostEntity h : hours) {
      totalImportKwh = totalImportKwh.add(h.importKwh);
      totalExportKwh = totalExportKwh.add(h.exportKwh);
      totalImportCost = totalImportCost.add(h.importCostEur);
      totalExportIncome = totalExportIncome.add(h.exportIncomeEur);
      totalFee = totalFee.add(h.feeEur);
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
    // netCostEur = total import cost + time-based standing fees + fixed costs;
    // export income is tracked separately and NOT subtracted (no saldo).
    monthly.netCostEur = totalImportCost.add(totalFee).add(fixedCost);
    monthly.hoursCalculated = hours.size();

    monthlyCostRepository.upsert(monthly);
  }

  /** Price resolution result: raw market price in ct/kWh + source label. */
  record PriceResolution(BigDecimal priceCt, String source) {}

  /**
   * Effective prices after grid fees have been applied.
   *
   * @param importPriceCt effective import price in ct/kWh (market − fees); may be negative
   * @param exportPriceCt effective export price in ct/kWh (market − fees); may be negative
   * @param timeFeeEur    per-hour standing charge from ABSOLUTE_TIME fees in EUR
   */
  record EffectivePrices(BigDecimal importPriceCt, BigDecimal exportPriceCt, BigDecimal timeFeeEur) {}
}
