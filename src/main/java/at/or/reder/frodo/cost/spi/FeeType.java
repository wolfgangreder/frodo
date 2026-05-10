package at.or.reder.frodo.cost.spi;

/**
 * Grid fee calculation type.
 */
public enum FeeType {
  /**
   * Percentage of the base energy cost.
   * {@code feeValue} is a percentage (e.g. 5.0 = 5%).
   */
  PERCENT,

  /**
   * Absolute charge per kilowatt-hour.
   * {@code feeValue} is in ct/kWh.
   */
  ABSOLUTE_ENERGY,

  /**
   * Absolute charge per month, amortised per hour (÷ 730).
   * {@code feeValue} is in EUR/month.
   */
  ABSOLUTE_TIME
}
