package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Registers and manages Micrometer/Prometheus metrics for market price operations.
 *
 * <p>Provides a gauge that exposes the current market price to the
 * {@code /q/metrics} Prometheus endpoint.</p>
 *
 * <p><b>Registered Metrics:</b></p>
 *
 * <p><em>Gauges:</em></p>
 * <ul>
 *   <li>{@code frodo.market_price.current} — current market price in ct/kWh</li>
 * </ul>
 */
@ApplicationScoped
@Startup
public class MarketPriceMetrics {

  private static final Logger LOG = Logger.getLogger(MarketPriceMetrics.class);

  private final MeterRegistry registry;
  private final MarketPriceRepository marketPriceRepository;

  @Inject
  public MarketPriceMetrics(MeterRegistry registry,
                            MarketPriceRepository marketPriceRepository) {
    this.registry = registry;
    this.marketPriceRepository = marketPriceRepository;

    // --- Register Gauges ---
    Gauge.builder("frodo.market_price.current", this, MarketPriceMetrics::currentPrice)
      .description("Current market price in ct/kWh")
      .register(registry);

    LOG.info("Market price metrics registered");
  }

  // --- Gauge value suppliers ---

  private double currentPrice() {
    try {
      return marketPriceRepository.findCurrent()
        .map(price -> price.priceCt)
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch current market price: %s", ex.getMessage());
      return Double.NaN;
    }
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
