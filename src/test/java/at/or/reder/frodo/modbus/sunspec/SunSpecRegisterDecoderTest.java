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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link SunSpecRegisterDecoder}.
 */
class SunSpecRegisterDecoderTest {

  // ---- decodeUint16 ----

  @Test
  void testDecodeUint16() {
    int[] registers = {100};
    assertEquals(100, SunSpecRegisterDecoder.decodeUint16(registers, 0));
  }

  @Test
  void testDecodeUint16MaxValue() {
    int[] registers = {0xFFFE};
    assertEquals(0xFFFE, SunSpecRegisterDecoder.decodeUint16(registers, 0));
  }

  @Test
  void testDecodeUint16Zero() {
    int[] registers = {0};
    assertEquals(0, SunSpecRegisterDecoder.decodeUint16(registers, 0));
  }

  @Test
  void testDecodeUint16NotImplemented() {
    int[] registers = {0xFFFF};
    assertNull(SunSpecRegisterDecoder.decodeUint16(registers, 0));
  }

  @Test
  void testDecodeUint16WithOffset() {
    int[] registers = {10, 20, 30};
    assertEquals(20, SunSpecRegisterDecoder.decodeUint16(registers, 1));
    assertEquals(30, SunSpecRegisterDecoder.decodeUint16(registers, 2));
  }

  // ---- decodeInt16 ----

  @Test
  void testDecodeInt16Positive() {
    int[] registers = {100};
    assertEquals(100, SunSpecRegisterDecoder.decodeInt16(registers, 0));
  }

  @Test
  void testDecodeInt16Negative() {
    // -1 as unsigned 16-bit = 0xFFFF, but that's "not implemented" for uint16
    // -2 as unsigned 16-bit = 0xFFFE
    int[] registers = {0xFFFE};
    assertEquals(-2, SunSpecRegisterDecoder.decodeInt16(registers, 0));
  }

  @Test
  void testDecodeInt16MinNegative() {
    // -32767 as unsigned 16-bit = 0x8001
    int[] registers = {0x8001};
    assertEquals(-32767, SunSpecRegisterDecoder.decodeInt16(registers, 0));
  }

  @Test
  void testDecodeInt16MaxPositive() {
    int[] registers = {0x7FFF};
    assertEquals(32767, SunSpecRegisterDecoder.decodeInt16(registers, 0));
  }

  @Test
  void testDecodeInt16NotImplemented() {
    // 0x8000 is the "not implemented" sentinel for int16
    int[] registers = {0x8000};
    assertNull(SunSpecRegisterDecoder.decodeInt16(registers, 0));
  }

  @Test
  void testDecodeInt16Zero() {
    int[] registers = {0};
    assertEquals(0, SunSpecRegisterDecoder.decodeInt16(registers, 0));
  }

  // ---- decodeUint32 ----

  @Test
  void testDecodeUint32() {
    int[] registers = {0x0001, 0x0000};  // 0x00010000 = 65536
    assertEquals(65536L, SunSpecRegisterDecoder.decodeUint32(registers, 0));
  }

  @Test
  void testDecodeUint32SmallValue() {
    int[] registers = {0x0000, 0x0064};  // 100
    assertEquals(100L, SunSpecRegisterDecoder.decodeUint32(registers, 0));
  }

  @Test
  void testDecodeUint32LargeValue() {
    int[] registers = {0xFFFF, 0xFFFE};  // 0xFFFFFFFE = 4294967294
    assertEquals(4294967294L, SunSpecRegisterDecoder.decodeUint32(registers, 0));
  }

  @Test
  void testDecodeUint32NotImplemented() {
    int[] registers = {0xFFFF, 0xFFFF};  // 0xFFFFFFFF = not implemented
    assertNull(SunSpecRegisterDecoder.decodeUint32(registers, 0));
  }

  @Test
  void testDecodeUint32Zero() {
    int[] registers = {0, 0};
    assertEquals(0L, SunSpecRegisterDecoder.decodeUint32(registers, 0));
  }

  // ---- decodeAcc32 ----

  @Test
  void testDecodeAcc32() {
    int[] registers = {0x0001, 0x0000};  // 65536
    assertEquals(65536L, SunSpecRegisterDecoder.decodeAcc32(registers, 0));
  }

  @Test
  void testDecodeAcc32NotImplemented() {
    // For acc32, 0 means "not implemented"
    int[] registers = {0x0000, 0x0000};
    assertNull(SunSpecRegisterDecoder.decodeAcc32(registers, 0));
  }

