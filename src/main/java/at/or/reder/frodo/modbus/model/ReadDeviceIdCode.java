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

package at.or.reder.frodo.modbus.model;

/**
 * Modbus Read Device Identification code values.
 * Defines the level of device identification to request.
 *
 * <p>Per Modbus specification, the Read Device ID code determines which
 * set of object IDs are returned in the response:</p>
 * <ul>
 *   <li>{@link #BASIC} - Vendor Name, Product Code, Major Minor Revision</li>
 *   <li>{@link #REGULAR} - Basic + Vendor URL, Product Name, Model Name</li>
 *   <li>{@link #EXTENDED} - Regular + User Application Name + additional objects</li>
 *   <li>{@link #SPECIFIC} - Read a single specific object by ID</li>
 * </ul>
 *
 * @see ModbusObjectId
 */
public enum ReadDeviceIdCode {

  /**
   * Basic device identification: Vendor Name (0x00), Product Code (0x01),
   * Major Minor Revision (0x02).
   */
  BASIC(0x01),

  /**
   * Regular device identification: Basic objects plus Vendor URL (0x03),
   * Product Name (0x04), Model Name (0x05).
   */
  REGULAR(0x02),

  /**
   * Extended device identification: Regular objects plus User Application Name (0x06)
   * and any vendor-specific objects (0x80-0xFF).
   */
  EXTENDED(0x03),

  /**
   * Specific device identification: Read a single object by its ID.
   */
  SPECIFIC(0x04);

  private final int code;

  ReadDeviceIdCode(int code) {
    this.code = code;
  }

  /**
   * Returns the Modbus protocol code value.
   *
   * @return protocol code (0x01-0x04)
   */
  public int getCode() {
    return code;
  }

  /**
   * Finds a ReadDeviceIdCode by its protocol code value.
   *
   * @param code the protocol code (0x01-0x04)
   * @return the matching ReadDeviceIdCode
   * @throws IllegalArgumentException if the code is not recognized
   */
  public static ReadDeviceIdCode fromCode(int code) {
    for (ReadDeviceIdCode value : values()) {
      if (value.code == code) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknown Read Device ID code: 0x" + Integer.toHexString(code));
  }
}
