package at.or.reder.frodo.cost.spi;

/**
 * Energy flow direction relative to the electricity grid.
 */
public enum PriceDirection {
  /** Energy flowing from grid to premises (consumer buys from grid). */
  IMPORT,
  /** Energy flowing from premises to grid (consumer sells to grid). */
  EXPORT
}
