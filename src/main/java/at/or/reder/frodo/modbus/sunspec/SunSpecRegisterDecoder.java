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

package at.or.reder.frodo.modbus.sunspec;

import java.nio.charset.StandardCharsets;

/**
 * Decodes raw Modbus holding register values into typed Java values
 * according to SunSpec data type conventions.
 *
 * <p>All multi-register values are big-endian (most significant register
 * first), as specified by the Modbus and SunSpec protocols.</p>
 *
 * <p>This class contains only static utility methods and is thread-safe.</p>
 *
 * <p><b>Protocol References:</b></p>
 * <ul>
 *   <li>SunSpec Data Types: {@code refdoc/gen24-modbus-api-external-docs/Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx}</li>
 *   <li>Data types covered: uint16, int16, uint32, int32, float32, acc32, acc64, string, enum, bitfield, sunssf</li>
 *   <li>Not Implemented values: 0xFFFF (uint16), 0x8000 (int16), NaN (float32)</li>
 * </ul>
 */
public final class SunSpecRegisterDecoder {

  /** SunSpec "not implemented" sentinel for uint16 fields. */
  public static final int NOT_IMPLEMENTED_UINT16 = 0xFFFF;

  /** SunSpec "not implemented" sentinel for int16 fields. */
  public static final int NOT_IMPLEMENTED_INT16 = 0x8000;

  /** SunSpec "not implemented" sentinel for sunssf fields. */
  public static final int NOT_IMPLEMENTED_SUNSSF = 0x8000;

  /** SunSpec "not implemented" sentinel for acc32 fields. */
  public static final long NOT_IMPLEMENTED_ACC32 = 0x00000000L;

  /** SunSpec "not implemented" sentinel for uint32 fields. */
  public static final long NOT_IMPLEMENTED_UINT32 = 0xFFFFFFFFL;

  /** SunSpec "not implemented" sentinel for float32 fields. */
  public static final float NOT_IMPLEMENTED_FLOAT32 = Float.NaN;

  private SunSpecRegisterDecoder() {
    // Utility class
  }

