package at.or.reder.frodo.modbus.entity;

/**
 * Blocking strategy applied during a grid-export schedule window.
 *
 * <p>Controls how the inverter's {@code WMaxLimPct} is computed each time
 * the scheduler writes the export block during an active time window.</p>
 */
public enum ExportBlockStrategy {

  /**
   * Closed-loop dynamic zero-export (Nulleinspeisung).
   *
   * <p>Every scheduler tick the house load is estimated from the Smart Meter's
   * real-time grid power reading:</p>
   * <pre>
   *   houseLoad = inverterW + meterW
   *   limitPct  = clamp(houseLoad / WMax × 100, 0, 100)
   * </pre>
   * <p>This minimises grid export while allowing the inverter to supply as much
   * house load as possible with solar.  Requires an enabled Smart Meter child
   * device on the same inverter.</p>
   */
  ZERO_EXPORT_DYNAMIC,

  /**
   * Fixed watt-cap limit.
   *
   * <p>A static {@code WMaxLimPct} is derived once from the configured
   * {@link at.or.reder.frodo.modbus.entity.ExportScheduleEntity#limitWatts} value:</p>
   * <pre>
   *   limitPct = clamp(limitWatts / WMax × 100, 0, 100)
   * </pre>
   * <p>The same percentage is written every tick for reliability.
   * Allows a small, constant grid feed-in (e.g. 50 W) without requiring a
   * Smart Meter child device.</p>
   */
  FIXED_LIMIT,

  /**
   * Price-controlled export reduction (aWATTar AT market prices).
   *
   * <p>Every scheduler tick the current market price is fetched from the
   * {@link at.or.reder.frodo.modbus.repository.MarketPriceRepository}.
   * When the price (minus grace tolerance) is negative, grid export is blocked.</p>
   * <pre>
   *   priceThreshold = -priceGraceTolerance
   *   if (priceEur < priceThreshold) block export
   * </pre>
   * <p>Requires the market price scheduler to be running and fetching
   * prices from aWATTar AT.</p>
   */
  PRICE_CONTROLLED,

  /**
   * GPIO-based price-controlled export (RPi5 only).
   *
   * <p>When the market price is negative, sets the GPIO output pin of the
   * configured pair to the block level, controlling an external relay/switch
   * instead of writing Modbus WMaxLim registers.  Monitors the paired GPIO
   * input pin to detect when an external override switch has taken control —
   * in that case, Modbus throttling is completely disabled (inverter runs at
   * 100%) and the system only reports state.</p>
   *
   * <p>Uses JDK Foreign Function & Memory API with Linux GPIO character
   * device ioctl for direct GPIO access via /dev/gpiochip* (zero external
   * dependencies).</p>
   *
   * <p>Requires {@code frodo.gpio.enabled=true}, a Raspberry Pi 5 with
   * Linux kernel 5.10+ (GPIO v2 ABI), and a GPIO pair assigned to the
   * device in the database.  Falls back to {@code PRICE_CONTROLLED}
   * (Modbus throttling) if GPIO is unavailable, initialisation fails, or
   * no pair is assigned to the device.</p>
   *
   * <p>Manual grid supply disable via REST API is always honoured regardless
   * of external switch state.</p>
   */
  PRICE_CONTROLLED_GPIO
}
