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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecFieldDefinition}.
 */
class SunSpecFieldDefinitionTest {

  @Test
  void testReadOnlyFactory() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "A", 0, 2, SunSpecDataType.FLOAT32, "A", "AC Current");

    assertEquals("A", field.name());
    assertEquals(0, field.offset());
    assertEquals(2, field.size());
    assertEquals(SunSpecDataType.FLOAT32, field.dataType());
    assertEquals("A", field.units());
    assertNull(field.scaleFactor());
    assertFalse(field.writable());
    assertEquals("AC Current", field.description());
  }

  @Test
  void testReadOnlyScaledFactory() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnlyScaled(
      "W", 12, 1, SunSpecDataType.INT16, "W", "W_SF", "AC Power");

    assertEquals("W", field.name());
    assertEquals(12, field.offset());
    assertEquals(1, field.size());
    assertEquals(SunSpecDataType.INT16, field.dataType());
    assertEquals("W", field.units());
    assertEquals("W_SF", field.scaleFactor());
    assertFalse(field.writable());
    assertEquals("AC Power", field.description());
  }

  @Test
  void testWritableFactory() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.writable(
      "Conn", 2, 1, SunSpecDataType.ENUM16, null, null, "Connection control");

    assertEquals("Conn", field.name());
    assertEquals(2, field.offset());
    assertEquals(1, field.size());
    assertEquals(SunSpecDataType.ENUM16, field.dataType());
    assertNull(field.units());
    assertNull(field.scaleFactor());
    assertTrue(field.writable());
    assertEquals("Connection control", field.description());
  }

  @Test
  void testWritableFactoryWithScaleFactor() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.writable(
      "WMaxLimPct", 3, 1, SunSpecDataType.UINT16, "% WMax", "WMaxLimPct_SF", "Power output limit");

    assertTrue(field.writable());
    assertTrue(field.hasScaleFactor());
    assertEquals("WMaxLimPct_SF", field.scaleFactor());
  }

  @Test
  void testHasScaleFactorTrue() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnlyScaled(
      "A", 0, 1, SunSpecDataType.UINT16, "A", "A_SF", "Current");
    assertTrue(field.hasScaleFactor());
  }

  @Test
  void testHasScaleFactorFalseNull() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "St", 46, 1, SunSpecDataType.ENUM16, null, "Operating state");
    assertFalse(field.hasScaleFactor());
  }

  @Test
  void testHasScaleFactorFalseEmpty() {
    SunSpecFieldDefinition field = new SunSpecFieldDefinition(
      "test", 0, 1, SunSpecDataType.UINT16, null, "", false, "test");
    assertFalse(field.hasScaleFactor());
  }

  @Test
  void testReadOnlyNullUnits() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "DA", 64, 1, SunSpecDataType.UINT16, null, "Modbus Device Address");
    assertNull(field.units());
  }

  @Test
  void testStringFieldLargeSize() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "Mn", 0, 16, SunSpecDataType.STRING, null, "Manufacturer");
    assertEquals(16, field.size());
    assertEquals(SunSpecDataType.STRING, field.dataType());
  }
}
