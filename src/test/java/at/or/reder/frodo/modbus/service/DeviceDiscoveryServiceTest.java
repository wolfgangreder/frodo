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

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.DeviceType;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DeviceDiscoveryService}.
 *
 * <p>Tests the pure logic methods (unit ID parsing, device type determination)
 * without requiring CDI or Modbus connections.</p>
 */
class DeviceDiscoveryServiceTest {

  private final DeviceDiscoveryService service = new DeviceDiscoveryService();

  // ---- Unit ID Range Parsing ----

  @Test
  void testParseUnitIdRanges_singleValue() {
    List<Integer> ids = service.parseUnitIdRanges("1");
    assertEquals(List.of(1), ids);
  }

  @Test
  void testParseUnitIdRanges_multipleValues() {
    List<Integer> ids = service.parseUnitIdRanges("1,200,201");
    assertEquals(List.of(1, 200, 201), ids);
  }

  @Test
  void testParseUnitIdRanges_range() {
    List<Integer> ids = service.parseUnitIdRanges("200-203");
    assertEquals(List.of(200, 201, 202, 203), ids);
  }

  @Test
  void testParseUnitIdRanges_mixedValuesAndRanges() {
    List<Integer> ids = service.parseUnitIdRanges("1,200-203");
    assertEquals(List.of(1, 200, 201, 202, 203), ids);
  }

  @Test
  void testParseUnitIdRanges_complexMixed() {
    List<Integer> ids = service.parseUnitIdRanges("1-3,10,200-202");
    assertEquals(List.of(1, 2, 3, 10, 200, 201, 202), ids);
  }

  @Test
  void testParseUnitIdRanges_sorted() {
    List<Integer> ids = service.parseUnitIdRanges("200,1,100");
    assertEquals(List.of(1, 100, 200), ids);
  }

  @Test
  void testParseUnitIdRanges_deduplicates() {
    List<Integer> ids = service.parseUnitIdRanges("1,1,1-3");
    assertEquals(List.of(1, 2, 3), ids);
  }

  @Test
  void testParseUnitIdRanges_whitespace() {
    List<Integer> ids = service.parseUnitIdRanges(" 1 , 200 - 203 ");
    assertEquals(List.of(1, 200, 201, 202, 203), ids);
  }

  @Test
  void testParseUnitIdRanges_emptyString() {
    List<Integer> ids = service.parseUnitIdRanges("");
    assertTrue(ids.isEmpty());
  }

  @Test
  void testParseUnitIdRanges_null() {
    List<Integer> ids = service.parseUnitIdRanges(null);
    assertTrue(ids.isEmpty());
  }

  @Test
  void testParseUnitIdRanges_singleRange() {
    List<Integer> ids = service.parseUnitIdRanges("5-5");
    assertEquals(List.of(5), ids);
  }

  @Test
  void testParseUnitIdRanges_maxUnitId() {
    List<Integer> ids = service.parseUnitIdRanges("247");
    assertEquals(List.of(247), ids);
  }

  @Test
  void testParseUnitIdRanges_invalidUnitIdZero() {
    assertThrows(IllegalArgumentException.class, () ->
      service.parseUnitIdRanges("0"));
  }

  @Test
  void testParseUnitIdRanges_invalidUnitIdTooHigh() {
    assertThrows(IllegalArgumentException.class, () ->
      service.parseUnitIdRanges("248"));
  }

  @Test
  void testParseUnitIdRanges_invalidRangeReversed() {
    assertThrows(IllegalArgumentException.class, () ->
      service.parseUnitIdRanges("203-200"));
  }

  @Test
  void testParseUnitIdRanges_invalidNonNumeric() {
    assertThrows(IllegalArgumentException.class, () ->
      service.parseUnitIdRanges("abc"));
  }

  // ---- Device Type Determination (SunSpec) ----

