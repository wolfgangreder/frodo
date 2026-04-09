package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
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
 *
 * <p><strong>Jaybird/Firebird compatibility:</strong> Methods that need
 * both device and parameters use a two-step approach: first a
 * {@code JOIN FETCH} for the device (1:1 association), then a separate
 * lazy-load initialization for the parameters collection. This avoids
 * the {@code LEFT JOIN FETCH} on collections which can trigger Jaybird's
 * "The result set is closed" error (ISC error 337248339) when Hibernate's
 * result set processing conflicts with Jaybird's auto-close behavior.</p>
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
   * Finds the metrics config for a device, eagerly fetching the device and parameters.
   *
   * <p>Uses a two-step approach for Jaybird compatibility: loads the config
   * with its device via {@code JOIN FETCH}, then force-initializes the
   * parameters collection in a separate query.</p>
   *
   * @param deviceId the device ID
   * @return Optional containing the config with device and parameters loaded, or empty
   */
  @Transactional
  public Optional<MetricsConfigEntity> findByDeviceIdWithParameters(Long deviceId) {
    EntityManager em = getEntityManager();

    // Step 1: Load config with device (1:1 join, no collection fetch)
    List<MetricsConfigEntity> results = em.createQuery(
      "SELECT c FROM MetricsConfigEntity c JOIN FETCH c.device WHERE c.device.id = ?1",
      MetricsConfigEntity.class
    ).setParameter(1, deviceId)
    .getResultList();

    if (results.isEmpty()) {
      return Optional.empty();
    }

    // Step 2: Force-initialize parameters collection (triggers clean lazy load)
    MetricsConfigEntity config = results.get(0);
    config.parameters.size();

    return Optional.of(config);
  }

  /**
   * Lists all enabled metrics configs with their device and parameters eagerly loaded.
   *
   * <p>Uses a two-step approach for Jaybird compatibility: loads all enabled
   * configs with their devices via {@code JOIN FETCH}, then force-initializes
   * each config's parameters collection. This avoids the cartesian product
   * explosion and result set conflicts of a single multi-{@code JOIN FETCH}
   * query.</p>
   *
   * @return list of enabled configs with parameters populated
   */
  @Transactional
  public List<MetricsConfigEntity> findAllEnabled() {
    EntityManager em = getEntityManager();

    // Step 1: Load enabled configs with device (1:1 join, no collection fetch)
    List<MetricsConfigEntity> configs = em.createQuery(
      "SELECT c FROM MetricsConfigEntity c JOIN FETCH c.device WHERE c.enabled = true",
      MetricsConfigEntity.class
    ).getResultList();

    // Step 2: Force-initialize parameters collections
    // Each lazy load executes a clean, sequential query
    for (MetricsConfigEntity config : configs) {
      config.parameters.size();
    }

    return configs;
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
