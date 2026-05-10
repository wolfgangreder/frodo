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

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceInfoEntity;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scheduled service for collecting device identification information.
 *
 * <p>Periodically fetches device identification data from all enabled
 * Modbus devices via the SunSpec Common model (1), stores the results
 * in the database, and updates the in-memory cache. Implements retry
 * logic with exponential backoff for failed reads.</p>
 *
 * <p>The Fronius GEN24 and Smart Meter expose device info (manufacturer,
 * model, serial number, firmware version) through the SunSpec Common model
 * at register offsets discovered dynamically via FC 0x03. FC 0x2B
 * (Read Device Identification) is not supported by these devices.</p>
 *
 * <p>The collection interval is configurable via
 * {@code frodo.modbus.device-info.refresh-interval}.</p>
 */
@ApplicationScoped
public class DeviceInfoCollectorService {

  private static final Logger LOG = Logger.getLogger(DeviceInfoCollectorService.class);

  private volatile boolean shuttingDown = false;

  @Inject
  SunSpecService sunSpecService;

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

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "true")
  boolean modbusEnabled;

  /**
   * Observes Quarkus shutdown events to prevent the scheduled collector
   * from racing with container teardown (especially during dev-mode hot reloads).
   */
  void onStop(@Observes ShutdownEvent event) {
    shuttingDown = true;
    LOG.info("Shutdown event received, stopping device info collection");
  }

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
    delayed = "30s",
    identity = "device-info-collector"
  )
  @Transactional
  void collectAllDeviceInfo() {
    // Skip if shutting down (prevents race with container teardown)
    if (shuttingDown) {
      LOG.debug("Skipping device info collection: application is shutting down");
      return;
    }

    // Skip if Hibernate is disabled (dev/test modes)
    if (!hibernateEnabled) {
      LOG.debug("Skipping device info collection: Hibernate ORM is disabled");
      return;
    }

    // Skip if Modbus is disabled
    if (!modbusEnabled) {
      LOG.debug("Skipping device info collection: Modbus is disabled");
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
      if (shuttingDown) {
        LOG.info("Aborting device info collection: application is shutting down");
        break;
      }
      try {
        collectForDevice(device);
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
   */
  @Transactional
  public void collectForDevice(ModbusDeviceEntity device) {
    LOG.debugf("Collecting device info for device %d: %s",
      device.id, device.getConnectionString());

    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      DeviceIdentification identification = readCommonModelWithRetry(address);

      // Success: store in database and cache
      LOG.infof("Successfully read device info for device %d (%s): %s %s",
        device.id, device.name, identification.vendorName(), identification.productCode());

      ModbusDeviceInfoEntity infoEntity = deviceRepository.findOrCreateDeviceInfo(device.id);
      infoEntity.updateFrom(identification, true, null);
      infoEntity.persist();

      // Update cache
      cacheService.put(device.id, identification);
    } catch (Exception failure) {
      // Failure: record error in database
      LOG.errorf(failure, "Failed to read device info for device %d (%s) after %d retries: %s",
        device.id, device.name, retryAttempts, failure.getMessage());

      ModbusDeviceInfoEntity infoEntity = deviceRepository.findOrCreateDeviceInfo(device.id);
      infoEntity.updateFrom(null, false, truncateErrorMessage(failure.getMessage()));
      infoEntity.persist();

      // Invalidate cache on error
      cacheService.invalidate(device.id);
    }
  }

  /**
   * Refreshes device identification for a specific device by ID.
   *
   * <p>Forces an immediate collection for the device, bypassing the
   * scheduled job. Useful for manual refresh operations.</p>
   *
   * @param deviceId the device ID
   * @return the device identification
   * @throws IllegalArgumentException if device not found or disabled
   * @throws RuntimeException if reading fails
   */
  @Transactional
  public DeviceIdentification refreshDevice(Long deviceId) {
    ModbusDeviceEntity device = deviceRepository.findByIdOptional(deviceId)
      .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

    if (!device.enabled) {
      throw new IllegalArgumentException("Device is disabled: " + deviceId);
    }

    LOG.infof("Manual refresh requested for device %d (%s)", device.id, device.name);

    // Invalidate discovery cache so fresh model addresses are resolved
    DeviceAddress address = DeviceAddress.fromEntity(device);
    sunSpecService.invalidateDiscovery(address);

    try {
      SunSpecModelData model = sunSpecService.readCommonModel(address);
      DeviceIdentification identification = toDeviceIdentification(model);

      // Update database and cache
      ModbusDeviceInfoEntity infoEntity = deviceRepository.findOrCreateDeviceInfo(device.id);
      infoEntity.updateFrom(identification, true, null);
      infoEntity.persist();
      cacheService.put(device.id, identification);

      LOG.infof("Manual refresh completed for device %d (%s)", device.id, device.name);
      return identification;
    } catch (Exception e) {
      throw new RuntimeException("Failed to refresh device " + deviceId + ": " + e.getMessage(), e);
    }
  }

  /**
   * Reads the SunSpec Common model (1) with retry logic and exponential backoff,
   * then maps the fields to a {@link DeviceIdentification}.
   *
   * <p>The Fronius GEN24 and Smart Meter do not support FC 0x2B (Read Device
   * Identification). Device info is read from the SunSpec Common model via
   * FC 0x03. Register addresses are discovered dynamically per the Fronius
   * documentation: discover the model chain first, then use offsets.</p>
   *
   * <p>Field mapping from SunSpec Common model to DeviceIdentification:</p>
   * <ul>
   *   <li>{@code Mn} (Manufacturer) -> {@code vendorName}</li>
   *   <li>{@code Md} (Device/Model) -> {@code productCode}</li>
   *   <li>{@code Vr} (SW Version) -> {@code majorMinorRevision}</li>
   *   <li>{@code SN} (Serial Number) -> {@code productName}</li>
   * </ul>
   *
   * @param address the target device address
   * @return the device identification
   * @throws RuntimeException if all attempts fail
   */
  private DeviceIdentification readCommonModelWithRetry(DeviceAddress address) {
    Exception lastException = null;
    long delayMs = retryDelaySeconds * 1000L;

    for (int attempt = 1; attempt <= retryAttempts; attempt++) {
      try {
        SunSpecModelData model = sunSpecService.readCommonModel(address);
        return toDeviceIdentification(model);
      } catch (Exception e) {
        lastException = e;
        if (attempt < retryAttempts) {
          LOG.debugf("Attempt %d/%d failed for %s, retrying in %d ms: %s",
            attempt, retryAttempts, address, delayMs, e.getMessage());
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry", ie);
          }
          // Exponential backoff: double the delay, cap at 4x the initial
          delayMs = Math.min(delayMs * 2, retryDelaySeconds * 4000L);
        }
      }
    }
    throw new RuntimeException("Failed to read common model after " + retryAttempts + " attempts", lastException);
  }

  /**
   * Maps SunSpec Common model data to a {@link DeviceIdentification} record.
   *
   * @param model decoded Common model (ID 1) data
   * @return populated DeviceIdentification
   */
  private DeviceIdentification toDeviceIdentification(SunSpecModelData model) {
    String mn = Objects.requireNonNullElse(model.getString("Mn"), "").trim();
    String md = Objects.requireNonNullElse(model.getString("Md"), "").trim();
    String vr = Objects.requireNonNullElse(model.getString("Vr"), "").trim();
    String sn = Objects.requireNonNullElse(model.getString("SN"), "").trim();
    return new DeviceIdentification(mn, md, vr, null, sn, null, null, Map.of(), Instant.now());
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
