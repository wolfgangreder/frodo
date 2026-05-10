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

package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceInfoEntity;
import at.or.reder.frodo.modbus.model.DeviceType;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ModbusDeviceEntity} persistence operations.
 *
 * <p>Provides data access methods for device configuration management,
 * including finding enabled devices, fetching with device info, and
 * managing device info entities.</p>
 *
 * <p>Uses Panache repository pattern for simplified JPA operations.
 * All write operations are transactional.</p>
 */
@ApplicationScoped
public class ModbusDeviceRepository implements PanacheRepository<ModbusDeviceEntity> {

  /**
   * Finds the first enabled device, ordered by ID ascending.
   *
   * <p>This is used for Stage 1 compatibility where only a single
   * device is supported. Returns the lowest ID enabled device.</p>
   *
   * @return Optional containing the first enabled device, or empty if none found
   */
  public Optional<ModbusDeviceEntity> findFirstEnabled() {
    return find("enabled", Sort.by("id").ascending(), true)
      .firstResultOptional();
  }

  /**
   * Finds a device by ID and eagerly fetches its device info.
   *
   * @param id the device ID
   * @return Optional containing the device with info, or empty if not found
   */
  public Optional<ModbusDeviceEntity> findByIdWithInfo(Long id) {
    return find("SELECT d FROM ModbusDeviceEntity d LEFT JOIN FETCH d.deviceInfo WHERE d.id = ?1", id)
      .firstResultOptional();
  }

  /**
   * Lists all devices, ordered by ID ascending.
   *
   * @return list of all devices (enabled and disabled)
   */
  public List<ModbusDeviceEntity> listAllDevices() {
    return listAll(Sort.by("id").ascending());
  }

  /**
   * Lists all enabled devices, ordered by ID ascending.
   *
   * @return list of enabled devices only
   */
  public List<ModbusDeviceEntity> listAllEnabled() {
    return list("enabled", Sort.by("id").ascending(), true);
  }

  /**
   * Persists or updates a device entity.
   *
   * @param entity the device entity to save
   * @return the persisted/updated entity
   */
  @Transactional
  public ModbusDeviceEntity save(ModbusDeviceEntity entity) {
    persist(entity);
    return entity;
  }

  /**
   * Deletes a device by ID.
   *
   * @param id the device ID
   * @return true if the device was deleted, false if not found
   */
  @Transactional
  public boolean deleteDevice(Long id) {
    return deleteById(id);
  }

  /**
   * Finds or creates a device info entity for the given device ID.
   *
   * <p>If a device info entity already exists for this device, returns it.
   * Otherwise, creates a new entity associated with the device.</p>
   *
   * @param deviceId the device ID
   * @return existing or new device info entity
   * @throws IllegalArgumentException if the device does not exist
   */
  @Transactional
  public ModbusDeviceInfoEntity findOrCreateDeviceInfo(Long deviceId) {
    ModbusDeviceEntity device = findById(deviceId);
    if (device == null) {
      throw new IllegalArgumentException("Device not found: " + deviceId);
    }

    ModbusDeviceInfoEntity info = ModbusDeviceInfoEntity.find("device.id", deviceId).firstResult();
    if (info != null) {
      return info;
    }

    info = new ModbusDeviceInfoEntity();
    info.device = device;
    info.persist();
    return info;
  }

  /**
   * Finds a device info entity by device ID.
   *
   * @param deviceId the device ID
   * @return Optional containing the device info, or empty if not found
   */
  public Optional<ModbusDeviceInfoEntity> findDeviceInfo(Long deviceId) {
    return ModbusDeviceInfoEntity.<ModbusDeviceInfoEntity>find("device.id", deviceId)
      .firstResultOptional();
  }

  /**
   * Finds a device by host, port, and unit ID.
   *
   * @param host   Modbus TCP host
   * @param port   Modbus TCP port
   * @param unitId Modbus unit ID
   * @return Optional containing the matching device, or empty if not found
   */
  public Optional<ModbusDeviceEntity> findByConnection(String host, int port, int unitId) {
    return find("host = ?1 and port = ?2 and unitId = ?3", host, port, unitId)
      .firstResultOptional();
  }

