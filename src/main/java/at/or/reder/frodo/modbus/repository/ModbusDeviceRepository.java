package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceInfoEntity;
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
}
