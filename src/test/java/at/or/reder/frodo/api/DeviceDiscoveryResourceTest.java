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

package at.or.reder.frodo.api;

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.model.DeviceType;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.service.DeviceDiscoveryService;
import at.or.reder.frodo.modbus.service.DiscoveredDevice;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * Integration tests for {@link DeviceDiscoveryResource} REST endpoints.
 *
 * <p>Uses Quarkus InjectMock to mock the DeviceDiscoveryService and
 * ModbusDeviceRepository, testing the REST layer in isolation
 * from real Modbus hardware and Solar API.</p>
 */
@QuarkusTest
class DeviceDiscoveryResourceTest {

  private static final Long PARENT_DEVICE_ID = 1L;
  private static final String TEST_HOST = "192.168.1.160";
  private static final int TEST_PORT = 502;

  @InjectMock
  DeviceDiscoveryService discoveryService;

  @InjectMock
  ModbusDeviceRepository deviceRepository;

  private ModbusDeviceEntity parentDevice;

  @BeforeEach
  void setup() {
    parentDevice = new ModbusDeviceEntity();
    parentDevice.id = PARENT_DEVICE_ID;
    parentDevice.name = "Test Inverter";
    parentDevice.host = TEST_HOST;
    parentDevice.port = TEST_PORT;
    parentDevice.unitId = 1;
    parentDevice.enabled = true;
    parentDevice.deviceType = DeviceType.INVERTER;

    Mockito.when(deviceRepository.findByIdOptional(PARENT_DEVICE_ID))
      .thenReturn(Optional.of(parentDevice));
    Mockito.when(deviceRepository.findByIdOptional(999L))
      .thenReturn(Optional.empty());
  }

  // ========== POST /devices/discover ==========

  @Test
  void testDiscoverDevices_FindsInverterAndMeter() {
    List<DiscoveredDevice> discovered = List.of(
      new DiscoveredDevice(TEST_HOST, TEST_PORT, 1, DeviceType.INVERTER,
        "Fronius", "Symo 10.0-3-M", "12345678", "1.2.7",
        List.of(1, 113, 120, 160), DiscoveredDevice.SOURCE_SUNSPEC),
      new DiscoveredDevice(TEST_HOST, TEST_PORT, 200, DeviceType.SMART_METER,
        "Fronius", "Smart Meter TS", "87654321", null,
        List.of(1, 213), DiscoveredDevice.SOURCE_SUNSPEC)
    );

    Mockito.when(discoveryService.discoverDevices(TEST_HOST, TEST_PORT))
      .thenReturn(discovered);

    given()
      .contentType("application/json")
      .body("""
        {"host": "%s", "port": %d}
        """.formatted(TEST_HOST, TEST_PORT))
      .when().post("/api/devices/discover")
      .then()
      .statusCode(200)
      .body("host", is(TEST_HOST))
      .body("port", is(TEST_PORT))
      .body("devicesFound", is(2))
      .body("devices", hasSize(2))
      .body("devices[0].deviceType", is("INVERTER"))
      .body("devices[0].manufacturer", is("Fronius"))
      .body("devices[0].model", is("Symo 10.0-3-M"))
      .body("devices[0].serialNumber", is("12345678"))
      .body("devices[0].source", is("sunspec"))
      .body("devices[0].hasSunSpec", is(true))
      .body("devices[1].deviceType", is("SMART_METER"))
      .body("devices[1].unitId", is(200))
      .body("savedDeviceIds", empty());
  }

  @Test
  void testDiscoverDevices_NoDevicesFound() {
    Mockito.when(discoveryService.discoverDevices(TEST_HOST, TEST_PORT))
      .thenReturn(List.of());

    given()
      .contentType("application/json")
      .body("""
        {"host": "%s", "port": %d}
        """.formatted(TEST_HOST, TEST_PORT))
      .when().post("/api/devices/discover")
      .then()
      .statusCode(200)
      .body("devicesFound", is(0))
      .body("devices", empty())
      .body("savedDeviceIds", empty());
  }

