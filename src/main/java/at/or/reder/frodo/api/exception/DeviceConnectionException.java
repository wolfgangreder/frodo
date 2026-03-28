package at.or.reder.frodo.api.exception;

/**
 * Exception thrown when device connection fails.
 */
public class DeviceConnectionException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message the error message
   */
  public DeviceConnectionException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given message and cause.
   *
   * @param message the error message
   * @param cause   the underlying cause
   */
  public DeviceConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
