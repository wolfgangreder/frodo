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
    assertEquals(10.0, MarketPriceSchedulerService.eurMwhToCtKwh(100.0), DELTA);
  }

  @Test
  void conversion_zero() {
    assertEquals(0.0, MarketPriceSchedulerService.eurMwhToCtKwh(0.0), DELTA);
  }

  @Test
  void conversion_negativePrice_remainsNegative() {
    // Negative EUR/MWh → negative ct/kWh (triggers export block)
    assertEquals(-5.0, MarketPriceSchedulerService.eurMwhToCtKwh(-50.0), DELTA);
  }

  @Test
  void conversion_smallNegativePrice() {
    // -1 EUR/MWh → -0.1 ct/kWh  (still negative → still blocked)
    assertEquals(-0.1, MarketPriceSchedulerService.eurMwhToCtKwh(-1.0), DELTA);
  }

  @Test
  void conversion_largePositivePrice() {
    // 500 EUR/MWh → 50 ct/kWh
    assertEquals(50.0, MarketPriceSchedulerService.eurMwhToCtKwh(500.0), DELTA);
  }

  @Test
  void conversion_fractionalInput() {
    // 13.7 EUR/MWh → 1.37 ct/kWh
    assertEquals(1.37, MarketPriceSchedulerService.eurMwhToCtKwh(13.7), DELTA);
  }

  @Test
  void conversion_scaleFactor_isExactlyOneTenth() {
    // The scale factor must be exactly 1/10 — not 0.099 or 0.101
    assertEquals(MarketPriceSchedulerService.eurMwhToCtKwh(10.0), 1.0, DELTA);
  }
}
