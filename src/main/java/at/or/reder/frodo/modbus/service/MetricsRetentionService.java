package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import at.or.reder.frodo.modbus.repository.MetricsConfigRepository;
import at.or.reder.frodo.modbus.repository.MetricsDataRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service that cleans up old metrics data based on each device's
 * retention policy.
 *
 * <p>Runs daily at 02:00 and iterates over all metrics configs that have
 * database storage enabled. For each config, data older than the configured
 * {@code retentionDays} is deleted.</p>
 *
 * <p>Also cleans up market price data older than 365 days.</p>
 */
@ApplicationScoped
public class MetricsRetentionService {

  private static final Logger LOG = Logger.getLogger(MetricsRetentionService.class);

  private static final int MARKET_PRICE_RETENTION_DAYS = 365;

  @Inject
  MetricsConfigRepository configRepository;

  @Inject
  MetricsDataRepository dataRepository;

  @Inject
  MarketPriceRepository marketPriceRepository;

  /**
   * Daily retention cleanup job. Runs at 02:00 every day.
   *
   * <p>For each device with database storage enabled, deletes metrics data
   * that is older than the configured retention period.</p>
   *
   * <p>Also cleans up market price data older than 365 days.</p>
   */
  @Scheduled(cron = "0 0 2 * * ?", identity = "metrics-retention-cleanup")
  @Transactional
  void cleanupOldData() {
    LOG.info("Starting metrics data retention cleanup");

    List<MetricsConfigEntity> configs = configRepository.findAllEnabled();
    int totalDeleted = 0;

    for (MetricsConfigEntity config : configs) {
      if (!config.storeToDatabase) {
        continue;
      }

      Long deviceId = config.device.id;
      int retentionDays = config.retentionDays;
      Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

      try {
        int deleted = dataRepository.deleteOlderThan(deviceId, cutoff);
        if (deleted > 0) {
          LOG.infof("Deleted %d old metrics records for device %d (retention: %d days)",
            deleted, deviceId, retentionDays);
          totalDeleted += deleted;
        }
      } catch (Exception e) {
        LOG.errorf(e, "Failed to clean up metrics data for device %d", deviceId);
      }
    }

    LOG.infof("Metrics retention cleanup complete: %d records deleted", totalDeleted);

    // Clean up market prices
    cleanupMarketPrices();
  }

  /**
   * Cleans up market price data older than the retention period.
   */
  @Transactional
  void cleanupMarketPrices() {
    try {
      LocalDateTime cutoff = LocalDateTime.now().minusDays(MARKET_PRICE_RETENTION_DAYS);
      int deleted = marketPriceRepository.deleteExpired(cutoff);
      if (deleted > 0) {
        LOG.infof("Deleted %d expired market price entries (retention: %d days)",
          deleted, MARKET_PRICE_RETENTION_DAYS);
      }
    } catch (Exception e) {
      LOG.errorf(e, "Failed to clean up market price data");
    }
  }
}
