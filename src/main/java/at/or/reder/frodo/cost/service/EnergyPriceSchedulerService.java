package at.or.reder.frodo.cost.service;

import at.or.reder.frodo.cost.entity.CostControlConfigEntity;
import at.or.reder.frodo.cost.repository.EnergyPriceRepository;
import at.or.reder.frodo.cost.spi.EnergyPriceProviderSpi;
import at.or.reder.frodo.cost.spi.PriceDirection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Scheduled service that fetches hourly energy prices from the configured providers.
 *
 * <p>Runs hourly at minute 55 (one minute before the hour boundary) for both the
 * import and export directions independently. The active provider is read from
 * {@link CostControlConfigService} on every run — provider changes take effect
 * without restarting the application.</p>
 *
 * <p>At startup, fetches if the current hour has no price for a direction.</p>
 */
@ApplicationScoped
public class EnergyPriceSchedulerService {

  private static final Logger LOG = Logger.getLogger(EnergyPriceSchedulerService.class);

  @Inject
  @Any
  Instance<EnergyPriceProviderSpi> providers;

  @Inject
  CostControlConfigService configService;

  @Inject
  EnergyPriceRepository energyPriceRepository;

  @Inject
  MeterRegistry meterRegistry;

  @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "true")
  boolean datasourceActive;

  @ConfigProperty(name = "frodo.cost-control.enabled", defaultValue = "true")
  boolean costControlEnabled;

  private volatile boolean shuttingDown = false;
  private Counter importFetchSuccess;
  private Counter importFetchFailure;
  private Counter exportFetchSuccess;
  private Counter exportFetchFailure;

  void onStart(@Observes StartupEvent event) {
    importFetchSuccess = meterRegistry.counter(
      "frodo.cost.price_fetch_total", "direction", "IMPORT", "provider", "any", "status", "success");
    importFetchFailure = meterRegistry.counter(
      "frodo.cost.price_fetch_total", "direction", "IMPORT", "provider", "any", "status", "failure");
    exportFetchSuccess = meterRegistry.counter(
      "frodo.cost.price_fetch_total", "direction", "EXPORT", "provider", "any", "status", "success");
    exportFetchFailure = meterRegistry.counter(
      "frodo.cost.price_fetch_total", "direction", "EXPORT", "provider", "any", "status", "failure");

    if (!costControlEnabled || !datasourceActive) {
      LOG.debug("Skipping startup price fetch: cost-control disabled or datasource inactive");
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    try {
      if (energyPriceRepository.findForTime(now).map(e -> e.priceImportCt).isEmpty()) {
        fetchForDirection(PriceDirection.IMPORT, now);
      }
      if (energyPriceRepository.findForTime(now).map(e -> e.priceExportCt).isEmpty()) {
        fetchForDirection(PriceDirection.EXPORT, now);
      }
    } catch (Exception ex) {
      LOG.warnf("Startup price fetch skipped: %s", ex.getMessage());
    }
  }

  void onStop(@Observes ShutdownEvent event) {
    shuttingDown = true;
  }

  /** Fetches prices for both directions every hour at minute 55. */
  @Scheduled(cron = "0 55 * * * ?", identity = "cost-price-fetch")
  void fetchAll() {
    if (shuttingDown || !costControlEnabled) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    fetchForDirection(PriceDirection.IMPORT, now);
    fetchForDirection(PriceDirection.EXPORT, now);
  }

  /**
   * Triggers an immediate price fetch for a specific direction.
   * Called by the REST resource for on-demand refresh.
   *
   * @param direction IMPORT or EXPORT
   */
  public void refreshNow(PriceDirection direction) {
    fetchForDirection(direction, LocalDateTime.now());
  }

  // ---- internals ---------------------------------------------------------

  private void fetchForDirection(PriceDirection direction, LocalDateTime now) {
    CostControlConfigEntity cfg;
    try {
      cfg = configService.load();
    } catch (Exception ex) {
      LOG.warnf("Cannot load cost-control config: %s", ex.getMessage());
      return;
    }

    String providerId = direction == PriceDirection.IMPORT
      ? cfg.importProviderId
      : cfg.exportProviderId;

    EnergyPriceProviderSpi provider;
    try {
      provider = resolveProvider(providerId);
    } catch (IllegalStateException ex) {
      LOG.errorf("No price provider registered for id '%s'", providerId);
      return;
    }

    if (!provider.isAutoFetchSupported()) {
      LOG.debugf("Provider '%s' does not support auto-fetch for %s, skipping", providerId, direction);
      return;
    }

    if (!provider.getSupportedDirections().contains(direction)) {
      LOG.warnf("Provider '%s' does not support direction %s, skipping", providerId, direction);
      return;
    }

    LocalDateTime from = now.withMinute(0).withSecond(0).withNano(0);
    LocalDateTime to = from.plusHours(48);

    LOG.debugf("Fetching %s prices from provider '%s' (%s – %s)", direction, providerId, from, to);
    try {
      List<EnergyPriceProviderSpi.HourlyPrice> prices =
        provider.fetchPrices(direction, from, to);
      persistPrices(direction, providerId, prices);
      if (direction == PriceDirection.IMPORT) {
        importFetchSuccess.increment();
      } else {
        exportFetchSuccess.increment();
      }
      LOG.debugf("Persisted %d %s prices from '%s'", prices.size(), direction, providerId);
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to fetch %s prices from provider '%s'", direction, providerId);
      if (direction == PriceDirection.IMPORT) {
        importFetchFailure.increment();
      } else {
        exportFetchFailure.increment();
      }
    }
  }

  private void persistPrices(
      PriceDirection direction, String providerId,
      List<EnergyPriceProviderSpi.HourlyPrice> prices) {
    for (EnergyPriceProviderSpi.HourlyPrice p : prices) {
      if (direction == PriceDirection.IMPORT) {
        energyPriceRepository.upsertImport(p.startTime(), p.endTime(), p.priceCt(), providerId);
      } else {
        energyPriceRepository.upsertExport(p.startTime(), p.endTime(), p.priceCt(), providerId);
      }
    }
  }

  private EnergyPriceProviderSpi resolveProvider(String id) {
    return StreamSupport.stream(providers.spliterator(), false)
      .filter(p -> p.getProviderId().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No price provider registered for id: " + id));
  }

  /**
   * Returns all registered SPI provider implementations for the REST registry endpoint.
   *
   * @return list of providers
   */
  public List<EnergyPriceProviderSpi> listProviders() {
    return StreamSupport.stream(providers.spliterator(), false).toList();
  }
}
