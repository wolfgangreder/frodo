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
 * Constants for Modbus Device Identification object IDs.
 *
 * <p>Defines the standard object identifiers used in the
 * Read Device Identification (FC 0x2B/MEI 0x0E) response.
 * Objects 0x00-0x02 are mandatory (Basic), 0x03-0x06 are optional
 * (Regular/Extended), and 0x80-0xFF are vendor-specific.</p>
 *
 * @see ReadDeviceIdCode
 */
public final class ModbusObjectId {

  /** Vendor Name (mandatory, Basic identification). */
  public static final int VENDOR_NAME = 0x00;

  /** Product Code (mandatory, Basic identification). */
  public static final int PRODUCT_CODE = 0x01;

  /** Major Minor Revision (mandatory, Basic identification). */
  public static final int MAJOR_MINOR_REVISION = 0x02;

  /** Vendor URL (optional, Regular identification). */
  public static final int VENDOR_URL = 0x03;

  /** Product Name (optional, Regular identification). */
  public static final int PRODUCT_NAME = 0x04;

  /** Model Name (optional, Regular identification). */
  public static final int MODEL_NAME = 0x05;

  /** User Application Name (optional, Extended identification). */
  public static final int USER_APPLICATION_NAME = 0x06;

  /** First vendor-specific object ID. */
  public static final int VENDOR_SPECIFIC_START = 0x80;

  /** Last vendor-specific object ID. */
  public static final int VENDOR_SPECIFIC_END = 0xFF;

  private ModbusObjectId() {
    // Utility class, no instantiation
  }

  /**
   * Checks whether the given object ID is in the vendor-specific range (0x80-0xFF).
   *
   * @param objectId the object ID to check
   * @return true if the object ID is vendor-specific
   */
  public static boolean isVendorSpecific(int objectId) {
    return objectId >= VENDOR_SPECIFIC_START && objectId <= VENDOR_SPECIFIC_END;
  }

  /**
   * Returns a human-readable name for a known object ID.
   *
   * @param objectId the object ID
   * @return descriptive name, or "Unknown(0xHH)" for unrecognized IDs
   */
  public static String nameOf(int objectId) {
    return switch (objectId) {
      case VENDOR_NAME -> "VendorName";
      case PRODUCT_CODE -> "ProductCode";
      case MAJOR_MINOR_REVISION -> "MajorMinorRevision";
      case VENDOR_URL -> "VendorUrl";
      case PRODUCT_NAME -> "ProductName";
      case MODEL_NAME -> "ModelName";
      case USER_APPLICATION_NAME -> "UserApplicationName";
      default -> {
        if (isVendorSpecific(objectId)) {
          yield "VendorSpecific(0x" + Integer.toHexString(objectId) + ")";
        }
        yield "Unknown(0x" + Integer.toHexString(objectId) + ")";
      }
    };
  }
}
