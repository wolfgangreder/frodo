package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.service.MetricsScrapingService.FieldAccumulator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsScrapingService.FieldAccumulator}.
 *
 * <p>These tests cover the per-minute accumulation logic used to reduce
 * database write frequency when the scrape interval is shorter than one minute.</p>
 */
class MetricsScrapingServiceTest {

  private static final Instant MINUTE = Instant.parse("2024-01-15T10:05:00Z");

  // ========== hasData ==========

  @Test
  void hasData_emptyAccumulator_returnsFalse() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    assertFalse(acc.hasData());
  }

  @Test
  void hasData_afterNumericAdd_returnsTrue() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add(42.0);
    assertTrue(acc.hasData());
  }

  @Test
  void hasData_afterStringAdd_returnsTrue() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add("active");
    assertTrue(acc.hasData());
  }

  @Test
  void hasData_nullValue_remainsFalse() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add(null);
    assertFalse(acc.hasData());
  }

  // ========== average ==========

  @Test
  void average_singleSample_returnsThatValue() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add(100.0);
    assertEquals(100.0, acc.average().getAsDouble(), 1e-9);
  }

  @Test
  void average_multipleSamples_returnsArithmeticMean() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add(10.0);
    acc.add(20.0);
    acc.add(30.0);
    assertEquals(20.0, acc.average().getAsDouble(), 1e-9);
  }

  @Test
  void average_noNumericSamples_returnsEmpty() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add("running");
    assertTrue(acc.average().isEmpty());
  }

  @Test
  void average_emptyAccumulator_returnsEmpty() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    assertTrue(acc.average().isEmpty());
  }

  @Test
  void average_integerValues_treatedAsDouble() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add(1);
    acc.add(2);
    acc.add(3);
    assertEquals(2.0, acc.average().getAsDouble(), 1e-9);
  }

  // ========== string handling ==========

  @Test
  void lastString_multipleStringValues_returnsLast() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add("first");
    acc.add("second");
    acc.add("third");
    assertEquals("third", acc.lastString);
  }

  @Test
  void lastString_mixedValues_numericDoesNotOverwriteString() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add("status");
    acc.add(99.0);
    assertEquals("status", acc.lastString);
  }

  // ========== sampleCount ==========

  @Test
  void sampleCount_zero_afterConstruction() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    assertEquals(0, acc.sampleCount());
  }

  @Test
  void sampleCount_countsOnlyNumerics() {
    FieldAccumulator acc = new FieldAccumulator(MINUTE);
    acc.add(1.0);
    acc.add(2.0);
    acc.add("text");
    assertEquals(2, acc.sampleCount());
  }

  // ========== minuteBucket ==========

  @Test
  void minuteBucket_storedExact() {
    Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    FieldAccumulator acc = new FieldAccumulator(bucket);
    assertEquals(bucket, acc.minuteBucket);
  }
}
