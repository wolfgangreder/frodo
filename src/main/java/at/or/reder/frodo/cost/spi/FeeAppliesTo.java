package at.or.reder.frodo.cost.spi;

/**
 * Which energy flow direction a grid fee applies to.
 */
public enum FeeAppliesTo {
  /** Fee applies only to exported energy. */
  EXPORT,
  /** Fee applies only to imported energy. */
  IMPORT,
  /** Fee applies to both imported and exported energy. */
  BOTH
}
