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

import at.or.reder.frodo.cost.entity.HourlyEnergyEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HourlyEnergyEntity} — hourly grid import/export kWh.
 */
@ApplicationScoped
public class HourlyEnergyRepository implements PanacheRepository<HourlyEnergyEntity> {

  /**
   * Finds the energy record for a specific hour start.
   *
   * @param hourStart exact hour start (UTC)
   * @return energy entity if found
   */
  public Optional<HourlyEnergyEntity> findByHourStart(LocalDateTime hourStart) {
    return find("hourStart", hourStart).firstResultOptional();
  }

  /**
   * Returns energy records in a date range, ordered by hour ascending.
   *
   * @param from range start (inclusive)
   * @param to   range end (exclusive)
   * @return list of hourly energy entities
   */
  public List<HourlyEnergyEntity> findByDateRange(LocalDateTime from, LocalDateTime to) {
    return list("hourStart >= ?1 and hourStart < ?2 order by hourStart asc", from, to);
  }

  /**
   * Creates or updates the hourly energy record for an hour.
   *
   * @param hourStart   hour start
   * @param hourEnd     hour end
   * @param importKwh   kWh imported
   * @param exportKwh   kWh exported
   * @param sampleCount number of P_Grid samples used
   * @return the persisted entity
   */
  @Transactional
  public HourlyEnergyEntity upsert(
      LocalDateTime hourStart, LocalDateTime hourEnd,
      BigDecimal importKwh, BigDecimal exportKwh, int sampleCount) {
    HourlyEnergyEntity e = findByHourStart(hourStart).orElseGet(HourlyEnergyEntity::new);
    e.hourStart = hourStart;
    e.hourEnd = hourEnd;
    e.importKwh = importKwh;
    e.exportKwh = exportKwh;
    e.sampleCount = sampleCount;
    persist(e);
    return e;
  }

  /**
   * Deletes energy records older than the given cutoff.
   *
   * @param before cutoff time (rows with hourEnd before this are deleted)
   * @return number of deleted rows
   */
  @Transactional
  public int deleteOlderThan(LocalDateTime before) {
    return (int) delete("hourEnd < ?1", before);
  }
}
