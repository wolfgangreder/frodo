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

package at.or.reder.frodo.modbus.entity;

import java.time.temporal.ChronoUnit;

/**
 * Aggregation mode for metrics data persistence.
 *
 * <p>Controls how scraped values are reduced before being written to the database.
 * The Prometheus gauge is always updated on every scrape, regardless of mode.
 * Choosing a coarser mode for slow-changing or low-priority metrics can cut
 * disk usage by 60–1,440× compared to the default 1-minute average.</p>
 *
 * <p>Disk usage example (30 s scrape, 1 year retention, 1 parameter):</p>
 * <ul>
 *   <li>MINUTE_AVERAGE: ~525,600 rows/year (baseline)</li>
 *   <li>HOUR_AVERAGE:   ~8,760 rows/year (60× reduction)</li>
 *   <li>DAY_AVERAGE:    ~365 rows/year (1,440× reduction)</li>
 * </ul>
 *
 * <p>See {@code docs/METRICS_AGGREGATION.md} for full documentation.</p>
 */
public enum AggregationMode {

  /** Average all samples within each calendar minute (existing default behaviour). */
  MINUTE_AVERAGE,

  /** Store the first sample that falls within each calendar minute. */
  MINUTE_CURRENT,

  /**
   * Store the difference between the last value of this minute and the last
   * value of the previous minute. The first recorded diff after a service
   * restart is skipped (no previous value).
   */
  MINUTE_DIFF,

  /** Average all samples within each calendar hour. */
  HOUR_AVERAGE,

  /** Store the first sample that falls within each calendar hour. */
  HOUR_CURRENT,

  /**
   * Store the difference between the last value of this hour and the last
   * value of the previous hour. The first recorded diff after a service
   * restart is skipped (no previous value).
   */
  HOUR_DIFF,

  /** Average all samples within each calendar day (00:00–23:59 UTC). */
  DAY_AVERAGE,

  /** Store the first sample that falls within each calendar day (at or after 00:00 UTC). */
  DAY_CURRENT,

  /**
   * Store the difference between the last value of this day and the last
   * value of the previous day. The first recorded diff after a service
   * restart is skipped (no previous value).
   */
  DAY_DIFF;

  // ========== Helper methods ==========

  /**
   * Returns the {@link ChronoUnit} used to truncate an {@code Instant} to this
   * mode's window boundary.
   */
  public ChronoUnit chronoUnit() {
    return switch (this) {
      case MINUTE_AVERAGE, MINUTE_CURRENT, MINUTE_DIFF -> ChronoUnit.MINUTES;
      case HOUR_AVERAGE, HOUR_CURRENT, HOUR_DIFF -> ChronoUnit.HOURS;
      case DAY_AVERAGE, DAY_CURRENT, DAY_DIFF -> ChronoUnit.DAYS;
    };
  }

  /**
   * Returns the window duration in seconds (60, 3600, or 86400).
   */
  public long windowSeconds() {
    return switch (this) {
      case MINUTE_AVERAGE, MINUTE_CURRENT, MINUTE_DIFF -> 60L;
      case HOUR_AVERAGE, HOUR_CURRENT, HOUR_DIFF -> 3600L;
      case DAY_AVERAGE, DAY_CURRENT, DAY_DIFF -> 86400L;
    };
  }

  /** Returns {@code true} if this mode computes arithmetic averages. */
  public boolean isAverage() {
    return switch (this) {
      case MINUTE_AVERAGE, HOUR_AVERAGE, DAY_AVERAGE -> true;
      case MINUTE_CURRENT, HOUR_CURRENT, DAY_CURRENT,
           MINUTE_DIFF, HOUR_DIFF, DAY_DIFF -> false;
    };
  }

  /** Returns {@code true} if this mode keeps the first value in the window. */
  public boolean isCurrent() {
    return switch (this) {
      case MINUTE_CURRENT, HOUR_CURRENT, DAY_CURRENT -> true;
      case MINUTE_AVERAGE, HOUR_AVERAGE, DAY_AVERAGE,
           MINUTE_DIFF, HOUR_DIFF, DAY_DIFF -> false;
    };
  }

  /** Returns {@code true} if this mode computes differences between windows. */
  public boolean isDiff() {
    return switch (this) {
      case MINUTE_DIFF, HOUR_DIFF, DAY_DIFF -> true;
      case MINUTE_AVERAGE, HOUR_AVERAGE, DAY_AVERAGE,
           MINUTE_CURRENT, HOUR_CURRENT, DAY_CURRENT -> false;
    };
  }

  /**
   * Returns an estimated number of rows per year for a single parameter,
   * assuming a 30-second scrape interval.
   */
  public long estimatedRowsPerYear() {
    return switch (this) {
      case MINUTE_AVERAGE, MINUTE_CURRENT, MINUTE_DIFF -> 525_600L;
      case HOUR_AVERAGE, HOUR_CURRENT, HOUR_DIFF -> 8_760L;
      case DAY_AVERAGE, DAY_CURRENT, DAY_DIFF -> 365L;
    };
  }

  /** Returns a human-readable description. */
  public String description() {
    return switch (this) {
      case MINUTE_AVERAGE -> "1 minute average (default)";
      case MINUTE_CURRENT -> "1 minute current value";
      case MINUTE_DIFF -> "1 minute difference to previous minute";
      case HOUR_AVERAGE -> "1 hour average";
      case HOUR_CURRENT -> "1 hour current value (first sample at :00)";
      case HOUR_DIFF -> "1 hour difference to previous hour";
      case DAY_AVERAGE -> "1 day average";
      case DAY_CURRENT -> "1 day current value (first sample at 00:00 UTC)";
      case DAY_DIFF -> "1 day difference to previous day";
    };
  }
}
