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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadDeviceIdCodeTest {

  @Test
  void testBasicCode() {
    assertEquals(0x01, ReadDeviceIdCode.BASIC.getCode());
  }

  @Test
  void testRegularCode() {
    assertEquals(0x02, ReadDeviceIdCode.REGULAR.getCode());
  }

  @Test
  void testExtendedCode() {
    assertEquals(0x03, ReadDeviceIdCode.EXTENDED.getCode());
  }

  @Test
  void testSpecificCode() {
    assertEquals(0x04, ReadDeviceIdCode.SPECIFIC.getCode());
  }

  @Test
  void testFromCodeValid() {
    assertEquals(ReadDeviceIdCode.BASIC, ReadDeviceIdCode.fromCode(0x01));
    assertEquals(ReadDeviceIdCode.REGULAR, ReadDeviceIdCode.fromCode(0x02));
    assertEquals(ReadDeviceIdCode.EXTENDED, ReadDeviceIdCode.fromCode(0x03));
    assertEquals(ReadDeviceIdCode.SPECIFIC, ReadDeviceIdCode.fromCode(0x04));
  }

  @Test
  void testFromCodeInvalid() {
    assertThrows(IllegalArgumentException.class, () -> ReadDeviceIdCode.fromCode(0x00));
    assertThrows(IllegalArgumentException.class, () -> ReadDeviceIdCode.fromCode(0x05));
    assertThrows(IllegalArgumentException.class, () -> ReadDeviceIdCode.fromCode(0xFF));
  }

  @Test
  void testEnumValues() {
    ReadDeviceIdCode[] values = ReadDeviceIdCode.values();
    assertEquals(4, values.length);
  }
}
