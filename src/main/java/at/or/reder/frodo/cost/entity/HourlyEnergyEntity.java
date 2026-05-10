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

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Hourly grid import/export energy in kWh, computed by trapezoidal integration of P_Grid samples.
 *
 * <p>One row per completed calendar hour. The {@code sampleCount} field records how many
 * P_Grid data points were used; consumers can filter or flag low-quality hours.</p>
 */
@Entity
@Table(
  name = "FroHourlyEnergy",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroHourlyEnergy_hour",
    columnNames = {"hour_start"}
  )
)
public class HourlyEnergyEntity extends PanacheEntity {

  /** Start of the calendar hour (UTC). */
  @Column(name = "hour_start", nullable = false)
  public LocalDateTime hourStart;

  /** End of the calendar hour (UTC). */
  @Column(name = "hour_end", nullable = false)
  public LocalDateTime hourEnd;

  /** kWh imported from the grid during this hour (≥ 0). */
  @Column(name = "import_kwh", nullable = false, precision = 15, scale = 6)
  public BigDecimal importKwh;

  /** kWh exported to the grid during this hour (≥ 0). */
  @Column(name = "export_kwh", nullable = false, precision = 15, scale = 6)
  public BigDecimal exportKwh;

  /** Number of P_Grid samples used in the trapezoidal integration. */
  @Column(name = "sample_count", nullable = false)
  public int sampleCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