  @Test
  void testDecodeAcc32LargeValue() {
    int[] registers = {0xFFFF, 0xFFFF};  // max uint32 value is valid for acc32
    assertEquals(0xFFFFFFFFL, SunSpecRegisterDecoder.decodeAcc32(registers, 0));
  }

  // ---- decodeAcc64 ----

  @Test
  void testDecodeAcc64() {
    int[] registers = {0x0000, 0x0000, 0x0001, 0x0000};  // 65536
    assertEquals(65536L, SunSpecRegisterDecoder.decodeAcc64(registers, 0));
  }

  @Test
  void testDecodeAcc64NotImplemented() {
    int[] registers = {0, 0, 0, 0};
    assertNull(SunSpecRegisterDecoder.decodeAcc64(registers, 0));
  }

  @Test
  void testDecodeAcc64LargeValue() {
    int[] registers = {0x0000, 0x0001, 0x0000, 0x0000};  // 2^32 = 4294967296
    assertEquals(4294967296L, SunSpecRegisterDecoder.decodeAcc64(registers, 0));
  }

  // ---- decodeFloat32 ----

  @Test
  void testDecodeFloat32() {
    // 42.0f = 0x42280000
    int bits = Float.floatToIntBits(42.0f);
    int high = (bits >> 16) & 0xFFFF;
    int low = bits & 0xFFFF;
    int[] registers = {high, low};
    assertEquals(42.0f, SunSpecRegisterDecoder.decodeFloat32(registers, 0));
  }

  @Test
  void testDecodeFloat32NegativeValue() {
    int bits = Float.floatToIntBits(-123.456f);
    int high = (bits >> 16) & 0xFFFF;
    int low = bits & 0xFFFF;
    int[] registers = {high, low};
    assertEquals(-123.456f, SunSpecRegisterDecoder.decodeFloat32(registers, 0));
  }

  @Test
  void testDecodeFloat32Zero() {
    int[] registers = {0x0000, 0x0000};
    assertEquals(0.0f, SunSpecRegisterDecoder.decodeFloat32(registers, 0));
  }

  @Test
  void testDecodeFloat32NotImplemented() {
    // NaN represents "not implemented" in SunSpec
    int bits = Float.floatToIntBits(Float.NaN);
    int high = (bits >> 16) & 0xFFFF;
    int low = bits & 0xFFFF;
    int[] registers = {high, low};
    assertNull(SunSpecRegisterDecoder.decodeFloat32(registers, 0));
  }

  @Test
  void testDecodeFloat32SmallValue() {
    int bits = Float.floatToIntBits(0.001f);
    int high = (bits >> 16) & 0xFFFF;
    int low = bits & 0xFFFF;
    int[] registers = {high, low};
    assertEquals(0.001f, SunSpecRegisterDecoder.decodeFloat32(registers, 0));
  }

  // ---- decodeSunssf ----

  @Test
  void testDecodeSunssf() {
    // SF = -2 means multiply by 0.01
    // -2 as unsigned 16-bit = 0xFFFE
    int[] registers = {0xFFFE};
    assertEquals(-2, SunSpecRegisterDecoder.decodeSunssf(registers, 0));
  }

  @Test
  void testDecodeSunssfZero() {
    int[] registers = {0};
    assertEquals(0, SunSpecRegisterDecoder.decodeSunssf(registers, 0));
  }

  @Test
  void testDecodeSunssfPositive() {
    int[] registers = {3};
    assertEquals(3, SunSpecRegisterDecoder.decodeSunssf(registers, 0));
  }

  @Test
  void testDecodeSunssfNotImplemented() {
    int[] registers = {0x8000};
    assertNull(SunSpecRegisterDecoder.decodeSunssf(registers, 0));
  }

  // ---- decodeString ----

  @Test
  void testDecodeString() {
    // "Fronius" = 0x46 0x72 0x6F 0x6E 0x69 0x75 0x73 0x00
    int[] registers = {0x4672, 0x6F6E, 0x6975, 0x7300};
    assertEquals("Fronius", SunSpecRegisterDecoder.decodeString(registers, 0, 4));
  }

  @Test
  void testDecodeStringPadded() {
    // "AB" followed by nulls in 4 registers
    int[] registers = {0x4142, 0x0000, 0x0000, 0x0000};
    assertEquals("AB", SunSpecRegisterDecoder.decodeString(registers, 0, 4));
  }

