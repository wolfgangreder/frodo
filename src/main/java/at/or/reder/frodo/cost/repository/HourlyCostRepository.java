package at.or.reder.frodo.cost.repository;

import at.or.reder.frodo.cost.entity.HourlyCostEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HourlyCostEntity} — hourly cost and income records.
 */
@ApplicationScoped
public class HourlyCostRepository implements PanacheRepository<HourlyCostEntity> {

  /**
   * Finds the cost record for a specific hour start.
   *
   * @param hourStart exact hour start (UTC)
   * @return cost entity if found
   */
  public Optional<HourlyCostEntity> findByHourStart(LocalDateTime hourStart) {
    return find("hourStart", hourStart).firstResultOptional();
  }

  /**
   * Returns cost records in a date range, ordered by hour ascending.
   *
   * @param from range start (inclusive)
   * @param to   range end (exclusive)
   * @return list of hourly cost entities
   */
  public List<HourlyCostEntity> findByDateRange(LocalDateTime from, LocalDateTime to) {
    return list("hourStart >= ?1 and hourStart < ?2 order by hourStart asc", from, to);
  }

  /**
   * Returns the latest cost record (most recent completed hour).
   *
   * @return latest entity if any exists
   */
  public Optional<HourlyCostEntity> findLatest() {
    return find("order by hourStart desc").firstResultOptional();
  }

  /**
   * Creates or updates the hourly cost record for an hour.
   *
   * @param entity fully populated cost entity
   * @return the persisted entity
   */
  @Transactional
  public HourlyCostEntity upsert(HourlyCostEntity entity) {
    Optional<HourlyCostEntity> existing = findByHourStart(entity.hourStart);
    if (existing.isPresent()) {
      HourlyCostEntity e = existing.get();
      e.hourEnd = entity.hourEnd;
      e.importKwh = entity.importKwh;
      e.exportKwh = entity.exportKwh;
      e.priceImportCt = entity.priceImportCt;
      e.priceExportCt = entity.priceExportCt;
      e.importPriceSource = entity.importPriceSource;
      e.exportPriceSource = entity.exportPriceSource;
      e.importCostEur = entity.importCostEur;
      e.exportIncomeEur = entity.exportIncomeEur;
      e.feeEur = entity.feeEur;
      e.netCostEur = entity.netCostEur;
      return e;
    }
    persist(entity);
    return entity;
  }

  /**
   * Deletes cost records older than the given cutoff.
   *
   * @param before cutoff time (rows with hourEnd before this are deleted)
   * @return number of deleted rows
   */
  @Transactional
  public int deleteOlderThan(LocalDateTime before) {
    return (int) delete("hourEnd < ?1", before);
  }
}
