package at.or.reder.frodo.cost.repository;

import at.or.reder.frodo.cost.entity.MonthlyCostEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link MonthlyCostEntity} — pre-calculated monthly cost summaries.
 */
@ApplicationScoped
public class MonthlyCostRepository implements PanacheRepository<MonthlyCostEntity> {

  /**
   * Finds the monthly cost record for a specific year-month.
   *
   * @param yearMonth year-month key in format {@code "yyyy-MM"} (e.g. {@code "2026-05"})
   * @return monthly cost entity if found
   */
  public Optional<MonthlyCostEntity> findByYearMonth(String yearMonth) {
    return find("yearMonth", yearMonth).firstResultOptional();
  }

  /**
   * Returns all monthly cost records ordered by year-month descending.
   *
   * @return list of all monthly cost entities
   */
  public List<MonthlyCostEntity> listAllDesc() {
    return list("order by yearMonth desc");
  }

  /**
   * Creates or updates the monthly cost summary.
   *
   * @param entity fully populated monthly cost entity
   * @return the persisted entity
   */
  @Transactional
  public MonthlyCostEntity upsert(MonthlyCostEntity entity) {
    Optional<MonthlyCostEntity> existing = findByYearMonth(entity.yearMonth);
    if (existing.isPresent()) {
      MonthlyCostEntity e = existing.get();
      e.totalImportKwh = entity.totalImportKwh;
      e.totalExportKwh = entity.totalExportKwh;
      e.totalImportCostEur = entity.totalImportCostEur;
      e.totalExportIncomeEur = entity.totalExportIncomeEur;
      e.totalFeeEur = entity.totalFeeEur;
      e.fixedCostEur = entity.fixedCostEur;
      e.netCostEur = entity.netCostEur;
      e.hoursCalculated = entity.hoursCalculated;
      return e;
    }
    persist(entity);
    return entity;
  }

  /**
   * Deletes monthly cost records older than the given year-month.
   *
   * @param beforeYearMonth year-month key (exclusive lower bound); rows with
   *                        {@code yearMonth < beforeYearMonth} are deleted
   * @return number of deleted rows
   */
  @Transactional
  public int deleteOlderThan(String beforeYearMonth) {
    return (int) delete("yearMonth < ?1", beforeYearMonth);
  }
}
