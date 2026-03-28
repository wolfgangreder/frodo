package at.or.reder.frodo.modbus;

/**
 * Exception representing a Modbus protocol error response.
 *
 * <p>When a Modbus device returns an exception response (function code with
 * high bit set, e.g. 0xAB for FC 0x2B), this exception carries the original
 * function code and the Modbus exception code.</p>
 *
 * <p>Common Modbus exception codes:</p>
 * <ul>
 *   <li>0x01 - Illegal Function</li>
 *   <li>0x02 - Illegal Data Address</li>
 *   <li>0x03 - Illegal Data Value</li>
 *   <li>0x04 - Server Device Failure</li>
 *   <li>0x05 - Acknowledge</li>
 *   <li>0x06 - Server Device Busy</li>
 * </ul>
 */
public class ModbusException extends RuntimeException {

  private final int functionCode;
  private final int exceptionCode;

  /**
   * Creates a new ModbusException.
   *
   * @param functionCode  the original function code from the request
   * @param exceptionCode the Modbus exception code from the error response
   */
  public ModbusException(int functionCode, int exceptionCode) {
    super(String.format("Modbus exception: FC=0x%02X, exception code=0x%02X (%s)",
      functionCode, exceptionCode, describeExceptionCode(exceptionCode)));
    this.functionCode = functionCode;
    this.exceptionCode = exceptionCode;
  }

  /**
   * Returns the original function code from the request.
   *
   * @return function code (e.g. 0x2B for Read Device Identification)
   */
  public int getFunctionCode() {
    return functionCode;
  }

  /**
   * Returns the Modbus exception code from the error response.
   *
   * @return exception code (0x01-0x06 typically)
   */
  public int getExceptionCode() {
    return exceptionCode;
  }

  /**
   * Returns a human-readable description of a Modbus exception code.
   *
   * @param exceptionCode the exception code
   * @return description string
   */
  public static String describeExceptionCode(int exceptionCode) {
    return switch (exceptionCode) {
      case 0x01 -> "Illegal Function";
      case 0x02 -> "Illegal Data Address";
      case 0x03 -> "Illegal Data Value";
      case 0x04 -> "Server Device Failure";
      case 0x05 -> "Acknowledge";
      case 0x06 -> "Server Device Busy";
      default -> "Unknown (0x" + Integer.toHexString(exceptionCode) + ")";
    };
  }
}
