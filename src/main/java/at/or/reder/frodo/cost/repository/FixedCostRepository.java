package at.or.reder.frodo.cost.repository;

import at.or.reder.frodo.cost.entity.FixedCostEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for {@link FixedCostEntity} — recurring standing charges.
 *
 * <p>An entry is active for a given month when its {@code validFrom} is on or before the
 * first day of that month. Multiple entries can be active simultaneously — all are summed.
 * Delete by ID to deactivate (same pattern as {@link GridFeeRepository}).</p>
 */
@ApplicationScoped
public class FixedCostRepository implements PanacheRepository<FixedCostEntity> {

  /**
   * Returns all fixed costs active on the given date, i.e. entries whose
   * {@code validFrom &lt;= date}, ordered by {@code validFrom} ascending.
   *
   * @param date typically the first day of the month being calculated
   * @return active fixed cost entries
   */
  public List<FixedCostEntity> findActiveForDate(LocalDate date) {
    return list("validFrom <= ?1 order by validFrom asc", date);
  }

  /**
   * Persists a new fixed cost entry.
   *
   * @param fee the entity to persist
   * @return the persisted entity
   */
  @Transactional
  public FixedCostEntity save(FixedCostEntity fee) {
    persist(fee);
    return fee;
  }

  /**
   * Updates an existing fixed cost entry by id.
   *
   * @param id         entry id
   * @param updatedFee entity with updated values
   * @return updated entity, or empty Optional if not found
   */
  @Transactional
  public java.util.Optional<FixedCostEntity> update(long id, FixedCostEntity updatedFee) {
    FixedCostEntity existing = findById(id);
    if (existing == null) {
      return java.util.Optional.empty();
    }
    existing.validFrom = updatedFee.validFrom;
    existing.direction = updatedFee.direction;
    existing.monthlyCostEur = updatedFee.monthlyCostEur;
    existing.description = updatedFee.description;
    return java.util.Optional.of(existing);
  }
}
