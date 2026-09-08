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
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
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

/**
 * Scheduled service to refresh the SunSpec discovery cache for all enabled devices.
 *
 * <p>Periodically re-runs SunSpec model chain discovery so the in-memory cache
 * never goes stale. Without this, the cache would only be populated on the first
 * request and would expire after {@code frodo.sunspec.health.max-cache-age-hours}
 * (default: 24 h), causing the health check to report DOWN even though
 * Modbus communication is healthy.</p>
 *
 * <p>The refresh interval is configurable via
 * {@code frodo.sunspec.discovery.refresh-interval} (default: {@code 6h}).
 * Choose a value smaller than {@code frodo.sunspec.health.max-cache-age-hours}
 * to ensure the cache stays valid.</p>
 *
 * <p>Individual device failures are logged but do not abort the run.</p>
 */
@ApplicationScoped
public class SunSpecDiscoveryRefreshService {

  private static final Logger LOG = Logger.getLogger(SunSpecDiscoveryRefreshService.class);

  private volatile boolean shuttingDown = false;

  @Inject
  SunSpecService sunSpecService;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "quarkus.hibernate-orm.enabled", defaultValue = "true")
  boolean hibernateEnabled;

  void onStop(@Observes ShutdownEvent event) {
    shuttingDown = true;
    LOG.info("Shutdown event received, stopping SunSpec discovery refresh");
  }

  /**
   * Scheduled job to refresh SunSpec discovery for all enabled devices.
   *
   * <p>Runs according to the configured refresh interval (default: 6 hours),
   * with an initial delay to let the application fully start. Skipped in
   * test/dev modes when Hibernate is disabled.</p>
   */
  @Scheduled(
    every = "${frodo.sunspec.discovery.refresh-interval:6h}",
    delayed = "2m",
    identity = "sunspec-discovery-refresh"
  )
  @Transactional
  void refreshAllDevices() {
    if (shuttingDown) {
      LOG.debug("Skipping SunSpec discovery refresh: application is shutting down");
      return;
    }
    if (!hibernateEnabled) {
      LOG.debug("Skipping SunSpec discovery refresh: Hibernate ORM is disabled");
      return;
    }
    if (!modbusEnabled) {
      LOG.debug("Skipping SunSpec discovery refresh: Modbus is disabled");
      return;
    }

    Instant start = Instant.now();
    LOG.info("Starting SunSpec discovery refresh for all enabled devices");

    List<ModbusDeviceEntity> devices = deviceRepository.listAllEnabled();
    if (devices.isEmpty()) {
      LOG.info("No enabled devices found, skipping SunSpec discovery refresh");
      return;
    }

    LOG.infof("Found %d enabled device(s) to refresh SunSpec discovery for", devices.size());

    int successCount = 0;
    int failureCount = 0;

    for (ModbusDeviceEntity device : devices) {
      if (shuttingDown) {
        LOG.info("Aborting SunSpec discovery refresh: application is shutting down");
        break;
      }
      DeviceAddress address = DeviceAddress.fromEntity(device);
      try {
        sunSpecService.discover(address);
        LOG.debugf("SunSpec discovery refreshed for device %d (%s)", device.id, address);
        successCount++;
      } catch (Exception e) {
        LOG.warnf("SunSpec discovery refresh failed for device %d (%s): %s",
          device.id, address, e.getMessage());
        failureCount++;
      }
    }

    Duration duration = Duration.between(start, Instant.now());
    LOG.infof("SunSpec discovery refresh completed in %d ms: %d success, %d failures",
      duration.toMillis(), successCount, failureCount);
  }
}
