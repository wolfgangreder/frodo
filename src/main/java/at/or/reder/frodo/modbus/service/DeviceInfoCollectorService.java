package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.ModbusException;
import at.or.reder.frodo.modbus.ModbusTcpService;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceInfoEntity;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.ReadDeviceIdCode;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled service for collecting device identification information.
 *
 * <p>Periodically fetches device identification data from all enabled
 * Modbus devices, stores the results in the database, and updates the
 * in-memory cache. Implements retry logic with exponential backoff for
 * failed reads.</p>
 *
 * <p>The collection interval is configurable via
 * {@code frodo.modbus.device-info.refresh-interval}.</p>
 */
@ApplicationScoped
public class DeviceInfoCollectorService {

  private static final Logger LOG = Logger.getLogger(DeviceInfoCollectorService.class);

  @Inject
  ModbusTcpService modbusTcpService;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @Inject
  DeviceInfoCacheService cacheService;

  @ConfigProperty(name = "frodo.modbus.device-info.retry-attempts", defaultValue = "3")
  int retryAttempts;

  @ConfigProperty(name = "frodo.modbus.device-info.retry-delay-seconds", defaultValue = "5")
  int retryDelaySeconds;

  @ConfigProperty(name = "quarkus.hibernate-orm.enabled", defaultValue = "true")
  boolean hibernateEnabled;

  /**
   * Scheduled job to collect device identification for all enabled devices.
   *
   * <p>Runs according to the configured refresh interval (default: 5 minutes).
   * For each enabled device, attempts to read device identification and
   * stores the result in the database and cache.</p>
   *
   * <p>Skipped in dev/test modes when Hibernate is disabled.</p>
   */
  @Scheduled(
    every = "${frodo.modbus.device-info.refresh-interval:5m}",
    identity = "device-info-collector"
  )
  void collectAllDeviceInfo() {
    // Skip if Hibernate is disabled (dev/test modes)
    if (!hibernateEnabled) {
      LOG.debug("Skipping device info collection: Hibernate ORM is disabled");
      return;
    }

    Instant start = Instant.now();
    LOG.info("Starting device info collection for all enabled devices");

    List<ModbusDeviceEntity> devices = deviceRepository.listAllEnabled();
    if (devices.isEmpty()) {
      LOG.info("No enabled devices found, skipping collection");
      return;
    }

    LOG.infof("Found %d enabled device(s) to collect info from", devices.size());

    int successCount = 0;
    int failureCount = 0;

    for (ModbusDeviceEntity device : devices) {
      try {
        collectForDevice(device)
          .await()
          .atMost(Duration.ofSeconds(30));
        successCount++;
      } catch (Exception e) {
        LOG.errorf(e, "Failed to collect info for device %d (%s): %s",
          device.id, device.name, e.getMessage());
        failureCount++;
      }
    }

    Duration duration = Duration.between(start, Instant.now());
    LOG.infof("Device info collection completed in %d ms: %d success, %d failures",
      duration.toMillis(), successCount, failureCount);
  }

  /**
   * Collects device identification for a single device.
   *
   * <p>Attempts to read device identification with retry logic. On success,
   * updates the database and cache. On failure, records the error in the
   * database.</p>
   *
   * @param device the device entity
   * @return Uni that completes when collection is done
   */
  public Uni<Void> collectForDevice(ModbusDeviceEntity device) {
    LOG.debugf("Collecting device info for device %d: %s",
      device.id, device.getConnectionString());

    return readDeviceIdentificationWithRetry(device.unitId)
      .onItem().transformToUni(identification -> {
        // Success: store in database and cache
        LOG.infof("Successfully read device info for device %d (%s): %s %s",
          device.id, device.name, identification.vendorName(), identification.productCode());

        ModbusDeviceInfoEntity infoEntity = deviceRepository.findOrCreateDeviceInfo(device.id);
        infoEntity.updateFrom(identification, true, null);
        infoEntity.persist();

        // Update cache
        cacheService.put(device.id, identification);

        return Uni.createFrom().voidItem();
      })
      .onFailure().recoverWithUni(failure -> {
        // Failure: record error in database
        LOG.errorf(failure, "Failed to read device info for device %d (%s) after %d retries: %s",
          device.id, device.name, retryAttempts, failure.getMessage());

        ModbusDeviceInfoEntity infoEntity = deviceRepository.findOrCreateDeviceInfo(device.id);
        infoEntity.updateFrom(null, false, truncateErrorMessage(failure.getMessage()));
        infoEntity.persist();

        // Invalidate cache on error
        cacheService.invalidate(device.id);

        return Uni.createFrom().voidItem();
      });
  }

  /**
   * Refreshes device identification for a specific device by ID.
   *
   * <p>Forces an immediate collection for the device, bypassing the
   * scheduled job. Useful for manual refresh operations.</p>
   *
   * @param deviceId the device ID
   * @return Uni with the device identification
   * @throws IllegalArgumentException if device not found or disabled
   */
  public Uni<DeviceIdentification> refreshDevice(Long deviceId) {
    ModbusDeviceEntity device = deviceRepository.findByIdOptional(deviceId)
      .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

    if (!device.enabled) {
      return Uni.createFrom().failure(
        new IllegalArgumentException("Device is disabled: " + deviceId)
      );
    }

    LOG.infof("Manual refresh requested for device %d (%s)", device.id, device.name);

    return readDeviceIdentificationWithRetry(device.unitId)
      .onItem().invoke(identification -> {
        // Update database and cache
        ModbusDeviceInfoEntity infoEntity = deviceRepository.findOrCreateDeviceInfo(device.id);
        infoEntity.updateFrom(identification, true, null);
        infoEntity.persist();
        cacheService.put(device.id, identification);

        LOG.infof("Manual refresh completed for device %d (%s)", device.id, device.name);
      });
  }

  /**
   * Reads device identification with retry logic and exponential backoff.
   *
   * @param unitId the Modbus unit ID
   * @return Uni with the device identification
   */
  private Uni<DeviceIdentification> readDeviceIdentificationWithRetry(int unitId) {
    return modbusTcpService.readDeviceIdentification(unitId, ReadDeviceIdCode.BASIC)
      .onFailure().retry()
      .withBackOff(Duration.ofSeconds(retryDelaySeconds), Duration.ofSeconds(retryDelaySeconds * 4))
      .atMost(retryAttempts - 1); // -1 because first attempt is not a retry
  }

  /**
   * Truncates error message to fit database column size (500 chars).
   *
   * @param message the error message
   * @return truncated message
   */
  String truncateErrorMessage(String message) {
    if (message == null) {
      return "Unknown error";
    }
    if (message.length() <= 500) {
      return message;
    }
    return message.substring(0, 497) + "...";
  }
}
