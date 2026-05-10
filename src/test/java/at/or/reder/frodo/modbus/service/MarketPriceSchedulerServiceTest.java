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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MarketPriceSchedulerService#eurMwhToCtKwh(double)}.
 *
 * <p>The conversion rule is: {@code 1 EUR/MWh = 0.1 ct/kWh → ct/kWh = EUR/MWh / 10}.
 * Negative values must remain negative (important for price-controlled export logic).</p>
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
}
