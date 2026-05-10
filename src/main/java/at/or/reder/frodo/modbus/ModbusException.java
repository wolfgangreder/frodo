/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
