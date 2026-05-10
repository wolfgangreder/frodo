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

package at.or.reder.frodo.modbus.config;

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Initializes device configuration from application properties on startup.
 *
 * <p>This component provides Stage 1 compatibility by seeding a single
 * device configuration from {@code frodo.modbus.device.*} properties
 * if the database is empty and seeding is enabled.</p>
 *
 * <p>Seed behavior:</p>
 * <ul>
 *   <li>If {@code frodo.modbus.device.seed-from-config=false}, no action taken</li>
 *   <li>If database already contains devices, no action taken</li>
 *   <li>If {@code frodo.modbus.enabled=false}, device is created but disabled</li>
 *   <li>Otherwise, creates a single enabled device from properties</li>
 * </ul>
 */
@ApplicationScoped
public class DeviceConfigInitializer {

  private static final Logger LOG = Logger.getLogger(DeviceConfigInitializer.class);

  @Inject
  ModbusDeviceRepository repository;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "frodo.modbus.device.seed-from-config", defaultValue = "true")
  boolean seedFromConfig;

  @ConfigProperty(name = "quarkus.hibernate-orm.enabled", defaultValue = "true")
  boolean hibernateEnabled;

  @ConfigProperty(name = "frodo.modbus.device.host", defaultValue = "localhost")
  String deviceHost;

  @ConfigProperty(name = "frodo.modbus.device.port", defaultValue = "502")
  int devicePort;

  @ConfigProperty(name = "frodo.modbus.device.unit-id", defaultValue = "1")
  int deviceUnitId;

  @ConfigProperty(name = "frodo.modbus.device.name", defaultValue = "Default PV Device")
  String deviceName;

  @ConfigProperty(name = "frodo.modbus.device.description")
  Optional<String> deviceDescription;

  @ConfigProperty(name = "frodo.modbus.connection.timeout-seconds", defaultValue = "30")
  int connectionTimeoutSeconds;

  /**
   * Handles application startup event to seed device configuration.
   *
   * @param event the startup event
   */
  @Transactional
  void onStart(@Observes StartupEvent event) {
    if (!hibernateEnabled) {
      LOG.debug("Hibernate ORM is disabled, skipping device seeding");
      return;
    }

    if (!seedFromConfig) {
      LOG.info("Device seeding from config is disabled");
      return;
    }

    long deviceCount = repository.count();
    if (deviceCount > 0) {
      LOG.infof("Database already contains %d device(s), skipping seed from config", deviceCount);
      return;
    }

    LOG.info("Seeding device configuration from application properties");

    ModbusDeviceEntity device = new ModbusDeviceEntity();
    device.name = deviceName;
    device.host = deviceHost;
    device.port = devicePort;
    device.unitId = deviceUnitId;
    device.enabled = modbusEnabled;  // Inherit from global modbus.enabled setting
    device.description = deviceDescription.orElse("Seeded from application properties");
    device.connectionTimeoutSeconds = connectionTimeoutSeconds;

    repository.save(device);

    LOG.infof("Created device configuration: id=%d, name='%s', connection=%s, enabled=%s",
      device.id, device.name, device.getConnectionString(), device.enabled);
  }
}
