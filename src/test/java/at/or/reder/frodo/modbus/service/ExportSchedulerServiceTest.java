package at.or.reder.frodo.modbus.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExportSchedulerService#isInBlockWindow(LocalTime, LocalTime, LocalTime)}.
 *
 * <p>Covers three window types:</p>
 * <ul>
 *   <li>Same-day window (blockFrom &lt; enableFrom)</li>
 *   <li>Cross-midnight window (blockFrom &gt; enableFrom)</li>
 *   <li>Degenerate window (blockFrom == enableFrom → always blocked)</li>
 * </ul>
 */
class ExportSchedulerServiceTest {

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

  // ========== Helper ==========

  private static boolean isInBlockWindow(String now, String blockFrom, String enableFrom) {
    return ExportSchedulerService.isInBlockWindow(
      LocalTime.parse(now),
      LocalTime.parse(blockFrom),
      LocalTime.parse(enableFrom)
    );
  }
}
