package at.or.reder.frodo.modbus.connection;

/**
 * Represents the lifecycle state of a Modbus TCP connection.
 */
public enum ConnectionState {
  /**
   * Connection is not established. Initial state or after disconnect.
   */
  DISCONNECTED,

  /**
   * Connection attempt is in progress.
   */
  CONNECTING,

  /**
   * Connection is established and ready for requests.
   */
  CONNECTED,

  /**
   * Connection has failed and reconnect is pending.
   */
  FAILED
}
