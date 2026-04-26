package at.or.reder.frodo.modbus.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link MarketPriceEntity}.
 *
 * <p>Covers field assignment and the {@code @PrePersist} lifecycle callback,
 * which ensures {@code createdAt} is populated on insert and never overwritten.</p>
 */
class MarketPriceEntityTest {

  private static final double DELTA = 0.0001;

  @Test
  void fieldAssignment_roundTrip() {
    LocalDateTime start = LocalDateTime.of(2025, 6, 1, 14, 0);
    LocalDateTime end   = LocalDateTime.of(2025, 6, 1, 15, 0);

    MarketPriceEntity e = new MarketPriceEntity();
    e.startTime = start;
    e.endTime   = end;
    e.priceCt   = 12.5;

    assertEquals(start, e.startTime);
    assertEquals(end,   e.endTime);
    assertEquals(12.5,  e.priceCt, DELTA);
  }

  @Test
  void prePersist_setsCreatedAt_whenNull() {
    MarketPriceEntity e = new MarketPriceEntity();

    Instant before = Instant.now();
    e.onCreate();
    Instant after = Instant.now();

    assertNotNull(e.createdAt);
    // createdAt must be within the test execution window
    assert !e.createdAt.isBefore(before) : "createdAt is before test start";
    assert !e.createdAt.isAfter(after)   : "createdAt is after test end";
  }

  @Test
  void prePersist_doesNotOverwriteExistingCreatedAt() {
    MarketPriceEntity e = new MarketPriceEntity();
    Instant original = Instant.parse("2025-01-01T00:00:00Z");
    e.createdAt = original;

    e.onCreate();  // should be a no-op when createdAt is already set

    assertSame(original, e.createdAt, "onCreate() must not overwrite an existing createdAt");
  }

  @Test
  void negativePriceCt_storedCorrectly() {
    MarketPriceEntity e = new MarketPriceEntity();
    e.priceCt = -3.7;

    assertEquals(-3.7, e.priceCt, DELTA, "Negative prices must be preserved exactly");
  }
}