  @Test
  void testDiscoverDevices_WithCustomUnitIdRanges() {
    List<DiscoveredDevice> discovered = List.of(
      new DiscoveredDevice(TEST_HOST, TEST_PORT, 1, DeviceType.INVERTER,
        "Fronius", "Gen24", null, null,
        List.of(1, 113), DiscoveredDevice.SOURCE_SUNSPEC)
    );

    Mockito.when(discoveryService.discoverDevices(TEST_HOST, TEST_PORT, "1,200-202"))
      .thenReturn(discovered);

    given()
      .contentType("application/json")
      .body("""
        {"host": "%s", "port": %d, "unitIdRanges": "1,200-202"}
        """.formatted(TEST_HOST, TEST_PORT))
      .when().post("/api/devices/discover")
      .then()
      .statusCode(200)
      .body("devicesFound", is(1))
      .body("devices[0].deviceType", is("INVERTER"));

    Mockito.verify(discoveryService)
      .discoverDevices(TEST_HOST, TEST_PORT, "1,200-202");
  }

  @Test
  void testDiscoverDevices_WithAutoSave() {
    List<DiscoveredDevice> discovered = List.of(
      new DiscoveredDevice(TEST_HOST, TEST_PORT, 1, DeviceType.INVERTER,
        "Fronius", "Symo", "SN1", null,
        List.of(1, 113), DiscoveredDevice.SOURCE_SUNSPEC)
    );

    ModbusDeviceEntity savedEntity = new ModbusDeviceEntity();
    savedEntity.id = 42L;

    Mockito.when(discoveryService.discoverDevices(TEST_HOST, TEST_PORT))
      .thenReturn(discovered);
    Mockito.when(discoveryService.saveDiscoveredDevices(isNull(), eq(discovered)))
      .thenReturn(List.of(savedEntity));

    given()
      .contentType("application/json")
      .body("""
        {"host": "%s", "port": %d, "autoSave": true}
        """.formatted(TEST_HOST, TEST_PORT))
      .when().post("/api/devices/discover")
      .then()
      .statusCode(200)
      .body("devicesFound", is(1))
      .body("savedDeviceIds", hasSize(1))
      .body("savedDeviceIds[0]", is(42));

    Mockito.verify(discoveryService).saveDiscoveredDevices(isNull(), eq(discovered));
  }

  @Test
  void testDiscoverDevices_DefaultPort() {
    Mockito.when(discoveryService.discoverDevices(TEST_HOST, 502))
      .thenReturn(List.of());

    given()
      .contentType("application/json")
      .body("""
        {"host": "%s"}
        """.formatted(TEST_HOST))
      .when().post("/api/devices/discover")
      .then()
      .statusCode(200)
      .body("port", is(502));
  }

  @Test
  void testDiscoverDevices_MissingHost() {
    given()
      .contentType("application/json")
      .body("""
        {"port": 502}
        """)
      .when().post("/api/devices/discover")
      .then()
      .statusCode(400);
  }

  @Test
  void testDiscoverDevices_OhmpilotViaSolarApi() {
    List<DiscoveredDevice> discovered = List.of(
      new DiscoveredDevice(TEST_HOST, 80, -1, DeviceType.OHMPILOT,
        "Fronius", "Ohmpilot", "0", null,
        List.of(), DiscoveredDevice.SOURCE_SOLAR_API)
    );

    Mockito.when(discoveryService.discoverDevices(TEST_HOST, TEST_PORT))
      .thenReturn(discovered);

    given()
      .contentType("application/json")
      .body("""
        {"host": "%s", "port": %d}
        """.formatted(TEST_HOST, TEST_PORT))
      .when().post("/api/devices/discover")
      .then()
      .statusCode(200)
      .body("devicesFound", is(1))
      .body("devices[0].deviceType", is("OHMPILOT"))
      .body("devices[0].source", is("solar-api"))
      .body("devices[0].hasSunSpec", is(false));
  }

  // ========== POST /devices/{id}/discover-sub-devices ==========

