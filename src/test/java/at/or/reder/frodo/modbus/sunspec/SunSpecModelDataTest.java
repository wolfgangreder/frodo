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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecModelData} and its {@link SunSpecModelData.Builder}.
 */
class SunSpecModelDataTest {

  @Test
  void testBuilderAndBasicProperties() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .put("W", 3500.0f)
      .put("Hz", 50.0f)
      .build();

    assertEquals(113, data.modelId());
    assertEquals("Inverter", data.modelName());
    assertEquals(40070, data.address());
    assertNotNull(data.readTime());
    assertNotNull(data.values());
    assertEquals(3, data.values().size());
  }

  @Test
  void testGetString() {
    SunSpecModelData data = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .put("Md", "Symo 10.0-3-M")
      .put("SN", "12345678")
      .build();

    assertEquals("Fronius", data.getString("Mn"));
    assertEquals("Symo 10.0-3-M", data.getString("Md"));
    assertEquals("12345678", data.getString("SN"));
  }

  @Test
  void testGetStringNull() {
    SunSpecModelData data = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", null)
      .build();

    assertNull(data.getString("Mn"));
    assertNull(data.getString("nonexistent"));
  }

  @Test
  void testGetInt() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("St", 4)
      .put("DA", 1)
      .build();

    assertEquals(4, data.getInt("St"));
    assertEquals(1, data.getInt("DA"));
  }

  @Test
  void testGetIntNull() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("St", null)
      .build();

    assertNull(data.getInt("St"));
    assertNull(data.getInt("nonexistent"));
  }

  @Test
  void testGetFloat() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .put("W", 3500.0f)
      .build();

    assertEquals(12.5f, data.getFloat("A"));
    assertEquals(3500.0f, data.getFloat("W"));
  }

  @Test
  void testGetFloatNull() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", null)
      .build();

    assertNull(data.getFloat("A"));
    assertNull(data.getFloat("nonexistent"));
  }

  @Test
  void testGetLong() {
    SunSpecModelData data = SunSpecModelData.builder(122, "Status", 40192)
      .put("ActWh", 123456789L)
      .build();

    assertEquals(123456789L, data.getLong("ActWh"));
  }

  @Test
  void testGetLongFromInteger() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("St", 4)
      .build();

    // getLong should convert Integer to Long
    assertEquals(4L, data.getLong("St"));
  }

  @Test
  void testGetLongNull() {
    SunSpecModelData data = SunSpecModelData.builder(122, "Status", 40192)
      .put("ActWh", null)
      .build();

    assertNull(data.getLong("ActWh"));
    assertNull(data.getLong("nonexistent"));
  }

  @Test
  void testGetDouble() {
    // Scaled values are stored as Double
    SunSpecModelData data = SunSpecModelData.builder(101, "Inverter", 40070)
      .put("A", 234.5)
      .put("W", 3500.0)
      .build();

    assertEquals(234.5, data.getDouble("A"), 0.001);
    assertEquals(3500.0, data.getDouble("W"), 0.001);
  }

  @Test
  void testGetDoubleFromFloat() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .build();

    assertEquals(12.5, data.getDouble("A"), 0.001);
  }

  @Test
  void testGetDoubleFromInteger() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("St", 4)
      .build();

    assertEquals(4.0, data.getDouble("St"), 0.001);
  }

  @Test
  void testGetDoubleNull() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", null)
      .build();

    assertNull(data.getDouble("A"));
    assertNull(data.getDouble("nonexistent"));
  }

  @Test
  void testHasValueTrue() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .build();

    assertTrue(data.hasValue("A"));
  }

  @Test
  void testHasValueFalseNull() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", null)
      .build();

    assertFalse(data.hasValue("A"));
  }

  @Test
  void testHasValueFalseNotPresent() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .build();

    assertFalse(data.hasValue("A"));
  }

  @Test
  void testGetTyped() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .put("St", 4)
      .put("Mn", "Fronius")
      .build();

    assertEquals(12.5f, data.get("A", Float.class));
    assertEquals(4, data.get("St", Integer.class));
    assertEquals("Fronius", data.get("Mn", String.class));
  }

  @Test
  void testGetTypedNull() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .build();

    assertNull(data.get("A", Float.class));
  }

  @Test
  void testBuilderOverwritesValue() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 10.0f)
      .put("A", 12.5f)
      .build();

    assertEquals(12.5f, data.getFloat("A"));
  }
}
