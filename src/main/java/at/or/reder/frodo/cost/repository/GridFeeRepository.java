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

package at.or.reder.frodo.cost.repository;

import at.or.reder.frodo.cost.entity.GridFeeEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for {@link GridFeeEntity} — grid surcharge rules.
 */
@ApplicationScoped
public class GridFeeRepository implements PanacheRepository<GridFeeEntity> {

  /**
   * Returns all fees whose {@code validFrom} is on or before the given time,
   * ordered by {@code validFrom} ascending.
   *
   * <p>All such fees are considered active (no {@code validTo}; delete to deactivate).</p>
   *
   * @param at point in time
   * @return active fee entities
   */
  public List<GridFeeEntity> findActiveFeesForTime(LocalDateTime at) {
    return list("validFrom <= ?1 order by validFrom asc", at);
  }

  /**
   * Persists a new grid fee.
   *
   * @param fee the fee entity to persist
   * @return the persisted entity
   */
  @Transactional
  public GridFeeEntity save(GridFeeEntity fee) {
    persist(fee);
    return fee;
  }

  /**
   * Updates an existing grid fee by id.
   *
   * @param id          fee id
   * @param updatedFee  entity with updated values
   * @return updated entity, or empty Optional if not found
   */
  @Transactional
  public java.util.Optional<GridFeeEntity> update(long id, GridFeeEntity updatedFee) {
    GridFeeEntity existing = findById(id);
    if (existing == null) {
      return java.util.Optional.empty();
    }
    existing.validFrom = updatedFee.validFrom;
    existing.feeType = updatedFee.feeType;
    existing.feeValue = updatedFee.feeValue;
    existing.appliesTo = updatedFee.appliesTo;
    existing.description = updatedFee.description;
    return java.util.Optional.of(existing);
  }
}
