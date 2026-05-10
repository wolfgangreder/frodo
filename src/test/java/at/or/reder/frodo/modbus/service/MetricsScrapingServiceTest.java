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

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.entity.AggregationMode;
import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import at.or.reder.frodo.modbus.entity.MetricsDataEntity;
import at.or.reder.frodo.modbus.entity.MetricsParameterEntity;
import at.or.reder.frodo.modbus.service.MetricsScrapingService.AggregatingAccumulator;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsScrapingService.AggregatingAccumulator}.
 *
 * <p>Covers all 9 aggregation modes: per-minute, per-hour, and per-day
 * variants of AVERAGE, CURRENT, and DIFF. Tests are pure-Java (no CDI or
 * Quarkus bootstrap required).</p>
 */
class MetricsScrapingServiceTest {

  /** A minute-aligned instant used as bucket start in tests. */
  private static final Instant BUCKET_MIN = Instant.parse("2024-01-15T10:05:00Z");

  /** An hour-aligned instant used as bucket start in tests. */
  private static final Instant BUCKET_HOUR = Instant.parse("2024-01-15T10:00:00Z");

  /** A day-aligned instant used as bucket start in tests. */
  private static final Instant BUCKET_DAY = Instant.parse("2024-01-15T00:00:00Z");

  // ========== Helpers ==========

  private static MetricsParameterEntity makeParam(String fieldName, AggregationMode mode) {
    MetricsParameterEntity p = new MetricsParameterEntity();
    p.fieldName = fieldName;
    p.aggregationMode = mode;
    return p;
  }

  private static MetricsConfigEntity makeConfig() {
    // device may be null — buildDataEntity only assigns the reference, no FK resolution
    return new MetricsConfigEntity();
  }

  private static Instant bucketFor(AggregationMode mode) {
    return switch (mode.chronoUnit()) {
      case MINUTES -> BUCKET_MIN;
      case HOURS -> BUCKET_HOUR;
      case DAYS -> BUCKET_DAY;
      default -> throw new IllegalArgumentException("Unexpected ChronoUnit: " + mode.chronoUnit());
    };
  }

  // ========== hasData ==========