  /**
   * Lists devices filtered by device type.
   *
   * @param deviceType the device type to filter by
   * @return list of devices with the specified type
   */
  public List<ModbusDeviceEntity> listByDeviceType(DeviceType deviceType) {
    return list("deviceType", Sort.by("id").ascending(), deviceType);
  }

  /**
   * Lists enabled devices that are inverter candidates.
   *
   * <p>Returns all enabled devices where either:</p>
   * <ul>
   *   <li>{@code deviceType = INVERTER} — explicitly classified inverters, or</li>
   *   <li>{@code deviceType IS NULL AND parentDevice IS NULL} — root-level untyped
   *       devices (manually configured before automatic device-type detection was
   *       introduced; the main inverter is always a root-level device).</li>
   * </ul>
   *
   * @return list of enabled inverter candidates, sorted by ID
   */
  public List<ModbusDeviceEntity> listEnabledInverterCandidates() {
    return list(
      "(deviceType = ?1 OR (deviceType IS NULL AND parentDevice IS NULL)) AND enabled = true",
      Sort.by("id").ascending(),
      DeviceType.INVERTER);
  }

  /**
   * Lists child devices of a parent device.
   *
   * @param parentDeviceId the parent device ID
   * @return list of child devices
   */
  public List<ModbusDeviceEntity> listChildDevices(Long parentDeviceId) {
    return list("parentDevice.id", Sort.by("id").ascending(), parentDeviceId);
  }

  /**
   * Lists all auto-discovered devices.
   *
   * @return list of auto-discovered devices
   */
  public List<ModbusDeviceEntity> listAutoDiscovered() {
    return list("autoDiscovered", Sort.by("id").ascending(), true);
  }

  /**
   * Finds the first enabled Smart Meter for the given parent device.
   *
   * <p>Strategy (child-first, same-gateway fallback):</p>
   * <ol>
   *   <li>Look for an enabled {@link DeviceType#SMART_METER} among the
   *       child devices of {@code parentDeviceId}.</li>
   *   <li>If none is found, look for any enabled device on the same Modbus
   *       TCP gateway ({@code host:port}) that either has
   *       {@code deviceType = SMART_METER} or has no device type set
   *       ({@code deviceType IS NULL}).  The parent device itself is excluded.
   *       This covers the common case where a Smart Meter was manually added to
   *       the DB without setting a device type or a parent-device link.</li>
   * </ol>
   *
   * @param parentDeviceId the inverter device ID
   * @return first matching Smart Meter, or empty if none is found
   */
  public Optional<ModbusDeviceEntity> findSmartMeterForDevice(Long parentDeviceId) {
    // 1. Child-device lookup (preferred — explicit relationship)
    Optional<ModbusDeviceEntity> child = listChildDevices(parentDeviceId).stream()
      .filter(d -> DeviceType.SMART_METER == d.deviceType && d.enabled)
      .findFirst();
    if (child.isPresent()) {
      return child;
    }

    // 2. Same-gateway fallback — typed SMART_METER or untyped device on same host:port,
    // excluding the parent inverter. Prefer typed (SMART_METER) over untyped (null).
    ModbusDeviceEntity parent = findById(parentDeviceId);
    if (parent == null) {
      return Optional.empty();
    }

    List<ModbusDeviceEntity> candidates = list(
      "(deviceType = ?1 OR deviceType IS NULL) AND enabled = ?2 AND host = ?3 AND port = ?4 AND id <> ?5",
      Sort.by("id").ascending(),
      DeviceType.SMART_METER, Boolean.TRUE, parent.host, parent.port, parentDeviceId);

    // Prefer explicitly typed SMART_METER; fall back to null-typed (unclassified) device
    return candidates.stream()
      .filter(d -> DeviceType.SMART_METER == d.deviceType)
      .findFirst()
      .or(() -> candidates.stream().findFirst());
  }
}