  /**
   * Decodes a uint16 value from a single register.
   *
   * @param registers register array
   * @param offset    index into the array
   * @return unsigned 16-bit value (0-65535), or {@code null} if "not implemented"
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Integer decodeUint16(int[] registers, int offset) {
    validateOffset(registers, offset, 1);
    int raw = registers[offset] & 0xFFFF;
    if (raw == NOT_IMPLEMENTED_UINT16) {
      return null;
    }
    return raw;
  }

  /**
   * Decodes an int16 value from a single register.
   *
   * @param registers register array
   * @param offset    index into the array
   * @return signed 16-bit value (-32768 to 32767), or {@code null} if "not implemented"
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Integer decodeInt16(int[] registers, int offset) {
    validateOffset(registers, offset, 1);
    int raw = registers[offset] & 0xFFFF;
    if (raw == (NOT_IMPLEMENTED_INT16 & 0xFFFF)) {
      return null;
    }
    // Sign-extend from 16 bits
    return (int) (short) raw;
  }

  /**
   * Decodes a uint32 value from two consecutive registers (big-endian).
   *
   * @param registers register array
   * @param offset    index of the first (high) register
   * @return unsigned 32-bit value (0 to 4294967295), or {@code null} if "not implemented"
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Long decodeUint32(int[] registers, int offset) {
    validateOffset(registers, offset, 2);
    long high = (registers[offset] & 0xFFFFL) << 16;
    long low = registers[offset + 1] & 0xFFFFL;
    long value = high | low;
    if (value == NOT_IMPLEMENTED_UINT32) {
      return null;
    }
    return value;
  }

  /**
   * Decodes an acc32 (accumulated unsigned 32-bit) value from two registers.
   *
   * @param registers register array
   * @param offset    index of the first (high) register
   * @return accumulated value, or {@code null} if "not implemented" (value 0)
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Long decodeAcc32(int[] registers, int offset) {
    validateOffset(registers, offset, 2);
    long high = (registers[offset] & 0xFFFFL) << 16;
    long low = registers[offset + 1] & 0xFFFFL;
    long value = high | low;
    if (value == NOT_IMPLEMENTED_ACC32) {
      return null;
    }
    return value;
  }

  /**
   * Decodes an acc64 (accumulated unsigned 64-bit) value from four registers.
   *
   * @param registers register array
   * @param offset    index of the first (highest) register
   * @return accumulated value, or {@code null} if "not implemented" (value 0)
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Long decodeAcc64(int[] registers, int offset) {
    validateOffset(registers, offset, 4);
    long value = ((registers[offset] & 0xFFFFL) << 48)
      | ((registers[offset + 1] & 0xFFFFL) << 32)
      | ((registers[offset + 2] & 0xFFFFL) << 16)
      | (registers[offset + 3] & 0xFFFFL);
    if (value == 0L) {
      return null;
    }
    return value;
  }

  /**
   * Decodes an IEEE 754 float32 value from two consecutive registers (big-endian).
   *
   * @param registers register array
   * @param offset    index of the first (high) register
   * @return float value, or {@code null} if NaN (SunSpec "not implemented")
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Float decodeFloat32(int[] registers, int offset) {
    validateOffset(registers, offset, 2);
    int bits = ((registers[offset] & 0xFFFF) << 16) | (registers[offset + 1] & 0xFFFF);
    float value = Float.intBitsToFloat(bits);
    if (Float.isNaN(value)) {
      return null;
    }
    return value;
  }

  /**
   * Decodes a SunSpec scale factor (sunssf) from a single register.
   * The scale factor is a signed 16-bit integer representing a base-10 exponent.
   *
   * @param registers register array
   * @param offset    index into the array
   * @return scale factor exponent, or {@code null} if "not implemented"
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Integer decodeSunssf(int[] registers, int offset) {
    return decodeInt16(registers, offset);
  }

  /**
   * Decodes a string from consecutive registers.
   * Each register holds 2 bytes (big-endian). The string is trimmed of
   * trailing null bytes and whitespace.
   *
   * @param registers    register array
   * @param offset       index of the first register
   * @param registerSize number of registers the string occupies
   * @return decoded and trimmed string
   * @throws IllegalArgumentException if offset/size is out of bounds
   */
  public static String decodeString(int[] registers, int offset, int registerSize) {
    validateOffset(registers, offset, registerSize);
    byte[] bytes = new byte[registerSize * 2];
    for (int i = 0; i < registerSize; i++) {
      bytes[i * 2] = (byte) ((registers[offset + i] >> 8) & 0xFF);
      bytes[i * 2 + 1] = (byte) (registers[offset + i] & 0xFF);
    }
    // Find end of string (first null byte)
    int len = bytes.length;
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == 0) {
        len = i;
        break;
      }
    }
    return new String(bytes, 0, len, StandardCharsets.US_ASCII).trim();
  }

  /**
   * Decodes a bitfield16 value from a single register.
   *
   * @param registers register array
   * @param offset    index into the array
   * @return bitmask value (0-65535), or {@code null} if "not implemented"
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Integer decodeBitfield16(int[] registers, int offset) {
    return decodeUint16(registers, offset);
  }

  /**
   * Decodes a bitfield32 value from two consecutive registers (big-endian).
   *
   * @param registers register array
   * @param offset    index of the first (high) register
   * @return bitmask value, or {@code null} if "not implemented"
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Long decodeBitfield32(int[] registers, int offset) {
    return decodeUint32(registers, offset);
  }

  /**
   * Decodes an enum16 value from a single register.
   *
   * @param registers register array
   * @param offset    index into the array
   * @return enum ordinal value, or {@code null} if "not implemented" (0xFFFF)
   * @throws IllegalArgumentException if offset is out of bounds
   */
  public static Integer decodeEnum16(int[] registers, int offset) {
    return decodeUint16(registers, offset);
  }

  /**
   * Applies a SunSpec scale factor to a raw integer value.
   * Real value = rawValue * 10^scaleFactor.
   *
   * @param rawValue    the raw integer register value
   * @param scaleFactor the scale factor exponent (may be negative)
   * @return scaled double value, or {@code null} if either input is null
   */
  public static Double applyScaleFactor(Integer rawValue, Integer scaleFactor) {
    if (rawValue == null || scaleFactor == null) {
      return null;
    }
    return rawValue * Math.pow(10, scaleFactor);
  }

  /**
   * Validates that the register array has enough elements starting at offset.
   *
   * @param registers register array
   * @param offset    starting offset
   * @param count     number of registers needed
   * @throws IllegalArgumentException if bounds check fails
   */
  private static void validateOffset(int[] registers, int offset, int count) {
    if (registers == null) {
      throw new IllegalArgumentException("Register array must not be null");
    }
    if (offset < 0 || offset + count > registers.length) {
      throw new IllegalArgumentException(
        String.format("Offset %d + count %d exceeds register array length %d",
          offset, count, registers.length));
    }
  }
}