  @Test
  void testDetermineDeviceType_inverterIntSf() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_INVERTER_THREE_PHASE,
      SunSpecConstants.MODEL_NAMEPLATE,
      SunSpecConstants.MODEL_SETTINGS,
      SunSpecConstants.MODEL_STATUS,
      SunSpecConstants.MODEL_CONTROLS,
      SunSpecConstants.MODEL_STORAGE,
      SunSpecConstants.MODEL_MPPT
    );

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_inverterFloat() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_INVERTER_THREE_PHASE_FLOAT
    );

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_inverterSinglePhase() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_INVERTER_SINGLE_PHASE
    );

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_meterIntSf() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_METER_THREE_PHASE_WYE
    );

    assertEquals(DeviceType.SMART_METER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_meterFloat() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_METER_THREE_PHASE_WYE_FLOAT
    );

    assertEquals(DeviceType.SMART_METER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_meterSinglePhase() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_METER_SINGLE_PHASE
    );

    assertEquals(DeviceType.SMART_METER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_meterDelta() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_METER_THREE_PHASE_DELTA_FLOAT
    );

    assertEquals(DeviceType.SMART_METER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_storageOnly() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_STORAGE
    );

    assertEquals(DeviceType.STORAGE, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_mpptFallbackToInverter() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_MPPT
    );

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_commonOnly() {
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON
    );

    assertEquals(DeviceType.UNKNOWN, service.determineDeviceType(discovery));
  }

  @Test
  void testDetermineDeviceType_inverterTakesPrecedenceOverStorage() {
    // When both inverter and storage models are present, inverter wins
    SunSpecDiscoveryResult discovery = createDiscovery(
      SunSpecConstants.MODEL_COMMON,
      SunSpecConstants.MODEL_INVERTER_THREE_PHASE,
      SunSpecConstants.MODEL_STORAGE
    );

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(discovery));
  }

  // ---- Device Type Determination (FC 0x2B) ----

  @Test
  void testDetermineDeviceType_fc2b_froniusOhmpilot() {
    DeviceIdentification id = DeviceIdentification.basic(
      "Fronius International GmbH", "Ohmpilot", "1.2.3", Instant.now());

    assertEquals(DeviceType.OHMPILOT, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_froniusSmartload() {
    DeviceIdentification id = DeviceIdentification.basic(
      "Fronius", "Smartload Controller", "2.0.0", Instant.now());

    assertEquals(DeviceType.OHMPILOT, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_froniusMeter() {
    DeviceIdentification id = DeviceIdentification.basic(
      "Fronius", "Smart Meter TS 65A", "1.0.0", Instant.now());

    assertEquals(DeviceType.SMART_METER, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_froniusGen24() {
    DeviceIdentification id = DeviceIdentification.basic(
      "Fronius", "Gen24 10.0", "1.3.0", Instant.now());

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_froniusPrimo() {
    DeviceIdentification id = DeviceIdentification.basic(
      "Fronius", "Primo 5.0-1", "3.2.1", Instant.now());

    assertEquals(DeviceType.INVERTER, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_unknownVendor() {
    DeviceIdentification id = DeviceIdentification.basic(
      "SomeVendor", "SomeProduct", "1.0.0", Instant.now());

    assertEquals(DeviceType.UNKNOWN, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_nullVendor() {
    DeviceIdentification id = DeviceIdentification.basic(
      null, null, null, Instant.now());

    assertEquals(DeviceType.UNKNOWN, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_froniusUnknownProduct() {
    DeviceIdentification id = DeviceIdentification.basic(
      "Fronius", "FutureDevice X", "1.0.0", Instant.now());

    assertEquals(DeviceType.UNKNOWN, service.determineDeviceType(id));
  }

  @Test
  void testDetermineDeviceType_fc2b_ohmpilotInProductName() {
    DeviceIdentification id = new DeviceIdentification(
      "Fronius", "XYZ", "1.0.0",
      null, "Ohmpilot 2.0", null, null,
      Map.of(), Instant.now());

    assertEquals(DeviceType.OHMPILOT, service.determineDeviceType(id));
  }

  // ---- DiscoveredDevice Record ----

  @Test
  void testDiscoveredDevice_hasUnitId() {
    DiscoveredDevice device = createDevice(1, DeviceType.INVERTER, DiscoveredDevice.SOURCE_SUNSPEC);
    assertTrue(device.hasUnitId());
  }

  @Test
  void testDiscoveredDevice_noUnitId() {
    DiscoveredDevice device = createDevice(-1, DeviceType.OHMPILOT, DiscoveredDevice.SOURCE_SOLAR_API);
    assertFalse(device.hasUnitId());
  }

  @Test
  void testDiscoveredDevice_hasSunSpec() {
    DiscoveredDevice device = new DiscoveredDevice(
      "192.168.1.160", 502, 1, DeviceType.INVERTER,
      "Fronius", "Gen24", "12345", "1.0.0",
      List.of(1, 113, 120), DiscoveredDevice.SOURCE_SUNSPEC);

    assertTrue(device.hasSunSpec());
  }

  @Test
  void testDiscoveredDevice_noSunSpec() {
    DiscoveredDevice device = createDevice(-1, DeviceType.OHMPILOT, DiscoveredDevice.SOURCE_SOLAR_API);
    assertFalse(device.hasSunSpec());
  }

  @Test
  void testDiscoveredDevice_connectionString_withUnitId() {
    DiscoveredDevice device = createDevice(200, DeviceType.SMART_METER, DiscoveredDevice.SOURCE_SUNSPEC);
    assertEquals("192.168.1.160:502/200", device.connectionString());
  }

  @Test
  void testDiscoveredDevice_connectionString_noUnitId() {
    DiscoveredDevice device = createDevice(-1, DeviceType.OHMPILOT, DiscoveredDevice.SOURCE_SOLAR_API);
    assertEquals("192.168.1.160:502/solar-api", device.connectionString());
  }

  @Test
  void testDiscoveredDevice_suggestedName_withManufacturer() {
    DiscoveredDevice device = new DiscoveredDevice(
      "192.168.1.160", 502, 1, DeviceType.INVERTER,
      "Fronius", "Gen24 10.0", null, null,
      List.of(), DiscoveredDevice.SOURCE_SUNSPEC);

    assertEquals("Fronius Gen24 10.0", device.suggestedName());
  }

  @Test
  void testDiscoveredDevice_suggestedName_noManufacturer_withUnitId() {
    DiscoveredDevice device = new DiscoveredDevice(
      "192.168.1.160", 502, 200, DeviceType.SMART_METER,
      null, null, null, null,
      List.of(), DiscoveredDevice.SOURCE_SUNSPEC);

    assertEquals("Smart Meter (Unit 200)", device.suggestedName());
  }

  @Test
  void testDiscoveredDevice_suggestedName_noManufacturer_noUnitId() {
    DiscoveredDevice device = createDevice(-1, DeviceType.OHMPILOT, DiscoveredDevice.SOURCE_SOLAR_API);
    assertEquals("Ohmpilot", device.suggestedName());
  }

  @Test
  void testDiscoveredDevice_sourceConstants() {
    assertEquals("sunspec", DiscoveredDevice.SOURCE_SUNSPEC);
    assertEquals("modbus-fc2b", DiscoveredDevice.SOURCE_MODBUS_FC2B);
    assertEquals("solar-api", DiscoveredDevice.SOURCE_SOLAR_API);
  }

  // ---- SunSpecDiscoveryResult.hasAnyModel ----

  @Test
  void testHasAnyModel_found() {
    SunSpecDiscoveryResult discovery = createDiscovery(1, 113, 120);
    assertTrue(discovery.hasAnyModel(113, 203));
  }

  @Test
  void testHasAnyModel_notFound() {
    SunSpecDiscoveryResult discovery = createDiscovery(1, 113, 120);
    assertFalse(discovery.hasAnyModel(201, 202, 203));
  }

  @Test
  void testHasAnyModel_empty() {
    SunSpecDiscoveryResult discovery = createDiscovery();
    assertFalse(discovery.hasAnyModel(1, 113));
  }

  @Test
  void testModelIds() {
    SunSpecDiscoveryResult discovery = createDiscovery(1, 113, 120, 160);
    List<Integer> ids = discovery.modelIds();
    assertEquals(List.of(1, 113, 120, 160), ids);
  }

  @Test
  void testModelIds_empty() {
    SunSpecDiscoveryResult discovery = createDiscovery();
    assertTrue(discovery.modelIds().isEmpty());
  }

  // ---- Helper methods ----

  private SunSpecDiscoveryResult createDiscovery(int... modelIds) {
    List<SunSpecModelBlock> blocks = new java.util.ArrayList<>();
    int address = 40002;
    for (int modelId : modelIds) {
      blocks.add(new SunSpecModelBlock(modelId, address, 50));
      address += 52;
    }
    return SunSpecDiscoveryResult.of(40000, blocks);
  }

  private DiscoveredDevice createDevice(int unitId, DeviceType type, String source) {
    return new DiscoveredDevice(
      "192.168.1.160", 502, unitId, type,
      null, null, null, null,
      List.of(), source);
  }
}
