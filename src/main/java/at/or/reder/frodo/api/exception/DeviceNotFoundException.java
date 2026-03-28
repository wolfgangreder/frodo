package at.or.reder.frodo.api.exception;

/**
 * Exception thrown when a requested device is not found.
 */
public class DeviceNotFoundException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message the error message
   */
  public DeviceNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates a new exception for a device ID.
   *
   * @param deviceId the device ID that was not found
   * @return exception instance
   */
  public static DeviceNotFoundException forId(Long deviceId) {
    return new DeviceNotFoundException("Device not found: id=" + deviceId);
  }
}
