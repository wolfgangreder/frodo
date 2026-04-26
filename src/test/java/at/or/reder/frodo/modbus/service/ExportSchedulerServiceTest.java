package at.or.reder.frodo.modbus.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExportSchedulerService#isInBlockWindow},
 * {@link ExportSchedulerService#shouldBlockForPrice},
 * {@link ExportSchedulerService#computeEffectiveGridW},
 * {@link ExportSchedulerService#computeTargetWatts}, and
 * {@link ExportSchedulerService#computeTargetWattsFallback}.
 */
class ExportSchedulerServiceTest {

  private static final double DELTA = 0.001;

  // ========== Same-day window [11:00, 15:00) ==========

  @Test
  void sameDayWindow_insideWindow_returnsTrue() {
    assertTrue(isInBlockWindow("12:00", "11:00", "15:00"));
  }

  @Test
  void sameDayWindow_atBlockFrom_returnsTrue() {
    assertTrue(isInBlockWindow("11:00", "11:00", "15:00"));
  }

  @Test
  void sameDayWindow_justBeforeEnableFrom_returnsTrue() {
    assertTrue(isInBlockWindow("14:59", "11:00", "15:00"));
  }

  @Test
  void sameDayWindow_atEnableFrom_returnsFalse() {
    assertFalse(isInBlockWindow("15:00", "11:00", "15:00"));
  }

  @Test
  void sameDayWindow_beforeBlockFrom_returnsFalse() {
    assertFalse(isInBlockWindow("10:59", "11:00", "15:00"));
  }

  @Test
  void sameDayWindow_afterEnableFrom_returnsFalse() {
    assertFalse(isInBlockWindow("16:00", "11:00", "15:00"));
  }

  @Test
  void sameDayWindow_midnight_returnsFalse() {
    assertFalse(isInBlockWindow("00:00", "11:00", "15:00"));
  }

  // ========== Cross-midnight window [22:00, 06:00) ==========

  @Test
  void crossMidnightWindow_atBlockFrom_returnsTrue() {
    assertTrue(isInBlockWindow("22:00", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_lateEvening_returnsTrue() {
    assertTrue(isInBlockWindow("23:30", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_midnight_returnsTrue() {
    assertTrue(isInBlockWindow("00:00", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_earlyMorning_returnsTrue() {
    assertTrue(isInBlockWindow("05:00", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_justBeforeEnableFrom_returnsTrue() {
    assertTrue(isInBlockWindow("05:59", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_atEnableFrom_returnsFalse() {
    assertFalse(isInBlockWindow("06:00", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_duringDay_returnsFalse() {
    assertFalse(isInBlockWindow("12:00", "22:00", "06:00"));
  }

  @Test
  void crossMidnightWindow_justBeforeBlockFrom_returnsFalse() {
    assertFalse(isInBlockWindow("21:59", "22:00", "06:00"));
  }

  // ========== Degenerate window (blockFrom == enableFrom → always blocked) ==========

  @Test
  void degenerateWindow_alwaysBlocked_midnight() {
    assertTrue(isInBlockWindow("00:00", "12:00", "12:00"));
  }

  @Test
  void degenerateWindow_alwaysBlocked_atBoundary() {
    assertTrue(isInBlockWindow("12:00", "12:00", "12:00"));
  }

  @Test
  void degenerateWindow_alwaysBlocked_randomTime() {
    assertTrue(isInBlockWindow("17:45", "12:00", "12:00"));
  }

  // ========== shouldBlockForPrice — trigger is simply price < 0 ==========

  @Test
  void price_positive_notBlocked() {
    assertFalse(ExportSchedulerService.shouldBlockForPrice(100.0));
  }

  @Test
  void price_zero_notBlocked() {
    assertFalse(ExportSchedulerService.shouldBlockForPrice(0.0));
  }

  @Test
  void price_slightlyNegative_blocked() {
    assertTrue(ExportSchedulerService.shouldBlockForPrice(-0.01));
  }

  @Test
  void price_moderatelyNegative_blocked() {
    assertTrue(ExportSchedulerService.shouldBlockForPrice(-50.0));
  }

  @Test
  void price_veryNegative_blocked() {
    assertTrue(ExportSchedulerService.shouldBlockForPrice(-200.0));
  }

  @Test
  void price_smallPositive_notBlocked() {
    assertFalse(ExportSchedulerService.shouldBlockForPrice(0.01));
  }

  // ========== computeEffectiveGridW — dead-band suppression ==========

  @Test
  void effectiveGrid_smallPositiveImport_suppressedToZero() {
    // 30 W import < 50 W tolerance → suppressed
    assertEquals(0.0, ExportSchedulerService.computeEffectiveGridW(30.0, 50), DELTA);
  }

  @Test
  void effectiveGrid_atToleranceBoundary_notSuppressed() {
    // Exactly at tolerance threshold → NOT suppressed (condition is strict <)
    assertEquals(50.0, ExportSchedulerService.computeEffectiveGridW(50.0, 50), DELTA);
  }

  @Test
  void effectiveGrid_largeImport_usedAsIs() {
    assertEquals(200.0, ExportSchedulerService.computeEffectiveGridW(200.0, 50), DELTA);
  }

  @Test
  void effectiveGrid_negativeExport_usedAsIs() {
    // Grid export (negative) is never suppressed
    assertEquals(-300.0, ExportSchedulerService.computeEffectiveGridW(-300.0, 50), DELTA);
  }

  @Test
  void effectiveGrid_exactlyZero_notSuppressed() {
    // Zero grid is not in range (0 < gridW) so passes through as-is
    assertEquals(0.0, ExportSchedulerService.computeEffectiveGridW(0.0, 50), DELTA);
  }

  @Test
  void effectiveGrid_smallPositive_zeroToleranceNeverSuppressed() {
    // tolerance=0: condition 0 < gridW < 0 is never true; passes through
    assertEquals(10.0, ExportSchedulerService.computeEffectiveGridW(10.0, 0), DELTA);
  }

  // ========== computeTargetWatts — primary formula ==========

  @Test
  void targetWatts_householdLoad_noBattery_noTolerance() {
    // Load = -500 W (consuming), battery = 0, tolerance = 0 → target = 500 W
    assertEquals(500.0, ExportSchedulerService.computeTargetWatts(-500.0, 0.0, 0), DELTA);
  }

  @Test
  void targetWatts_householdLoad_batteryCharging_withTolerance() {
    // Load = -400 W, battery charging = -100 W, tolerance = 50 W → target = 400 + 100 + 50 = 550 W
    assertEquals(550.0, ExportSchedulerService.computeTargetWatts(-400.0, -100.0, 50), DELTA);
  }

  @Test
  void targetWatts_batteryDischarging_reducesTarget() {
    // Load = -500 W, battery discharging = +200 W, tolerance = 0 → target = 500 - 200 = 300 W
    assertEquals(300.0, ExportSchedulerService.computeTargetWatts(-500.0, 200.0, 0), DELTA);
  }

  @Test
  void targetWatts_toleranceAddsExportBuffer() {
    // Load = -600 W, battery = 0, tolerance = 50 W → target = 600 + 50 = 650 W
    assertEquals(650.0, ExportSchedulerService.computeTargetWatts(-600.0, 0.0, 50), DELTA);
  }

  // ========== computeTargetWattsFallback — fallback formula ==========

  @Test
  void targetWattsFallback_pvAndNoGrid_noTolerance() {
    // PV = 500 W, effectiveGrid = 0, tolerance = 0 → target = 500 W
    assertEquals(500.0, ExportSchedulerService.computeTargetWattsFallback(500.0, 0.0, 0), DELTA);
  }

  @Test
  void targetWattsFallback_pvAndGridImport_withTolerance() {
    // PV = 400 W, effectiveGrid = 100 W import, tolerance = 50 → target = 550 W
    assertEquals(550.0, ExportSchedulerService.computeTargetWattsFallback(400.0, 100.0, 50), DELTA);
  }

  @Test
  void targetWattsFallback_gridExportReducesTarget() {
    // PV = 600 W, effectiveGrid = -200 W export (already exporting), tolerance = 50
    // target = 600 - 200 + 50 = 450 W
    assertEquals(450.0, ExportSchedulerService.computeTargetWattsFallback(600.0, -200.0, 50), DELTA);
  }

  // ========== Helper ==========

  private static boolean isInBlockWindow(String now, String blockFrom, String enableFrom) {
    return ExportSchedulerService.isInBlockWindow(
      LocalTime.parse(now),
      LocalTime.parse(blockFrom),
      LocalTime.parse(enableFrom)
    );
  }
}
