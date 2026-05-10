package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import at.or.reder.frodo.modbus.service.model.MarketDataResponse;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Scheduled service that fetches hourly market prices from aWATTar AT.
 *
 * <p>Prices are fetched:</p>
 * <ol>
 *   <li><b>At startup</b> — if no price for the current hour is in the database yet.</li>
 *   <li><b>Every hour at minute 55</b> — to pre-load prices for the next hour before
 *       it begins.</li>
 * </ol>
 *
 * <p>aWATTar AT allows at most 100 API calls per day. With one call per hour plus an
 * occasional startup call, daily usage stays well under that limit.</p>
 *
 * <p>When prices are negative the export scheduler can automatically cap grid export
 * to avoid feeding back cheap electricity.</p>
 *
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>{@code frodo.awattar.enabled} — enable/disable price fetching</li>
 * </ul>
 *
 * <p>Market price retention is handled by {@link MetricsRetentionService} using
 * the default 365-day retention period.</p>
 */
@ApplicationScoped
public class MarketPriceSchedulerService {

  private static final Logger LOG = Logger.getLogger(MarketPriceSchedulerService.class);

  private volatile boolean shuttingDown = false;
  private volatile boolean lastFetchSuccess = false;

  @Inject
  AwattarClient awattarClient;

  @Inject
  MarketPriceRepository marketPriceRepository;

  @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "true")
  boolean datasourceActive;

  @ConfigProperty(name = "frodo.awattar.enabled", defaultValue = "false")
  boolean awattarEnabled;

  /**
   * Fetches prices at startup if the current hour has no price in the database.
   *
   * <p>Skipped when aWATTar is disabled or the DB already has a valid entry for
   * the current hour — avoids wasting one of the 100 daily API calls on a hot
   * restart.</p>
   */
  void onStart(@Observes StartupEvent event) {
    if (!awattarEnabled) {
      LOG.debug("Skipping startup market price fetch: aWATTar disabled");
      return;
    }
    if (!datasourceActive) {
      LOG.debug("Skipping startup market price fetch: datasource inactive");
      return;
    }
    try {
      if (marketPriceRepository.findCurrent().isPresent()) {
        LOG.debug("Startup market price fetch skipped: current-hour price already in database");
        return;
      }
    } catch (Exception ex) {
      // Database may not be available (e.g. test mode, datasource inactive).
      // Skip the startup fetch — persistPrices() would fail for the same reason.
      LOG.warnf("Startup market price fetch skipped: could not query database (%s)", ex.getMessage());
      return;
    }
    LOG.info("No current-hour price found at startup — fetching from aWATTar AT");
    fetchMarketPrices();
  }

  void onStop(@Observes ShutdownEvent event) {
    shuttingDown = true;
    LOG.info("Shutdown received, stopping market price scheduler");
  }

  /**
   * Fetches market prices every hour at minute 55.
   *
   * <p>Runs one minute before the top of the hour so the next hour's price is
   * available as soon as it begins. Also cleans up expired entries.</p>
   *
   * <p>The HTTP call is made on the scheduler worker thread (blocking).
   * The DB writes happen in a separate short transaction via
   * {@link #persistPrices(List)} so no DB connection is held open during the
   * network call.</p>
   */
  @Scheduled(cron = "0 55 * * * ?", identity = "market-price-fetch")
  void fetchMarketPrices() {
    if (shuttingDown) {
      LOG.debug("Skipping market price fetch: shutting down");
      return;
    }
    if (!awattarEnabled) {
      LOG.debug("Skipping market price fetch: aWATTar disabled");
      return;
    }

    LOG.info("Fetching market prices from aWATTar AT");

    // Blocking HTTP call on the scheduler worker thread.
    // Do NOT hold a DB transaction open across this network call.
    final MarketDataResponse response;
    try {
      response = awattarClient.getMarketData();
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to fetch market data from aWATTar");
      lastFetchSuccess = false;
      return;
    }

    List<MarketDataResponse.MarketPrice> prices = response != null ? response.data() : null;
    if (prices == null || prices.isEmpty()) {
      LOG.warn("No market prices received from aWATTar");
      lastFetchSuccess = false;
      return;
    }

    // Persist in a short, focused transaction — no network I/O inside.
    int saved = persistPrices(prices);
    LOG.infof("Saved %d market prices from aWATTar AT", saved);
    lastFetchSuccess = true;
  }

  /**
   * Persists the given price list to the database.
   * Runs in its own short transaction; called only after the HTTP fetch succeeds.
   *
   * <p>Cleanup of expired entries is handled by {@link MetricsRetentionService}.</p>
   *
   * @param prices raw price entries from aWATTar (EUR/MWh, converted to ct/kWh on store)
   * @return number of rows saved
   */
  @Transactional
  int persistPrices(List<MarketDataResponse.MarketPrice> prices) {
    int saved = 0;
    for (MarketDataResponse.MarketPrice price : prices) {
      LocalDateTime startTime = Instant.ofEpochMilli(price.getStartTimestamp())
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime();
      LocalDateTime endTime = Instant.ofEpochMilli(price.getEndTimestamp())
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime();

      marketPriceRepository.upsert(startTime, endTime, eurMwhToCtKwh(price.getMarketPrice()));
      saved++;
    }
    return saved;
  }

  /**
   * Triggers an immediate market price fetch outside of the regular schedule.
   *
   * <p>Delegates to {@link #fetchMarketPrices()} — same logic, same flow.
   * Intended for use by the REST resource to allow on-demand refresh.</p>
   *
   * @throws IllegalStateException if aWATTar is disabled
   */
  public void refreshNow() {
    if (!awattarEnabled || !awattarClient.isEnabled()) {
      throw new IllegalStateException("aWATTar integration is disabled");
    }
    fetchMarketPrices();
  }

  /**
   * Checks if the last fetch was successful.
   *
   * @return true if the last fetch succeeded
   */
  public boolean isLastFetchSuccess() {
    return lastFetchSuccess;
  }

  /**
   * Checks if aWATTar integration is enabled.
   *
   * @return true if enabled
   */
  public boolean isEnabled() {
    return awattarEnabled && awattarClient.isEnabled();
  }

  /**
   * Converts a market price from EUR/MWh (as provided by aWATTar) to ct/kWh
   * (the unit stored in the database and displayed in the UI).
   *
   * <pre>
   *   1 EUR/MWh = 0.1 ct/kWh  →  ct/kWh = EUR/MWh / 10
   * </pre>
   *
   * <p>The sign is preserved: negative EUR/MWh values remain negative in ct/kWh.</p>
   *
   * @param eurMwh market price in EUR/MWh
   * @return equivalent price in ct/kWh
   */
  static java.math.BigDecimal eurMwhToCtKwh(double eurMwh) {
    return java.math.BigDecimal.valueOf(eurMwh)
      .divide(java.math.BigDecimal.TEN, 5, java.math.RoundingMode.HALF_UP);
  }
}