  @Test
  void testDecodeStringEmpty() {
    int[] registers = {0x0000, 0x0000};
    assertEquals("", SunSpecRegisterDecoder.decodeString(registers, 0, 2));
  }

  @Test
  void testDecodeStringFullLength() {
    // "ABCD" exactly fills 2 registers
    int[] registers = {0x4142, 0x4344};
    assertEquals("ABCD", SunSpecRegisterDecoder.decodeString(registers, 0, 2));
  }

  @Test
  void testDecodeStringWithOffset() {
    int[] registers = {0x0000, 0x4142, 0x4344};
    assertEquals("ABCD", SunSpecRegisterDecoder.decodeString(registers, 1, 2));
  }

  // ---- decodeBitfield16 ----

  @Test
  void testDecodeBitfield16() {
    int[] registers = {0x0003};  // bits 0 and 1 set
    assertEquals(3, SunSpecRegisterDecoder.decodeBitfield16(registers, 0));
  }

  @Test
  void testDecodeBitfield16NotImplemented() {
    int[] registers = {0xFFFF};
    assertNull(SunSpecRegisterDecoder.decodeBitfield16(registers, 0));
  }

  // ---- decodeBitfield32 ----

  @Test
  void testDecodeBitfield32() {
    int[] registers = {0x0001, 0x0000};  // bit 16 set
    assertEquals(65536L, SunSpecRegisterDecoder.decodeBitfield32(registers, 0));
  }

  @Test
  void testDecodeBitfield32NotImplemented() {
    int[] registers = {0xFFFF, 0xFFFF};
    assertNull(SunSpecRegisterDecoder.decodeBitfield32(registers, 0));
  }

  // ---- decodeEnum16 ----

  @Test
  void testDecodeEnum16() {
    int[] registers = {4};  // Operating state = 4
    assertEquals(4, SunSpecRegisterDecoder.decodeEnum16(registers, 0));
  }

  @Test
  void testDecodeEnum16NotImplemented() {
    int[] registers = {0xFFFF};
    assertNull(SunSpecRegisterDecoder.decodeEnum16(registers, 0));
  }

  // ---- applyScaleFactor ----

  @Test
  void testApplyScaleFactorNegative() {
    // 2345 * 10^-1 = 234.5
    Double result = SunSpecRegisterDecoder.applyScaleFactor(2345, -1);
    assertEquals(234.5, result, 0.001);
  }

  @Test
  void testApplyScaleFactorZero() {
    // 100 * 10^0 = 100.0
    Double result = SunSpecRegisterDecoder.applyScaleFactor(100, 0);
    assertEquals(100.0, result, 0.001);
  }

  @Test
  void testApplyScaleFactorPositive() {
    // 5 * 10^2 = 500.0
    Double result = SunSpecRegisterDecoder.applyScaleFactor(5, 2);
    assertEquals(500.0, result, 0.001);
  }

  @Test
  void testApplyScaleFactorNullRawValue() {
    assertNull(SunSpecRegisterDecoder.applyScaleFactor(null, -2));
  }

  @Test
  void testApplyScaleFactorNullScaleFactor() {
    assertNull(SunSpecRegisterDecoder.applyScaleFactor(100, null));
  }

  @Test
  void testApplyScaleFactorBothNull() {
    assertNull(SunSpecRegisterDecoder.applyScaleFactor(null, null));
  }

  // ---- Boundary / Error cases ----

  @Test
  void testDecodeUint16NullRegisters() {
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeUint16(null, 0));
  }

  @Test
  void testDecodeUint16OffsetOutOfBounds() {
    int[] registers = {100};
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeUint16(registers, 1));
  }

  @Test
  void testDecodeUint16NegativeOffset() {
    int[] registers = {100};
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeUint16(registers, -1));
  }

  @Test
  void testDecodeUint32OffsetOutOfBounds() {
    int[] registers = {100};
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeUint32(registers, 0));
  }

  @Test
  void testDecodeFloat32OffsetOutOfBounds() {
    int[] registers = {100};
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeFloat32(registers, 0));
  }

  @Test
  void testDecodeAcc64OffsetOutOfBounds() {
    int[] registers = {1, 2, 3};  // Need 4
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeAcc64(registers, 0));
  }

  @Test
  void testDecodeStringOffsetOutOfBounds() {
    int[] registers = {0x4142};
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeString(registers, 0, 2));
  }

  @Test
  void testDecodeStringNullRegisters() {
    assertThrows(IllegalArgumentException.class,
      () -> SunSpecRegisterDecoder.decodeString(null, 0, 1));
  }
}
