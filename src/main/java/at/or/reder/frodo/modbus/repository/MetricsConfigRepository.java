package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link MetricsConfigEntity} persistence operations.
 *
 * <p>Provides data access methods for metrics configuration management,
 * including finding configs by device, listing enabled configs, and
 * eager-fetching parameters.</p>
 *
 * <p>Uses Panache repository pattern for simplified JPA operations.
 * All write operations are transactional.</p>
 */
@ApplicationScoped
public class MetricsConfigRepository implements PanacheRepository<MetricsConfigEntity> {

  /**
   * Finds the metrics config for a specific device.
   *
   * @param deviceId the device ID
   * @return Optional containing the config, or empty if not configured
   */
  public Optional<MetricsConfigEntity> findByDeviceId(Long deviceId) {
    return find("device.id", deviceId).firstResultOptional();
  }

  /**
   * Finds the metrics config for a device, eagerly fetching parameters.
   *
   * @param deviceId the device ID
   * @return Optional containing the config with parameters loaded, or empty
   */
  public Optional<MetricsConfigEntity> findByDeviceIdWithParameters(Long deviceId) {
    List<MetricsConfigEntity> results = find(
      "SELECT c FROM MetricsConfigEntity c LEFT JOIN FETCH c.parameters WHERE c.device.id = ?1",
      deviceId
    ).list();
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  /**
   * Lists all enabled metrics configs with their parameters eagerly loaded.
   *
   * @return list of enabled configs
   */
  public List<MetricsConfigEntity> findAllEnabled() {
    return find(
      "SELECT c FROM MetricsConfigEntity c LEFT JOIN FETCH c.parameters WHERE c.enabled = true"
    ).list();
  }

  /**
   * Lists all metrics configs (enabled and disabled).
   *
   * @return list of all configs
   */
  public List<MetricsConfigEntity> listAllConfigs() {
    return listAll();
  }

  /**
   * Persists or updates a metrics config entity.
   *
   * @param entity the config entity to save
   * @return the persisted/updated entity
   */
  @Transactional
  public MetricsConfigEntity save(MetricsConfigEntity entity) {
    persist(entity);
    return entity;
  }

  /**
   * Deletes a metrics config by device ID.
   *
   * @param deviceId the device ID
   * @return true if a config was deleted, false if not found
   */
  @Transactional
  public boolean deleteByDeviceId(Long deviceId) {
    return delete("device.id", deviceId) > 0;
  }
}
