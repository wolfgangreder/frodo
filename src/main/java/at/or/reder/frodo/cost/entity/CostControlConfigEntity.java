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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * DB-backed runtime configuration for the cost control feature (single row, id=1).
 *
 * <p>Stores provider selection, cron schedules, energy sampling parameters, and retention
 * settings. Changes take effect on the next scheduled run — no application restart needed.</p>
 *
 * <p>This entity does <em>not</em> extend {@code PanacheEntity} because the ID is always 1
 * (fixed, never sequence-generated) and no list operations are needed.</p>
 */
@Entity
@Table(name = "FroCostControlConfig")
public class CostControlConfigEntity {

  /** Always 1 — single-row config table. */
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  public long id = 1L;

  /** SPI provider ID for import prices (e.g. {@code "MANUAL"}, {@code "TIBBER"}). */
  @Column(name = "import_provider_id", nullable = false, length = 50)
  public String importProviderId;

  /** SPI provider ID for export prices (e.g. {@code "AWATTAR"}, {@code "MANUAL"}). */
  @Column(name = "export_provider_id", nullable = false, length = 50)
  public String exportProviderId;

  /** Quartz cron expression for scheduled import price fetch. */
  @Column(name = "import_fetch_cron", nullable = false, length = 100)
  public String importFetchCron;

  /** Quartz cron expression for scheduled export price fetch. */
  @Column(name = "export_fetch_cron", nullable = false, length = 100)
  public String exportFetchCron;

  /** Interval in seconds between P_Grid samples (default: 15). */
  @Column(name = "sample_interval_seconds", nullable = false)
  public int sampleIntervalSeconds;

  /**
   * Dead-band threshold in watts.
   * P_Grid values within ±dead_band_watts of zero are treated as zero
   * to avoid integrating noise.
   */
  @Column(name = "dead_band_watts", nullable = false)
  public double deadBandWatts;

  /** Number of days to retain hourly energy and cost records. */
  @Column(name = "retention_hourly_days", nullable = false)
  public int retentionHourlyDays;

  /** Number of years to retain monthly cost summaries. */
  @Column(name = "retention_monthly_years", nullable = false)
  public int retentionMonthlyYears;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
