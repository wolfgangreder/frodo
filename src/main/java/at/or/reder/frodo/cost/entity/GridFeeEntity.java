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

import at.or.reder.frodo.cost.spi.FeeAppliesTo;
import at.or.reder.frodo.cost.spi.FeeType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Grid surcharge rule.
 *
 * <p>Multiple fees can be active simultaneously; all are summed during cost calculation.
 * A fee becomes active when {@code validFrom} is reached and remains active indefinitely
 * (no {@code validTo} — delete to deactivate).</p>
 */
@Entity
@Table(name = "FroGridFee")
public class GridFeeEntity extends PanacheEntity {

  /** Timestamp when this fee became / becomes active. */
  @Column(name = "valid_from", nullable = false)
  public LocalDateTime validFrom;

  /** Calculation type: percent of cost, per-kWh absolute, or per-month absolute. */
  @Enumerated(EnumType.STRING)
  @Column(name = "fee_type", nullable = false, length = 20)
  public FeeType feeType;

  /**
   * Numeric fee value.
   * <ul>
   *   <li>{@link FeeType#PERCENT}: percentage of the base cost (e.g. 5.0 = 5%)</li>
   *   <li>{@link FeeType#ABSOLUTE_ENERGY}: ct/kWh</li>
   *   <li>{@link FeeType#ABSOLUTE_TIME}: EUR/month</li>
   * </ul>
   */
  @Column(name = "fee_value", nullable = false, precision = 15, scale = 5)
  public BigDecimal feeValue;

  /** Which energy direction this fee applies to. */
  @Enumerated(EnumType.STRING)
  @Column(name = "applies_to", nullable = false, length = 20)
  public FeeAppliesTo appliesTo;

  /** Optional description (e.g. "5% network export levy"). */
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
