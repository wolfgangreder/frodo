package at.or.reder.frodo.health;

import at.or.reder.frodo.cost.entity.GridFeeEntity;
import at.or.reder.frodo.cost.repository.GridFeeRepository;
import at.or.reder.frodo.cost.spi.FeeAppliesTo;
import at.or.reder.frodo.cost.spi.FeeType;
import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registers and manages Micrometer/Prometheus metrics for market price operations.
 *
 * <p>Provides gauges that expose the current market price to the
 * {@code /q/metrics} Prometheus endpoint.</p>
 *
 * <p><b>Registered Metrics:</b></p>
 *
 * <p><em>Gauges (all in ct/kWh):</em></p>
 * <ul>
 *   <li>{@code frodo.market_price.current{type="net",  direction="import"}} — raw spot price</li>
 *   <li>{@code frodo.market_price.current{type="gross", direction="import"}} — spot + active IMPORT/BOTH fees</li>
 *   <li>{@code frodo.market_price.current{type="net",  direction="export"}} — raw spot price</li>
 *   <li>{@code frodo.market_price.current{type="gross", direction="export"}} — spot + active EXPORT/BOTH fees</li>
 * </ul>
 *
 * <p>ABSOLUTE_ENERGY (ct/kWh) and PERCENT fees are included in gross.
 * ABSOLUTE_TIME (EUR/month standing charges) are excluded — not meaningful as ct/kWh.</p>
 */
@ApplicationScoped
@Startup
public class MarketPriceMetrics {

  private static final Logger LOG = Logger.getLogger(MarketPriceMetrics.class);

  private final MeterRegistry registry;
  private final MarketPriceRepository marketPriceRepository;
  private final GridFeeRepository gridFeeRepository;

  @Inject
  public MarketPriceMetrics(MeterRegistry registry,
                            MarketPriceRepository marketPriceRepository,
                            GridFeeRepository gridFeeRepository) {
    this.registry = registry;
    this.marketPriceRepository = marketPriceRepository;
    this.gridFeeRepository = gridFeeRepository;

    Gauge.builder("frodo.market_price.current", this, m -> m.currentNetPrice())
      .description("Current market price in ct/kWh (raw spot, no grid fees)")
      .tag("type", "net")
      .tag("direction", "import")
      .register(registry);

    Gauge.builder("frodo.market_price.current", this, m -> m.currentGrossPrice(FeeAppliesTo.IMPORT))
      .description("Current market price in ct/kWh including active IMPORT grid fees")
      .tag("type", "gross")
      .tag("direction", "import")
      .register(registry);

    Gauge.builder("frodo.market_price.current", this, m -> m.currentNetPrice())
      .description("Current market price in ct/kWh (raw spot, no grid fees)")
      .tag("type", "net")
      .tag("direction", "export")
      .register(registry);

    Gauge.builder("frodo.market_price.current", this, m -> m.currentGrossPrice(FeeAppliesTo.EXPORT))
      .description("Current market price in ct/kWh including active EXPORT grid fees")
      .tag("type", "gross")
      .tag("direction", "export")
      .register(registry);

    LOG.info("Market price metrics registered");
  }

  // --- Gauge value suppliers ---

  private double currentNetPrice() {
    try {
      return marketPriceRepository.findCurrent()
        .map(price -> price.priceCt.doubleValue())
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch current market price: %s", ex.getMessage());
      return Double.NaN;
    }
  }

  private double currentGrossPrice(FeeAppliesTo direction) {
    try {
      return marketPriceRepository.findCurrent().map(price -> {
        BigDecimal gross = applyGridFees(price.priceCt, direction);
        return gross.doubleValue();
      }).orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not compute gross market price (%s): %s", direction, ex.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Applies all active grid fees matching {@code direction} (or BOTH) of type
   * ABSOLUTE_ENERGY and PERCENT to a net price.
   * ABSOLUTE_TIME fees (EUR/month) are skipped — not meaningful as ct/kWh.
   *
   * @param net       raw spot price in ct/kWh
   * @param direction IMPORT or EXPORT — selects which fees apply
   * @return gross price in ct/kWh
   */
  private BigDecimal applyGridFees(BigDecimal net, FeeAppliesTo direction) {
    List<GridFeeEntity> fees = gridFeeRepository.findActiveFeesForTime(LocalDateTime.now());
    BigDecimal gross = net;
    for (GridFeeEntity fee : fees) {
      if (fee.appliesTo != FeeAppliesTo.BOTH && fee.appliesTo != direction) {
        continue;
      }
      if (fee.feeType == FeeType.ABSOLUTE_ENERGY) {
        gross = gross.add(fee.feeValue);
      } else if (fee.feeType == FeeType.PERCENT) {
        BigDecimal surcharge = net.multiply(fee.feeValue)
          .divide(BigDecimal.valueOf(100), 5, RoundingMode.HALF_UP);
        gross = gross.add(surcharge);
      }
      // ABSOLUTE_TIME: skip — EUR/month standing charge, not per-kWh
    }
    return gross;
  }

  /**
   * Returns the meter registry for testing purposes.
   *
   * @return the Micrometer meter registry
   */
  public MeterRegistry getRegistry() {
    return registry;
  }
}
