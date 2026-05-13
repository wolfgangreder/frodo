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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link TimeUtil}.
 *
 * <p>Covers UTC helpers and epoch millisecond conversions.</p>
 */
class TimeUtilTest {

  // ---- nowUtc / todayUtc --------------------------------------------------

  @Test
  void testNowUtc_isNotNull() {
    assertNotNull(TimeUtil.nowUtc());
  }

  @Test
  void testNowUtc_isBetweenBeforeAndAfter() {
    LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
    LocalDateTime result = TimeUtil.nowUtc();
    LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
    assertFalse(result.isBefore(before), "nowUtc() should not be before the before-snapshot");
    assertFalse(result.isAfter(after), "nowUtc() should not be after the after-snapshot");
  }

  @Test
  void testTodayUtc_matchesUtcDate() {
    LocalDate result = TimeUtil.todayUtc();
    assertEquals(LocalDate.now(ZoneOffset.UTC), result);
  }

  // ---- toUtcLdt -----------------------------------------------------------

  @Test
  void testToUtcLdt_epochZero() {
    Instant epoch = Instant.ofEpochSecond(0); // 1970-01-01T00:00:00Z
    LocalDateTime result = TimeUtil.toUtcLdt(epoch);
    assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0, 0), result);
  }

  @Test
  void testToUtcLdt_knownInstant() {
    // 2026-05-10T12:30:00Z
    Instant instant = Instant.parse("2026-05-10T12:30:00Z");
    LocalDateTime result = TimeUtil.toUtcLdt(instant);
    assertEquals(LocalDateTime.of(2026, 5, 10, 12, 30, 0), result);
  }

  // ---- toEpochMs / fromEpochMs: standard UTC epoch ------------------------

  @Test
  void testToEpochMs_epochZero() {
    LocalDateTime ldt = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    assertEquals(0L, TimeUtil.toEpochMs(ldt));
  }

  @Test
  void testToEpochMs_knownValue() {
    // 2026-05-11T12:00:00Z = 1778500800000 ms
    LocalDateTime ldt = LocalDateTime.of(2026, 5, 11, 12, 0, 0);
    assertEquals(1778500800000L, TimeUtil.toEpochMs(ldt));
  }

  @Test
  void testFromEpochMs_epochZero() {
    LocalDateTime result = TimeUtil.fromEpochMs(0L);
    assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0, 0), result);
  }

  @Test
  void testFromEpochMs_knownValue() {
    // 1778500800000 ms = 2026-05-11T12:00:00Z
    LocalDateTime result = TimeUtil.fromEpochMs(1778500800000L);
    assertEquals(LocalDateTime.of(2026, 5, 11, 12, 0, 0), result);
  }

  @Test
  void testRoundTrip_toEpochMs_fromEpochMs() {
    LocalDateTime input = LocalDateTime.of(2026, 7, 15, 14, 30, 45);
    long epochMs = TimeUtil.toEpochMs(input);
    LocalDateTime result = TimeUtil.fromEpochMs(epochMs);
    assertEquals(input, result, "round-trip should be lossless");
  }

  @Test
  void testRoundTrip_fromEpochMs_toEpochMs() {
    long input = 1778504400000L; // 2026-05-11T13:00:00Z
    LocalDateTime ldt = TimeUtil.fromEpochMs(input);
    long result = TimeUtil.toEpochMs(ldt);
    assertEquals(input, result, "round-trip should be lossless");
  }
}
