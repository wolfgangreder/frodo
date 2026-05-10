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

package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.MarketPriceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link MarketPriceEntity} persistence operations.
 *
 * <p>Stores hourly market prices from aWATTar AT for use by price-controlled
 * export blocking.</p>
 */
@ApplicationScoped
public class MarketPriceRepository implements PanacheRepository<MarketPriceEntity> {

    /**
     * Finds the market price for a specific hour.
     *
     * @param startTime the hour start time
     * @return Optional containing the price, or empty if not found
     */
    public Optional<MarketPriceEntity> findByStartTime(LocalDateTime startTime) {
        return find("startTime", startTime).firstResultOptional();
    }

/**
   * Finds the market price that applies to the given time.
   *
   * @param time the time to look up
   * @return Optional containing the price, or empty if not found
   */
  public Optional<MarketPriceEntity> findForTime(LocalDateTime time) {
    return find("startTime <= ?1 and endTime > ?1", time)
      .firstResultOptional();
  }

  /**
   * Finds the current market price (for the current hour or next hour).
   *
   * @return the current price entry, or empty if not available
   */
  public Optional<MarketPriceEntity> findCurrent() {
    LocalDateTime now = LocalDateTime.now();
    return find("startTime <= ?1 and endTime > ?1", now)
      .firstResultOptional();
  }

    /**
     * Lists all stored market prices.
     *
     * @return list of all price entities
     */
    public List<MarketPriceEntity> listAll() {
        return list("startTime desc");
    }

/**
   * Lists the most recent prices (for display).
   *
   * @param limit max number of entries to return
   * @return list of recent price entries
   */
  public List<MarketPriceEntity> listRecent(int limit) {
    return find("startTime desc")
      .page(io.quarkus.panache.common.Page.of(0, limit))
      .list();
  }

    /**
     * Saves or updates a market price.
     *
     * <p>If a price already exists for the hour, it is updated.
     * Otherwise a new entity is created.</p>
     *
     * @param startTime hour start time
     * @param endTime   hour end time
     * @param priceCt   price in ct/kWh
     * @return the persisted entity
     */
    @Transactional
    public MarketPriceEntity upsert(LocalDateTime startTime, LocalDateTime endTime, java.math.BigDecimal priceCt) {
        MarketPriceEntity entity = findByStartTime(startTime)
            .orElseGet(MarketPriceEntity::new);

        entity.startTime = startTime;
        entity.endTime = endTime;
        entity.priceCt = priceCt;
        persist(entity);
        return entity;
    }

/**
   * Deletes expired price entries older than the specified time.
   *
   * @param before the cutoff time
   * @return number of entries deleted
   */
  @Transactional
  public int deleteExpired(LocalDateTime before) {
    return (int) delete("endTime < ?1", before);
  }
}