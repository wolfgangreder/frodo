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
 * Hourly raw energy price from a price provider, stored per direction.
 *
 * <p>Both price columns are nullable because import and export are managed by
 * independent providers and may arrive at different times.</p>
 */
@Entity
@Table(
  name = "FroEnergyPrice",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroEnergyPrice_time",
    columnNames = {"start_time"}
  )
)
public class EnergyPriceEntity extends PanacheEntity {

  /** Start of the hour this price applies to (UTC). */
  @Column(name = "start_time", nullable = false)
  public LocalDateTime startTime;

  /** End of the hour this price applies to (UTC). */
  @Column(name = "end_time", nullable = false)
  public LocalDateTime endTime;

  /**
   * Import price in ct/kWh.
   * Null if no import provider is active or data not yet fetched.
   */
  @Column(name = "price_import_ct", precision = 15, scale = 5)
  public BigDecimal priceImportCt;

  /**
   * Export price in ct/kWh.
   * Null if no export provider is active or data not yet fetched.
   */
  @Column(name = "price_export_ct", precision = 15, scale = 5)
  public BigDecimal priceExportCt;

  /** Provider ID that delivered the import price (e.g. {@code AWATTAR}, {@code MANUAL}). */
  @Column(name = "import_source", length = 50)
  public String importSource;

  /** Provider ID that delivered the export price. */
  @Column(name = "export_source", length = 50)
  public String exportSource;

  /** Timestamp when this record was first created. */
  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  /** Timestamp when this record was last updated (e.g. when second direction arrived). */
  @Column(name = "updated_at")
  public Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    updatedAt = Instant.now();
  }
}
