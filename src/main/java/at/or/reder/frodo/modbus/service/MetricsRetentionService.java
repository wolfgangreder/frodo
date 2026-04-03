package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import at.or.reder.frodo.modbus.repository.MetricsConfigRepository;
import at.or.reder.frodo.modbus.repository.MetricsDataRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service that cleans up old metrics data based on each device's
 * retention policy.
 *
 * <p>Runs daily at 02:00 and iterates over all metrics configs that have
 * database storage enabled. For each config, data older than the configured
 * {@code retentionDays} is deleted.</p>
 */
@ApplicationScoped
public class MetricsRetentionService {

  private static final Logger LOG = Logger.getLogger(MetricsRetentionService.class);

  @Inject
  MetricsConfigRepository configRepository;

  @Inject
  MetricsDataRepository dataRepository;

  /**
   * Daily retention cleanup job. Runs at 02:00 every day.
   *
   * <p>For each device with database storage enabled, deletes metrics data
   * that is older than the configured retention period.</p>
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
  }
}
