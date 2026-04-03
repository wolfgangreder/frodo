package at.or.reder.frodo.modbus.entity;

/**
 * Status of the last metrics scrape operation for a device.
 */
public enum ScrapeStatus {

  /** Scrape completed successfully, all requested parameters were read. */
  SUCCESS,

  /** Scrape failed due to a communication or protocol error. */
  FAILED,

  /** Scrape timed out waiting for a response from the device. */
  TIMEOUT
}
