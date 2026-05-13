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

package at.or.reder.frodo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;


/**
 * Timezone-agnostic time utilities for Frodo.
 *
 * <p>All methods use explicit zones — never {@link java.time.ZoneId#systemDefault()}.
 * This ensures consistent behaviour regardless of the JVM/OS timezone setting.</p>
 *
 * <p>Internal storage convention: all {@link LocalDateTime} values persisted to the
 * database represent UTC wall-clock time.</p>
 */
public final class TimeUtil {

  /**
   * UTC zone offset constant — prefer this over {@code ZoneOffset.UTC} in call sites
   * so that the intent (UTC) is visible and consistent.
   */
  public static final ZoneOffset UTC = ZoneOffset.UTC;

  private TimeUtil() {
  }

  // ---- Current time -------------------------------------------------------

  /**
   * Returns the current date-time in UTC.
   *
   * <p>Drop-in replacement for {@code LocalDateTime.now()} that is always UTC,
   * regardless of the JVM timezone.</p>
   */
  public static LocalDateTime nowUtc() {
    return LocalDateTime.now(UTC);
  }

  /**
   * Returns today's date in UTC.
   *
   * <p>Drop-in replacement for {@code LocalDate.now()} that is always UTC.</p>
   */
  public static LocalDate todayUtc() {
    return LocalDate.now(UTC);
  }

  // ---- Instant conversions ------------------------------------------------

  /**
   * Converts an {@link Instant} to a UTC {@link LocalDateTime}.
   *
   * <p>Equivalent to {@code LocalDateTime.ofInstant(instant, ZoneOffset.UTC)}.</p>
   *
   * @param instant the instant to convert; must not be null
   * @return UTC date-time corresponding to the given instant
   */
  public static LocalDateTime toUtcLdt(Instant instant) {
    return LocalDateTime.ofInstant(instant, UTC);
  }

  // ---- Epoch millisecond conversions ---------------------------------------

  /**
   * Converts a UTC {@link LocalDateTime} to epoch milliseconds.
   *
   * @param utcLdt UTC date-time
   * @return epoch milliseconds (standard Unix time)
   */
  public static long toEpochMs(LocalDateTime utcLdt) {
    return utcLdt.toInstant(UTC).toEpochMilli();
  }

  /**
   * Converts epoch milliseconds to a UTC {@link LocalDateTime}.
   *
   * @param epochMs epoch milliseconds (standard Unix time)
   * @return UTC date-time
   */
  public static LocalDateTime fromEpochMs(long epochMs) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), UTC);
  }
}
