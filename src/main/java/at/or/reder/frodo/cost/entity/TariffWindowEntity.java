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

package at.or.reder.frodo.cost.entity;

import at.or.reder.frodo.cost.spi.PriceDirection;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fixed-price time slot that overrides provider spot prices for a given direction.
 *
 * <p>Example: peak import tariff 32.5 ct/kWh on weekdays 07:00–22:00.</p>
 *
 * <h3>Matching rules</h3>
 * <ul>
 *   <li>{@code validFrom} ≤ hour date AND ({@code validTo} is null OR {@code validTo} > hour date)</li>
 *   <li>{@code daysOfWeek} is null (= all days) OR contains the weekday of the hour</li>
 *   <li>{@code timeFrom} ≤ hour time AND {@code timeTo} > hour time;
 *       {@code timeTo = 00:00} is treated as end-of-day (24:00)</li>
 * </ul>
 *
 * <p>When multiple windows match, the one with the highest {@code priority} wins.</p>
 *
 * <p>Midnight-crossing tariffs (e.g. 22:00–06:00) must be split into two rows.</p>
 */
@Entity
@Table(name = "FroTariffWindow")
public class TariffWindowEntity extends PanacheEntity {

  /** Price direction this window applies to. */
  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  public PriceDirection direction;

  /** Tariff effective from date (inclusive). */
  @Column(name = "valid_from", nullable = false)
  public LocalDate validFrom;

  /** Tariff end date (exclusive); null means still active. */
  @Column(name = "valid_to")
  public LocalDate validTo;

  /**
   * Comma-separated weekday names: {@code MON,TUE,WED,THU,FRI,SAT,SUN}.
   * Null means all days.
   */
  @Column(name = "days_of_week", length = 35)
  public String daysOfWeek;

  /** Window start time within the day (e.g. 07:00:00). */
  @Column(name = "time_from", nullable = false)
  public LocalTime timeFrom;

  /**
   * Window end time within the day.
   * {@code 00:00:00} is interpreted as end-of-day (24:00).
   */
  @Column(name = "time_to", nullable = false)
  public LocalTime timeTo;

  /** Fixed price in ct/kWh for this window. */
  @Column(name = "price_ct", nullable = false, precision = 15, scale = 5)
  public BigDecimal priceCt;

  /** Higher priority wins when multiple windows match the same hour. */
  @Column(name = "priority", nullable = false)
  public int priority = 0;

  /** Optional description (e.g. "Peak import tariff"). */
  @Column(name = "description", length = 255)
  public String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
