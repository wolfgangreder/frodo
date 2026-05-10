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
import org.junit.jupiter.params.provider.ValueSource;

import static at.or.reder.frodo.modbus.sunspec.SunSpecConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecConstants}.
 */
class SunSpecConstantsTest {

  @Test
  void testSignatureValue() {
    // "SunS" in ASCII: 0x53='S', 0x75='u', 0x6e='n', 0x53='S'
    assertEquals(0x53756e53L, SUNSPEC_SIGNATURE);
  }

  @Test
  void testDefaultBaseAddress() {
    assertEquals(40000, DEFAULT_BASE_ADDRESS);
  }

  @Test
  void testAlternateBaseAddresses() {
    assertEquals(2, ALTERNATE_BASE_ADDRESSES.length);
    assertEquals(0, ALTERNATE_BASE_ADDRESSES[0]);
    assertEquals(50000, ALTERNATE_BASE_ADDRESSES[1]);
  }

  @Test
  void testEndModelId() {
    assertEquals(0xFFFF, END_MODEL_ID);
  }

  @Test
  void testMaxRegistersPerRead() {
    assertEquals(125, MAX_REGISTERS_PER_READ);
  }

  @Test
  void testModelIdConstants() {
    assertEquals(1, MODEL_COMMON);
    assertEquals(101, MODEL_INVERTER_SINGLE_PHASE);
    assertEquals(102, MODEL_INVERTER_SPLIT_PHASE);
    assertEquals(103, MODEL_INVERTER_THREE_PHASE);
    assertEquals(111, MODEL_INVERTER_SINGLE_PHASE_FLOAT);
    assertEquals(112, MODEL_INVERTER_SPLIT_PHASE_FLOAT);
    assertEquals(113, MODEL_INVERTER_THREE_PHASE_FLOAT);
    assertEquals(120, MODEL_NAMEPLATE);
    assertEquals(121, MODEL_SETTINGS);
    assertEquals(122, MODEL_STATUS);
    assertEquals(123, MODEL_CONTROLS);
    assertEquals(124, MODEL_STORAGE);
    assertEquals(160, MODEL_MPPT);
  }

  @ParameterizedTest
  @ValueSource(ints = {111, 112, 113})
  void testIsFloatInverterModel(int modelId) {
    assertTrue(isFloatInverterModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {101, 102, 103})
  void testIsFloatInverterModelReturnsFalseForIntSf(int modelId) {
    assertFalse(isFloatInverterModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 120, 160, 0, 200})
  void testIsFloatInverterModelReturnsFalseForNonInverter(int modelId) {
    assertFalse(isFloatInverterModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {101, 102, 103})
  void testIsIntSfInverterModel(int modelId) {
    assertTrue(isIntSfInverterModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {111, 112, 113})
  void testIsIntSfInverterModelReturnsFalseForFloat(int modelId) {
    assertFalse(isIntSfInverterModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {101, 102, 103, 111, 112, 113})
  void testIsInverterModel(int modelId) {
    assertTrue(isInverterModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 120, 121, 122, 123, 124, 160, 0, 200, 0xFFFF})
  void testIsInverterModelReturnsFalseForNonInverter(int modelId) {
    assertFalse(isInverterModel(modelId));
  }

  @Test
  void testModelNameCommon() {
    assertEquals("Common", modelName(MODEL_COMMON));
  }

  @Test
  void testModelNameInverterIntSf() {
    assertEquals("Inverter (Single Phase, Int+SF)", modelName(MODEL_INVERTER_SINGLE_PHASE));
    assertEquals("Inverter (Split Phase, Int+SF)", modelName(MODEL_INVERTER_SPLIT_PHASE));
    assertEquals("Inverter (Three Phase, Int+SF)", modelName(MODEL_INVERTER_THREE_PHASE));
  }

  @Test
  void testModelNameInverterFloat() {
    assertEquals("Inverter (Single Phase, Float)", modelName(MODEL_INVERTER_SINGLE_PHASE_FLOAT));
    assertEquals("Inverter (Split Phase, Float)", modelName(MODEL_INVERTER_SPLIT_PHASE_FLOAT));
    assertEquals("Inverter (Three Phase, Float)", modelName(MODEL_INVERTER_THREE_PHASE_FLOAT));
  }

  @Test
  void testModelNameOtherModels() {
    assertEquals("Nameplate Ratings", modelName(MODEL_NAMEPLATE));
    assertEquals("Basic Settings", modelName(MODEL_SETTINGS));
    assertEquals("Extended Measurements & Status", modelName(MODEL_STATUS));
    assertEquals("Immediate Controls", modelName(MODEL_CONTROLS));
    assertEquals("Basic Storage Controls", modelName(MODEL_STORAGE));
    assertEquals("Multiple MPPT Inverter Extension", modelName(MODEL_MPPT));
  }

  @Test
  void testModelNameEndBlock() {
    assertEquals("End Block", modelName(END_MODEL_ID));
  }

  @Test
  void testModelNameUnknown() {
    String name = modelName(999);
    assertTrue(name.startsWith("Unknown"));
    assertTrue(name.contains("999"));
  }

  // ========== Solar API sentinel ==========

  @Test
  void solarApiModelId_isNegativeOne() {
    assertEquals(-1, MODEL_ID_SOLAR_API);
  }

  @Test
  void isSolarApiModel_solarApiId_returnsTrue() {
    assertTrue(isSolarApiModel(MODEL_ID_SOLAR_API));
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, -100, Integer.MIN_VALUE})
  void isSolarApiModel_anyNegativeId_returnsTrue(int modelId) {
    assertTrue(isSolarApiModel(modelId));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 101, 111, 201, 0xFFFF})
  void isSolarApiModel_nonNegativeId_returnsFalse(int modelId) {
    assertFalse(isSolarApiModel(modelId));
  }

  @Test
  void modelName_solarApiId_returnsSolarApiSite() {
    assertEquals("Solar API Site", modelName(MODEL_ID_SOLAR_API));
  }
}
