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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecDataType}.
 */
class SunSpecDataTypeTest {

  @Test
  void testRegisterCounts() {
    assertEquals(1, SunSpecDataType.UINT16.getRegisterCount());
    assertEquals(1, SunSpecDataType.INT16.getRegisterCount());
    assertEquals(2, SunSpecDataType.UINT32.getRegisterCount());
    assertEquals(2, SunSpecDataType.INT32.getRegisterCount());
    assertEquals(2, SunSpecDataType.ACC32.getRegisterCount());
    assertEquals(4, SunSpecDataType.ACC64.getRegisterCount());
    assertEquals(2, SunSpecDataType.FLOAT32.getRegisterCount());
    assertEquals(1, SunSpecDataType.ENUM16.getRegisterCount());
    assertEquals(2, SunSpecDataType.ENUM32.getRegisterCount());
    assertEquals(1, SunSpecDataType.BITFIELD16.getRegisterCount());
    assertEquals(2, SunSpecDataType.BITFIELD32.getRegisterCount());
    assertEquals(1, SunSpecDataType.SUNSSF.getRegisterCount());
    assertEquals(0, SunSpecDataType.STRING.getRegisterCount()); // Variable size
    assertEquals(1, SunSpecDataType.PAD.getRegisterCount());
    assertEquals(1, SunSpecDataType.COUNT.getRegisterCount());
  }

  @Test
  void testSignedTypes() {
    assertTrue(SunSpecDataType.INT16.isSigned());
    assertTrue(SunSpecDataType.INT32.isSigned());
    assertTrue(SunSpecDataType.SUNSSF.isSigned());
  }

  @Test
  void testUnsignedTypes() {
    assertFalse(SunSpecDataType.UINT16.isSigned());
    assertFalse(SunSpecDataType.UINT32.isSigned());
    assertFalse(SunSpecDataType.ACC32.isSigned());
    assertFalse(SunSpecDataType.ACC64.isSigned());
    assertFalse(SunSpecDataType.FLOAT32.isSigned());
    assertFalse(SunSpecDataType.ENUM16.isSigned());
    assertFalse(SunSpecDataType.ENUM32.isSigned());
    assertFalse(SunSpecDataType.BITFIELD16.isSigned());
    assertFalse(SunSpecDataType.BITFIELD32.isSigned());
    assertFalse(SunSpecDataType.STRING.isSigned());
    assertFalse(SunSpecDataType.PAD.isSigned());
    assertFalse(SunSpecDataType.COUNT.isSigned());
  }

  @ParameterizedTest
  @CsvSource({
    "uint16, UINT16",
    "int16, INT16",
    "uint32, UINT32",
    "int32, INT32",
    "acc32, ACC32",
    "acc64, ACC64",
    "float32, FLOAT32",
    "enum16, ENUM16",
    "enum32, ENUM32",
    "bitfield16, BITFIELD16",
    "bitfield32, BITFIELD32",
    "sunssf, SUNSSF",
    "string, STRING",
    "pad, PAD",
    "count, COUNT"
  })
  void testFromString(String input, SunSpecDataType expected) {
    assertEquals(expected, SunSpecDataType.fromString(input));
  }

  @ParameterizedTest
  @CsvSource({
    "UINT16, UINT16",
    "Float32, FLOAT32",
    "STRING, STRING",
    "Pad, PAD"
  })
  void testFromStringCaseInsensitive(String input, SunSpecDataType expected) {
    assertEquals(expected, SunSpecDataType.fromString(input));
  }

  @Test
  void testFromStringWithWhitespace() {
    assertEquals(SunSpecDataType.UINT16, SunSpecDataType.fromString("  uint16  "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown", "int8", "float64", ""})
  void testFromStringInvalidThrows(String input) {
    assertThrows(IllegalArgumentException.class, () -> SunSpecDataType.fromString(input));
  }

  @ParameterizedTest
  @NullSource
  void testFromStringNullThrows(String input) {
    assertThrows(IllegalArgumentException.class, () -> SunSpecDataType.fromString(input));
  }
}
