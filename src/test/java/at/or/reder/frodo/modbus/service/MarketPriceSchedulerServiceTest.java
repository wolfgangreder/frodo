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

import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import at.or.reder.frodo.modbus.service.model.MarketDataResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MarketPriceSchedulerService#eurMwhToCtKwh(double)}
 * and the timestamp conversion inside
 * {@link MarketPriceSchedulerService#persistPrices(List)}.
 *
 * <p>The conversion rule is: {@code 1 EUR/MWh = 0.1 ct/kWh → ct/kWh = EUR/MWh / 10}.
 * Negative values must remain negative (important for price-controlled export logic).</p>
 *
 * <p>aWATTar timestamps are standard UTC epoch milliseconds.
 * {@code persistPrices} uses {@link at.or.reder.frodo.TimeUtil#fromEpochMs}
 * to decode them to UTC LocalDateTime.</p>
 */
class MarketPriceSchedulerServiceTest {

  private static final double DELTA = 0.0001;

  @Test
  void conversion_typicalPositivePrice() {
    // 100 EUR/MWh → 10 ct/kWh
    assertEquals(10.0, MarketPriceSchedulerService.eurMwhToCtKwh(100.0).doubleValue(), DELTA);
  }

  @Test
  void conversion_zero() {
    assertEquals(0.0, MarketPriceSchedulerService.eurMwhToCtKwh(0.0).doubleValue(), DELTA);
  }

  @Test
  void conversion_negativePrice_remainsNegative() {
    // Negative EUR/MWh → negative ct/kWh (triggers export block)
    assertEquals(-5.0, MarketPriceSchedulerService.eurMwhToCtKwh(-50.0).doubleValue(), DELTA);
  }

  @Test
  void conversion_smallNegativePrice() {
    // -1 EUR/MWh → -0.1 ct/kWh  (still negative → still blocked)
    assertEquals(-0.1, MarketPriceSchedulerService.eurMwhToCtKwh(-1.0).doubleValue(), DELTA);
  }

  @Test
  void conversion_largePositivePrice() {
    // 500 EUR/MWh → 50 ct/kWh
    assertEquals(50.0, MarketPriceSchedulerService.eurMwhToCtKwh(500.0).doubleValue(), DELTA);
  }

  @Test
  void conversion_fractionalInput() {
    // 13.7 EUR/MWh → 1.37 ct/kWh
    assertEquals(1.37, MarketPriceSchedulerService.eurMwhToCtKwh(13.7).doubleValue(), DELTA);
  }

  @Test
  void conversion_scaleFactor_isExactlyOneTenth() {
    // The scale factor must be exactly 1/10 — not 0.099 or 0.101
    assertEquals(MarketPriceSchedulerService.eurMwhToCtKwh(10.0).doubleValue(), 1.0, DELTA);
  }

  // ========== persistPrices — timestamp conversion ==========

  /**
   * Verifies persistPrices decodes aWATTar UTC epoch ms correctly.
   * aWATTar returns standard Unix timestamps in milliseconds.
   */
  @Test
  void persistPrices_utcEpoch_decodesCorrectly() throws Exception {
    MarketPriceRepository repo = mock(MarketPriceRepository.class);
    MarketPriceSchedulerService service = serviceWithRepo(repo);

    // 2026-05-11T12:00:00Z = 1778500800000 ms (confirmed from live API)
    // 2026-05-11T13:00:00Z = 1778504400000 ms
    long startMs = 1778500800000L;
    long endMs = 1778504400000L;

    service.persistPrices(List.of(
      new MarketDataResponse.MarketPrice(startMs, endMs, 105.74, "Eur/MWh")
    ));

    LocalDateTime expectedStart = LocalDateTime.of(2026, 5, 11, 12, 0, 0);
    LocalDateTime expectedEnd = LocalDateTime.of(2026, 5, 11, 13, 0, 0);
    verify(repo).upsert(expectedStart, expectedEnd, MarketPriceSchedulerService.eurMwhToCtKwh(105.74));
  }

  /**
   * Verifies negative prices are persisted correctly with proper timestamps.
   */
  @Test
  void persistPrices_negativePrice_persistedCorrectly() throws Exception {
    MarketPriceRepository repo = mock(MarketPriceRepository.class);
    MarketPriceSchedulerService service = serviceWithRepo(repo);

    // 2026-01-15T14:00:00Z
    long startMs = LocalDateTime.of(2026, 1, 15, 14, 0, 0)
      .toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    long endMs = startMs + 3600_000L;

    service.persistPrices(List.of(
      new MarketDataResponse.MarketPrice(startMs, endMs, -5.0, "Eur/MWh")
    ));

    LocalDateTime expectedStart = LocalDateTime.of(2026, 1, 15, 14, 0, 0);
    LocalDateTime expectedEnd = LocalDateTime.of(2026, 1, 15, 15, 0, 0);
    verify(repo).upsert(expectedStart, expectedEnd, MarketPriceSchedulerService.eurMwhToCtKwh(-5.0));
  }

  /**
   * Verifies multiple price entries are all persisted with correct timestamps.
   */
  @Test
  void persistPrices_multipleEntries_allPersisted() throws Exception {
    MarketPriceRepository repo = mock(MarketPriceRepository.class);
    MarketPriceSchedulerService service = serviceWithRepo(repo);

    // Two consecutive hours: 2026-05-11T12:00Z and 2026-05-11T13:00Z
    long start1 = 1778500800000L; // 12:00 UTC
    long end1 = 1778504400000L;   // 13:00 UTC
    long start2 = end1;
    long end2 = start2 + 3600_000L; // 14:00 UTC

    int saved = service.persistPrices(List.of(
      new MarketDataResponse.MarketPrice(start1, end1, 105.74, "Eur/MWh"),
      new MarketDataResponse.MarketPrice(start2, end2, 106.45, "Eur/MWh")
    ));

    assertEquals(2, saved);
    verify(repo).upsert(
      LocalDateTime.of(2026, 5, 11, 12, 0, 0),
      LocalDateTime.of(2026, 5, 11, 13, 0, 0),
      MarketPriceSchedulerService.eurMwhToCtKwh(105.74)
    );
    verify(repo).upsert(
      LocalDateTime.of(2026, 5, 11, 13, 0, 0),
      LocalDateTime.of(2026, 5, 11, 14, 0, 0),
      MarketPriceSchedulerService.eurMwhToCtKwh(106.45)
    );
  }

  // ========== Helper ==========

  private static MarketPriceSchedulerService serviceWithRepo(MarketPriceRepository repo) throws Exception {
    MarketPriceSchedulerService service = new MarketPriceSchedulerService();
    Field field = MarketPriceSchedulerService.class.getDeclaredField("marketPriceRepository");
    field.setAccessible(true);
    field.set(service, repo);
    return service;
  }
}
