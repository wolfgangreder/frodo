package at.or.reder.frodo.api.dto;

/**
 * Connection status for a Modbus device.
 */
public enum ConnectionStatus {
  /**
   * Connection status unknown (device not yet contacted).
   */
  UNKNOWN,

  /**
   * Device is connected and responding.
   */
  CONNECTED,

  /**
   * Connection to device failed.
   */
  FAILED
}
