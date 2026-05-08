package at.or.reder.frodo.modbus.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AggregationMode}.
 *
 * <p>Covers description strings (used by {@code GET /api/metrics-docs/aggregation-modes})
 * and estimated rows/year (used to guide user decisions in the UI).</p>
 */
class AggregationModeTest {

  // ========== description() ==========

  @ParameterizedTest
  @EnumSource(AggregationMode.class)
  void description_allModes_nonBlank(AggregationMode mode) {
    String desc = mode.description();
    assertNotNull(desc, "description() must not return null for " + mode);
    assertFalse(desc.isBlank(), "description() must not be blank for " + mode);
  }

  @Test
  void description_minuteAverage_mentioneDefault() {
    // The default mode must signal "default" so users understand it is pre-existing behaviour
    String desc = AggregationMode.MINUTE_AVERAGE.description();
    assertTrue(desc.contains("default") || desc.contains("Default"),
      "MINUTE_AVERAGE description should mention 'default', got: " + desc);
  }

  @Test
  void description_valuesAreDistinct() {
    long distinct = java.util.Arrays.stream(AggregationMode.values())
      .map(AggregationMode::description)
      .distinct()
      .count();
    assertEquals(AggregationMode.values().length, distinct,
      "Each AggregationMode must have a unique description");
  }

  // ========== estimatedRowsPerYear() ==========

  @ParameterizedTest
  @EnumSource(AggregationMode.class)
  void estimatedRowsPerYear_allModes_positive(AggregationMode mode) {
    assertTrue(mode.estimatedRowsPerYear() > 0,
      "estimatedRowsPerYear() must be positive for " + mode);
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"MINUTE_AVERAGE", "MINUTE_CURRENT", "MINUTE_DIFF"})
  void estimatedRowsPerYear_minuteModes_525600(AggregationMode mode) {
    assertEquals(525_600L, mode.estimatedRowsPerYear(),
      "Minute modes should estimate 525600 rows/year for " + mode);
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"HOUR_AVERAGE", "HOUR_CURRENT", "HOUR_DIFF"})
  void estimatedRowsPerYear_hourModes_8760(AggregationMode mode) {
    assertEquals(8_760L, mode.estimatedRowsPerYear(),
      "Hour modes should estimate 8760 rows/year for " + mode);
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"DAY_AVERAGE", "DAY_CURRENT", "DAY_DIFF"})
  void estimatedRowsPerYear_dayModes_365(AggregationMode mode) {
    assertEquals(365L, mode.estimatedRowsPerYear(),
      "Day modes should estimate 365 rows/year for " + mode);
  }

  @Test
  void estimatedRowsPerYear_minuteIsMoreThanHour() {
    assertTrue(AggregationMode.MINUTE_AVERAGE.estimatedRowsPerYear()
      > AggregationMode.HOUR_AVERAGE.estimatedRowsPerYear());
  }

  @Test
  void estimatedRowsPerYear_hourIsMoreThanDay() {
    assertTrue(AggregationMode.HOUR_AVERAGE.estimatedRowsPerYear()
      > AggregationMode.DAY_AVERAGE.estimatedRowsPerYear());
  }

  // ========== windowSeconds() ==========

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"MINUTE_AVERAGE", "MINUTE_CURRENT", "MINUTE_DIFF"})
  void windowSeconds_minuteModes_60(AggregationMode mode) {
    assertEquals(60L, mode.windowSeconds());
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"HOUR_AVERAGE", "HOUR_CURRENT", "HOUR_DIFF"})
  void windowSeconds_hourModes_3600(AggregationMode mode) {
    assertEquals(3600L, mode.windowSeconds());
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"DAY_AVERAGE", "DAY_CURRENT", "DAY_DIFF"})
  void windowSeconds_dayModes_86400(AggregationMode mode) {
    assertEquals(86400L, mode.windowSeconds());
  }

  // ========== mode family predicates ==========

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"MINUTE_AVERAGE", "HOUR_AVERAGE", "DAY_AVERAGE"})
  void isAverage_averageModes_true(AggregationMode mode) {
    assertTrue(mode.isAverage());
    assertFalse(mode.isCurrent());
    assertFalse(mode.isDiff());
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"MINUTE_CURRENT", "HOUR_CURRENT", "DAY_CURRENT"})
  void isCurrent_currentModes_true(AggregationMode mode) {
    assertFalse(mode.isAverage());
    assertTrue(mode.isCurrent());
    assertFalse(mode.isDiff());
  }

  @ParameterizedTest
  @EnumSource(value = AggregationMode.class, names = {"MINUTE_DIFF", "HOUR_DIFF", "DAY_DIFF"})
  void isDiff_diffModes_true(AggregationMode mode) {
    assertFalse(mode.isAverage());
    assertFalse(mode.isCurrent());
    assertTrue(mode.isDiff());
  }
}
