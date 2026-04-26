package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.PriceControlEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Repository for the {@link PriceControlEntity} singleton.
 *
 * <p>At most one row exists in {@code FroPriceControl}.  All read operations
 * return the first (and only) row; the {@link #save} method upserts it.</p>
 */
@ApplicationScoped
public class PriceControlRepository implements PanacheRepository<PriceControlEntity> {

  /**
   * Returns the global price-control setting, or empty if it has never been
   * saved (i.e. the feature has not been configured yet — treat as disabled).
   *
   * @return the singleton entity, or empty
   */
  public Optional<PriceControlEntity> findSingleton() {
    return findAll().firstResultOptional();
  }

  /**
   * Creates or updates the global price-control setting.
   *
   * @param enabled              whether price control is active
   * @param exportToleranceWatts allowed export buffer in Watts (≥ 0)
   * @return the persisted entity
   */
  @Transactional
  public PriceControlEntity save(boolean enabled, int exportToleranceWatts) {
    PriceControlEntity entity = findSingleton().orElseGet(PriceControlEntity::new);
    entity.enabled = enabled;
    entity.exportToleranceWatts = Math.max(0, exportToleranceWatts);
    persist(entity);
    return entity;
  }
}
