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

package at.or.reder.frodo.modbus.entity;

import at.or.reder.frodo.modbus.model.DeviceType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Modbus TCP device configuration.
 *
 * <p>This entity stores the connection details (host, port, unit ID) and
 * settings for a single Modbus device. Each device has an optional
 * one-to-one relationship with {@link ModbusDeviceInfoEntity} that caches
 * the device identification data.</p>
 *
 * <p>Uses Panache for simplified persistence operations. The {@code id}
 * field is inherited from {@link PanacheEntity}.</p>
 */
@Entity
@Table(
  name = "FroModbusDevice",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroDevice_connection",
    columnNames = {"host", "port", "unit_id"}
  )
)
public class ModbusDeviceEntity extends PanacheEntity {

  /**
   * Human-readable name for the device (e.g., "PV Inverter 1").
   */
  @Column(nullable = false, length = 255)
  @NotBlank(message = "Device name must not be blank")
  public String name;

  /**
   * Hostname or IP address of the Modbus TCP server.
   */
  @Column(nullable = false, length = 255)
  @NotBlank(message = "Device host must not be blank")
  public String host;

  /**
   * TCP port number (typically 502 for Modbus TCP).
   */
  @Column(nullable = false)
  @Min(value = 1, message = "Port must be between 1 and 65535")
  @Max(value = 65535, message = "Port must be between 1 and 65535")
  public int port;

  /**
   * Modbus unit/slave ID (1-247 per Modbus specification).
   */
  @Column(name = "unit_id", nullable = false)
  @Min(value = 1, message = "Unit ID must be between 1 and 247")
  @Max(value = 247, message = "Unit ID must be between 1 and 247")
  public int unitId;

  /**
   * Whether this device is enabled for communication.
   * Disabled devices are not queried by scheduled jobs.
   */
  @Column(nullable = false)
  public boolean enabled = true;

  /**
   * Optional description or notes about the device.
   */
  @Column(length = 1000)
  public String description;

  /**
   * Connection timeout in seconds (overrides global setting if specified).
   */
  @Column(name = "connection_timeout_seconds")
  @Min(value = 1, message = "Connection timeout must be at least 1 second")
  public Integer connectionTimeoutSeconds;

  /**
   * Timestamp when this entity was created.
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  /**
   * Timestamp when this entity was last updated.
   */
  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  /**
   * Type of device (inverter, meter, Ohmpilot, etc.).
   * Nullable for legacy devices; auto-detected during discovery.
   */
  @Column(name = "device_type", length = 50)
  @Enumerated(EnumType.STRING)
  public DeviceType deviceType;

  /**
   * Parent device in the Modbus gateway hierarchy.
   * For sub-devices (meters, Ohmpilots), this points to the inverter
   * that acts as the Modbus TCP gateway.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_device_id")
  public ModbusDeviceEntity parentDevice;

  /**
   * Child devices discovered via this device's gateway connection.
   */
  @OneToMany(mappedBy = "parentDevice", cascade = CascadeType.ALL)
  public List<ModbusDeviceEntity> childDevices = new ArrayList<>();

  /**
   * Whether this device was automatically discovered (true)
   * or manually configured (false).
   */
  @Column(name = "auto_discovered", nullable = false)
  public boolean autoDiscovered = false;

  /**
   * Cached device identification information.
   * One-to-one relationship, lazily loaded.
   */
  @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
  public ModbusDeviceInfoEntity deviceInfo;

  /**
   * Returns a formatted connection string for logging and display.
   *
   * @return connection string in format "host:port/unitId"
   */
  public String getConnectionString() {
    return String.format("%s:%d/%d", host, port, unitId);
  }

  /**
   * JPA lifecycle callback: set createdAt and updatedAt before persist.
   */
  @jakarta.persistence.PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  /**
   * JPA lifecycle callback: update updatedAt before update.
   */
  @jakarta.persistence.PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