  @Test
  void hasData_empty_returnsFalse() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    assertFalse(acc.hasData());
  }

  @ParameterizedTest
  @EnumSource(AggregationMode.class)
  void hasData_afterNumericAdd_returnsTrue(AggregationMode mode) {
    AggregatingAccumulator acc = new AggregatingAccumulator(mode, bucketFor(mode));
    acc.add(42.0);
    assertTrue(acc.hasData());
  }

  @Test
  void hasData_afterStringAdd_returnsTrue() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add("active");
    assertTrue(acc.hasData());
  }

  @Test
  void hasData_nullValue_remainsFalse() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(null);
    assertFalse(acc.hasData());
  }

  // ========== sampleCount ==========

  @Test
  void sampleCount_zero_afterConstruction() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    assertEquals(0, acc.sampleCount());
  }

  @Test
  void sampleCount_average_countsAllNumerics() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(1.0);
    acc.add(2.0);
    acc.add("text"); // string not counted
    assertEquals(2, acc.sampleCount());
  }

  @Test
  void sampleCount_current_maxOne_subsequentDiscarded() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_CURRENT, BUCKET_MIN);
    acc.add(10.0);
    acc.add(20.0);
    acc.add(30.0);
    assertEquals(1, acc.sampleCount());
  }

  @Test
  void sampleCount_diff_countsAllNumerics() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_DIFF, BUCKET_MIN);
    acc.add(10.0);
    acc.add(20.0);
    assertEquals(2, acc.sampleCount());
  }

  // ========== AVERAGE modes ==========

  @Test
  void average_minuteAverage_singleSample_returnsThatValue() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(100.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_AVERAGE), 111, null);
    assertNotNull(dp);
    assertEquals(100.0, dp.valueNumeric, 1e-9);
  }

  @Test
  void average_minuteAverage_multipleValues_computesMean() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(10.0);
    acc.add(20.0);
    acc.add(30.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_AVERAGE), 111, null);
    assertNotNull(dp);
    assertEquals(20.0, dp.valueNumeric, 1e-9);
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"HOUR_AVERAGE", "DAY_AVERAGE"})
  void average_hourAndDay_computesMean(AggregationMode mode) {
    AggregatingAccumulator acc = new AggregatingAccumulator(mode, bucketFor(mode));
    acc.add(40.0);
    acc.add(60.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", mode), 103, null);
    assertNotNull(dp);
    assertEquals(50.0, dp.valueNumeric, 1e-9);
  }

  @Test
  void average_integerValues_treatedAsDouble() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(1);
    acc.add(2);
    acc.add(3);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_AVERAGE), 101, null);
    assertNotNull(dp);
    assertEquals(2.0, dp.valueNumeric, 1e-9);
  }

  @Test
  void average_noData_buildDataEntityReturnsNull() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    assertNull(acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_AVERAGE), 111, null));
  }

  // ========== CURRENT modes ==========

  @Test
  void current_minuteCurrent_keepsFirstValue() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_CURRENT, BUCKET_MIN);
    acc.add(10.0);
    acc.add(20.0);
    acc.add(30.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_CURRENT), 111, null);
    assertNotNull(dp);
    assertEquals(10.0, dp.valueNumeric, 1e-9);
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"HOUR_CURRENT", "DAY_CURRENT"})
  void current_hourAndDay_keepsFirstValue(AggregationMode mode) {
    AggregatingAccumulator acc = new AggregatingAccumulator(mode, bucketFor(mode));
    acc.add(5.0);
    acc.add(99.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", mode), 103, null);
    assertNotNull(dp);
    assertEquals(5.0, dp.valueNumeric, 1e-9);
  }

  @Test
  void current_noData_buildDataEntityReturnsNull() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_CURRENT, BUCKET_MIN);
    assertNull(acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_CURRENT), 111, null));
  }

  @Test
  void current_stringOnly_buildDataEntity_hasValueString() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_CURRENT, BUCKET_MIN);
    acc.add("on");
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("St", AggregationMode.MINUTE_CURRENT), 122, null);
    assertNotNull(dp);
    assertNull(dp.valueNumeric);
    assertEquals("on", dp.valueString);
  }

  @Test
  void sampleCount_current_noData_returnsZero() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_CURRENT, BUCKET_MIN);
    assertEquals(0, acc.sampleCount());
  }

  // ========== DIFF modes ==========

  @Test
  void diff_minuteDiff_computesLastMinusPrevious() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_DIFF, BUCKET_MIN);
    acc.add(10.0);
    acc.add(15.0);
    acc.add(20.0); // last value in window
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("Wh", AggregationMode.MINUTE_DIFF), 103, 10.0);
    assertNotNull(dp);
    assertEquals(10.0, dp.valueNumeric, 1e-9); // 20 - 10 = 10
  }

  @Test
  void diff_noPreviousValue_returnsNull() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_DIFF, BUCKET_MIN);
    acc.add(50.0);
    // previousValue == null → first sample after restart, skip
    assertNull(acc.buildDataEntity(makeConfig(), makeParam("Wh", AggregationMode.MINUTE_DIFF), 103, null));
  }

  @Test
  void diff_negativeDiff_allowed() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.HOUR_DIFF, BUCKET_HOUR);
    acc.add(100.0);
    acc.add(80.0); // last = 80
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("Wh", AggregationMode.HOUR_DIFF), 103, 90.0);
    assertNotNull(dp);
    assertEquals(-10.0, dp.valueNumeric, 1e-9); // 80 - 90 = -10
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"HOUR_DIFF", "DAY_DIFF"})
  void diff_hourAndDay_withPreviousValue(AggregationMode mode) {
    AggregatingAccumulator acc = new AggregatingAccumulator(mode, bucketFor(mode));
    acc.add(200.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("Wh", mode), 103, 150.0);
    assertNotNull(dp);
    assertEquals(50.0, dp.valueNumeric, 1e-9); // 200 - 150 = 50
  }

  @Test
  void diff_noData_returnsNull() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_DIFF, BUCKET_MIN);
    assertNull(acc.buildDataEntity(makeConfig(), makeParam("Wh", AggregationMode.MINUTE_DIFF), 103, 100.0));
  }

  // ========== getLastNumeric ==========

  @Test
  void getLastNumeric_average_returnsLastSample() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(10.0);
    acc.add(20.0);
    acc.add(30.0);
    assertEquals(30.0, acc.getLastNumeric(), 1e-9);
  }

  @Test
  void getLastNumeric_current_returnsFirst() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_CURRENT, BUCKET_MIN);
    acc.add(42.0);
    acc.add(99.0);
    assertEquals(42.0, acc.getLastNumeric(), 1e-9);
  }

  @Test
  void getLastNumeric_diff_returnsLastSample() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_DIFF, BUCKET_MIN);
    acc.add(100.0);
    acc.add(110.0);
    assertEquals(110.0, acc.getLastNumeric(), 1e-9);
  }

  @Test
  void getLastNumeric_empty_returnsNull() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_DIFF, BUCKET_MIN);
    assertNull(acc.getLastNumeric());
  }

  // ========== shouldFlush ==========

  @Test
  void shouldFlush_minute_oneSecondBefore_false() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    assertFalse(acc.shouldFlush(Instant.parse("2024-01-15T10:05:59Z")));
  }

  @Test
  void shouldFlush_minute_atWindowBoundary_true() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    assertTrue(acc.shouldFlush(Instant.parse("2024-01-15T10:06:00Z")));
  }

  @Test
  void shouldFlush_minute_afterWindowEnd_true() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    assertTrue(acc.shouldFlush(Instant.parse("2024-01-15T10:07:00Z")));
  }

  @Test
  void shouldFlush_hour_oneSecondBefore_false() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.HOUR_AVERAGE, BUCKET_HOUR);
    assertFalse(acc.shouldFlush(Instant.parse("2024-01-15T10:59:59Z")));
  }

  @Test
  void shouldFlush_hour_atBoundary_true() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.HOUR_AVERAGE, BUCKET_HOUR);
    assertTrue(acc.shouldFlush(Instant.parse("2024-01-15T11:00:00Z")));
  }

  @Test
  void shouldFlush_day_oneSecondBefore_false() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.DAY_AVERAGE, BUCKET_DAY);
    assertFalse(acc.shouldFlush(Instant.parse("2024-01-15T23:59:59Z")));
  }

  @Test
  void shouldFlush_day_atBoundary_true() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.DAY_AVERAGE, BUCKET_DAY);
    assertTrue(acc.shouldFlush(Instant.parse("2024-01-16T00:00:00Z")));
  }

  // ========== string handling ==========

  @Test
  void string_lastStringUpdatedOnEachCall() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add("first");
    acc.add("second");
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("St", AggregationMode.MINUTE_AVERAGE), 101, null);
    assertNotNull(dp);
    assertEquals("second", dp.valueString);
  }

  @Test
  void string_onlyString_buildDataEntity_hasValueString() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add("running");
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("St", AggregationMode.MINUTE_AVERAGE), 101, null);
    assertNotNull(dp);
    assertNull(dp.valueNumeric);
    assertEquals("running", dp.valueString);
  }

  @Test
  void string_numericDoesNotOverwriteLastString() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add("status");
    acc.add(99.0);
    // numeric present → valueNumeric set; string fallback not emitted
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("St", AggregationMode.MINUTE_AVERAGE), 101, null);
    assertNotNull(dp);
    assertEquals(99.0, dp.valueNumeric, 1e-9);
    assertNull(dp.valueString);
  }

  // ========== bucketStart ==========

  @Test
  void bucketStart_storedExact() {
    Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, bucket);
    assertEquals(bucket, acc.bucketStart);
  }

  // ========== recordedAt set to bucketStart ==========

  @Test
  void buildDataEntity_recordedAt_equalsBucketStart() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(42.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.MINUTE_AVERAGE), 101, null);
    assertNotNull(dp);
    assertEquals(BUCKET_MIN, dp.recordedAt);
  }

  @Test
  void buildDataEntity_fieldName_matchesParam() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(1.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("PhVphA", AggregationMode.MINUTE_AVERAGE), 201, null);
    assertNotNull(dp);
    assertEquals("PhVphA", dp.fieldName);
  }

  @Test
  void buildDataEntity_sunspecModelId_matchesArgument() {
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.HOUR_AVERAGE, BUCKET_HOUR);
    acc.add(5.0);
    MetricsDataEntity dp = acc.buildDataEntity(makeConfig(), makeParam("W", AggregationMode.HOUR_AVERAGE), 203, null);
    assertNotNull(dp);
    assertEquals(203, dp.sunspecModelId);
  }

  // ========== Solar API model ID sentinel ==========

  @Test
  void buildDataEntity_solarApiModelId_persistedAsNegativeOne() {
    // Solar API params use MODEL_ID_SOLAR_API (-1) as the modelId sentinel.
    // AggregatingAccumulator just stores whatever modelId is passed — verify
    // that negative values pass through correctly.
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.MINUTE_AVERAGE, BUCKET_MIN);
    acc.add(1234.5); // grid_power_watts
    MetricsDataEntity dp = acc.buildDataEntity(
      makeConfig(),
      makeParam("grid_power_watts", AggregationMode.MINUTE_AVERAGE),
      SunSpecConstants.MODEL_ID_SOLAR_API,
      null
    );
    assertNotNull(dp);
    assertEquals(SunSpecConstants.MODEL_ID_SOLAR_API, dp.sunspecModelId);
    assertEquals("grid_power_watts", dp.fieldName);
    assertEquals(1234.5, dp.valueNumeric, 0.001);
  }

  @Test
  void buildDataEntity_solarApiRatioField_storesRatioValue() {
    // Autonomy ratio stored as 0-1 (SolarApiMetricsService already divides by 100)
    AggregatingAccumulator acc = new AggregatingAccumulator(AggregationMode.HOUR_CURRENT, BUCKET_HOUR);
    acc.add(0.75);
    MetricsDataEntity dp = acc.buildDataEntity(
      makeConfig(),
      makeParam("autonomy_ratio", AggregationMode.HOUR_CURRENT),
      SunSpecConstants.MODEL_ID_SOLAR_API,
      null
    );
    assertNotNull(dp);
    assertEquals(SunSpecConstants.MODEL_ID_SOLAR_API, dp.sunspecModelId);
    assertEquals(0.75, dp.valueNumeric, 0.0001);
  }
}
