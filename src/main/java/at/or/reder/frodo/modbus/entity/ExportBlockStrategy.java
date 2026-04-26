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
  PRICE_CONTROLLED
}