  @Test
  void testDiscoverSubDevices_Success() {
    List<DiscoveredDevice> discovered = List.of(
      new DiscoveredDevice(TEST_HOST, TEST_PORT, 200, DeviceType.SMART_METER,
        "Fronius", "Smart Meter", "SM001", null,
        List.of(1, 213), DiscoveredDevice.SOURCE_SUNSPEC)
    );

    ModbusDeviceEntity savedEntity = new ModbusDeviceEntity();
    savedEntity.id = 10L;

    Mockito.when(discoveryService.discoverDevices(TEST_HOST, TEST_PORT))
      .thenReturn(discovered);
    Mockito.when(discoveryService.saveDiscoveredDevices(eq(PARENT_DEVICE_ID), eq(discovered)))
      .thenReturn(List.of(savedEntity));

    given()
      .contentType("application/json")
      .when().post("/api/devices/{id}/discover-sub-devices", PARENT_DEVICE_ID)
      .then()
      .statusCode(200)
      .body("host", is(TEST_HOST))
      .body("port", is(TEST_PORT))
      .body("devicesFound", is(1))
      .body("devices[0].deviceType", is("SMART_METER"))
      .body("savedDeviceIds", hasSize(1))
      .body("savedDeviceIds[0]", is(10));

    Mockito.verify(discoveryService).saveDiscoveredDevices(eq(PARENT_DEVICE_ID), eq(discovered));
  }

  @Test
  void testDiscoverSubDevices_ParentNotFound() {
    given()
      .contentType("application/json")
      .when().post("/api/devices/{id}/discover-sub-devices", 999)
      .then()
      .statusCode(404);
  }

  // ========== GET /devices/{id}/sub-devices ==========

  @Test
  void testGetSubDevices_ReturnsChildren() {
    ModbusDeviceEntity child1 = new ModbusDeviceEntity();
    child1.id = 10L;
    child1.name = "Smart Meter 1";
    child1.host = TEST_HOST;
    child1.port = TEST_PORT;
    child1.unitId = 200;
    child1.enabled = true;
    child1.deviceType = DeviceType.SMART_METER;
    child1.autoDiscovered = true;
    child1.parentDevice = parentDevice;

    ModbusDeviceEntity child2 = new ModbusDeviceEntity();
    child2.id = 11L;
    child2.name = "Smart Meter 2";
    child2.host = TEST_HOST;
    child2.port = TEST_PORT;
    child2.unitId = 201;
    child2.enabled = true;
    child2.deviceType = DeviceType.SMART_METER;
    child2.autoDiscovered = true;
    child2.parentDevice = parentDevice;

    Mockito.when(deviceRepository.listChildDevices(PARENT_DEVICE_ID))
      .thenReturn(List.of(child1, child2));

    given()
      .when().get("/api/devices/{id}/sub-devices", PARENT_DEVICE_ID)
      .then()
      .statusCode(200)
      .body("total", is(2))
      .body("devices", hasSize(2))
      .body("devices[0].id", is(10))
      .body("devices[0].name", is("Smart Meter 1"))
      .body("devices[0].unitId", is(200))
      .body("devices[0].deviceType", is("SMART_METER"))
      .body("devices[0].autoDiscovered", is(true))
      .body("devices[0].parentDeviceId", is(PARENT_DEVICE_ID.intValue()))
      .body("devices[1].id", is(11))
      .body("devices[1].unitId", is(201));
  }

  @Test
  void testGetSubDevices_Empty() {
    Mockito.when(deviceRepository.listChildDevices(PARENT_DEVICE_ID))
      .thenReturn(List.of());

    given()
      .when().get("/api/devices/{id}/sub-devices", PARENT_DEVICE_ID)
      .then()
      .statusCode(200)
      .body("total", is(0))
      .body("devices", empty());
  }

  @Test
  void testGetSubDevices_ParentNotFound() {
    given()
      .when().get("/api/devices/{id}/sub-devices", 999)
      .then()
      .statusCode(404);
  }
}
