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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

  @Test
  void fieldAssignment_roundTrip() {
    LocalDateTime start = LocalDateTime.of(2025, 6, 1, 14, 0);
    LocalDateTime end   = LocalDateTime.of(2025, 6, 1, 15, 0);

    MarketPriceEntity e = new MarketPriceEntity();
    e.startTime = start;
    e.endTime   = end;
    e.priceCt   = new BigDecimal("12.5");

    assertEquals(start, e.startTime);
    assertEquals(end,   e.endTime);
    assertEquals(0, new BigDecimal("12.5").compareTo(e.priceCt));
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
    e.priceCt = new BigDecimal("-3.7");

    assertEquals(0, new BigDecimal("-3.7").compareTo(e.priceCt), "Negative prices must be preserved exactly");
  }
}
