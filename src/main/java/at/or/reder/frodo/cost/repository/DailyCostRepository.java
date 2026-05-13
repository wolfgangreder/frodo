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

import at.or.reder.frodo.cost.entity.DailyCostEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link DailyCostEntity} — pre-calculated daily cost summaries.
 */
@ApplicationScoped
public class DailyCostRepository implements PanacheRepository<DailyCostEntity> {

  /**
   * Finds the daily cost record for a specific day.
   *
   * @param day day key in format {@code "yyyy-MM-dd"} (e.g. {@code "2026-05-10"})
   * @return daily cost entity if found
   */
  public Optional<DailyCostEntity> findByDay(String day) {
    return find("costDay", day).firstResultOptional();
  }

  /**
   * Returns daily cost records in a day range, ordered by day descending (newest first).
   *
   * @param from range start inclusive (format {@code "yyyy-MM-dd"})
   * @param to   range end exclusive (format {@code "yyyy-MM-dd"})
   * @return list of daily cost entities
   */
  public List<DailyCostEntity> findByDateRange(String from, String to) {
    return list("costDay >= ?1 and costDay < ?2 order by costDay desc", from, to);
  }

  /**
   * Returns all daily cost records ordered by day descending (newest first).
   *
   * @return list of all daily cost entities
   */
  public List<DailyCostEntity> listAllDesc() {
    return list("order by costDay desc");
  }

  /**
   * Creates or updates the daily cost summary.
   *
   * @param entity fully populated daily cost entity
   * @return the persisted entity
   */
  @Transactional
  public DailyCostEntity upsert(DailyCostEntity entity) {
    Optional<DailyCostEntity> existing = findByDay(entity.costDay);
    if (existing.isPresent()) {
      DailyCostEntity e = existing.get();
      e.totalImportKwh = entity.totalImportKwh;
      e.totalExportKwh = entity.totalExportKwh;
      e.totalImportCostEur = entity.totalImportCostEur;
      e.totalExportIncomeEur = entity.totalExportIncomeEur;
      e.totalFeeEur = entity.totalFeeEur;
      e.netCostEur = entity.netCostEur;
      e.hoursCalculated = entity.hoursCalculated;
      return e;
    }
    persist(entity);
    return entity;
  }

  /**
   * Deletes daily cost records older than the given day.
   *
   * @param beforeDay day key (exclusive); rows with {@code day < beforeDay} are deleted
   * @return number of deleted rows
   */
  @Transactional
  public int deleteOlderThan(String beforeDay) {
    return (int) delete("costDay < ?1", beforeDay);
  }
}
