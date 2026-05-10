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

import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
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
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Integration tests for {@link SunSpecResource} REST endpoints.
 *
 * <p>Uses Quarkus InjectMock to mock the SunSpecService and
 * ModbusDeviceRepository, testing the REST layer in isolation
 * from real Modbus hardware.</p>
 */
@QuarkusTest
class SunSpecResourceTest {

  private static final Long DEVICE_ID = 1L;
  private static final int UNIT_ID = 1;

  @InjectMock
  SunSpecService sunSpecService;

  @InjectMock
  ModbusDeviceRepository deviceRepository;

  private ModbusDeviceEntity testDevice;

  @BeforeEach
  void setup() {
    testDevice = new ModbusDeviceEntity();
    testDevice.id = DEVICE_ID;
    testDevice.name = "Test Inverter";
    testDevice.host = "192.168.1.100";
    testDevice.port = 502;
    testDevice.unitId = UNIT_ID;
    testDevice.enabled = true;

    Mockito.when(deviceRepository.findByIdOptional(DEVICE_ID))
      .thenReturn(Optional.of(testDevice));
    Mockito.when(deviceRepository.findByIdOptional(999L))
      .thenReturn(Optional.empty());
  }

  // ========== Discovery ==========

  @Test
  void testDiscoveryReturnsModels() throws Exception {
    List<SunSpecModelBlock> blocks = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60),
      new SunSpecModelBlock(120, 40131, 26)
    );
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, blocks);

    Mockito.when(sunSpecService.getOrDiscover(any(DeviceAddress.class)))
      .thenReturn(result);

    given()
      .when().get("/api/devices/{id}/sunspec/discovery", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("deviceId", is(DEVICE_ID.intValue()))
      .body("unitId", is(UNIT_ID))
      .body("baseAddress", is(40000))
      .body("modelCount", is(3))
      .body("models", hasSize(3))
      .body("models[0].modelId", is(1))
      .body("models[0].name", is("Common"))
      .body("models[0].known", is(true))
      .body("models[1].modelId", is(113))
      .body("models[2].modelId", is(120))
      .body("discoveryTime", notNullValue());
  }

  @Test
  void testDiscoveryRefresh() throws Exception {
    List<SunSpecModelBlock> blocks = List.of(
      new SunSpecModelBlock(1, 40002, 65)
    );
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, blocks);

    Mockito.when(sunSpecService.getOrDiscover(any(DeviceAddress.class)))
      .thenReturn(result);

    given()
      .queryParam("refresh", "true")
      .when().get("/api/devices/{id}/sunspec/discovery", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelCount", is(1));

    Mockito.verify(sunSpecService).invalidateDiscovery(any(DeviceAddress.class));
  }

  @Test
  void testDiscoveryDeviceNotFound() {
    given()
      .when().get("/api/devices/{id}/sunspec/discovery", 999)
      .then()
      .statusCode(404);
  }

  @Test
  void testDiscoveryConnectionFailed() throws Exception {
    Mockito.when(sunSpecService.getOrDiscover(any(DeviceAddress.class)))
      .thenThrow(new IllegalStateException("SunSpec device not found at any known base address"));

    given()
      .when().get("/api/devices/{id}/sunspec/discovery", DEVICE_ID)
      .then()
      .statusCode(503);
  }

  // ========== Common Model ==========

  @Test
  void testReadCommonModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .put("Md", "Symo 10.0-3-M")
      .put("SN", "12345678")
      .put("Vr", "1.2.7")
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_COMMON)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/common", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(1))
      .body("modelName", is("Common"))
      .body("fields.Mn", is("Fronius"))
      .body("fields.Md", is("Symo 10.0-3-M"))
      .body("fields.SN", is("12345678"))
      .body("fields.Vr", is("1.2.7"))
      .body("hasWritableFields", is(false));
  }

  @Test
  void testReadCommonDeviceNotFound() {
    given()
      .when().get("/api/devices/{id}/sunspec/common", 999)
      .then()
      .statusCode(404);
  }

  // ========== Inverter Model ==========

  @Test
  void testReadInverterModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter (Three Phase, Float)", 40070)
      .put("A", 12.5f)
      .put("W", 3500.0f)
      .put("Hz", 50.0f)
      .put("St", 4)
      .build();

    Mockito.when(sunSpecService.readInverterModel(any(DeviceAddress.class)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/inverter", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(113))
      .body("fields.A", is(12.5f))
      .body("fields.W", is(3500.0f))
      .body("fields.Hz", is(50.0f))
      .body("fields.St", is(4))
      .body("hasWritableFields", is(false));
  }

  @Test
  void testReadInverterNotFound() throws Exception {
    Mockito.when(sunSpecService.readInverterModel(any(DeviceAddress.class)))
      .thenThrow(new IllegalArgumentException("No inverter model found on unit 1"));

    given()
      .when().get("/api/devices/{id}/sunspec/inverter", DEVICE_ID)
      .then()
      .statusCode(404);
  }

  // ========== Nameplate Model ==========

  @Test
  void testReadNameplateModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(120, "Nameplate Ratings", 40132)
      .put("WRtg", 10000.0f)
      .put("VARtg", 10000.0f)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_NAMEPLATE)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/nameplate", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(120))
      .body("fields.WRtg", is(10000.0f));
  }

  // ========== Settings Model ==========

  @Test
  void testReadSettingsModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(121, "Basic Settings", 40160)
      .put("WMax", 10000.0f)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_SETTINGS)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/settings", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(121));
  }

  // ========== Status Model ==========

  @Test
  void testReadStatusModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(122, "Extended Measurements & Status", 40192)
      .put("PVV", 450.0f)
      .put("PVA", 7.5f)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_STATUS)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/status", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(122))
      .body("fields.PVV", is(450.0f));
  }

  // ========== Controls Model ==========

  @Test
  void testReadControlsModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(123, "Immediate Controls", 40238)
      .put("WMaxLimPct", 100.0f)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_CONTROLS)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/controls", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(123))
      .body("hasWritableFields", is(true));
  }

  // ========== Storage Model ==========

  @Test
  void testReadStorageModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(124, "Basic Storage Controls", 40264)
      .put("ChaState", 50)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_STORAGE)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/storage", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(124))
      .body("hasWritableFields", is(true));
  }

  @Test
  void testReadStorageModelNotPresent() throws Exception {
    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_STORAGE)))
      .thenThrow(new IllegalArgumentException("Model 124 not found on unit 1"));

    given()
      .when().get("/api/devices/{id}/sunspec/storage", DEVICE_ID)
      .then()
      .statusCode(404);
  }

  // ========== MPPT Model ==========

  @Test
  void testReadMpptModel() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(160, "Multiple MPPT Inverter Extension", 40264)
      .put("DCA_1", 7.5f)
      .put("DCV_1", 450.0f)
      .put("DCW_1", 3375.0f)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(SunSpecConstants.MODEL_MPPT)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/mppt", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(160))
      .body("fields.DCA_1", is(7.5f));
  }

  // ========== Generic Model Reader ==========

  @Test
  void testReadModelById() throws Exception {
    SunSpecModelData data = SunSpecModelData.builder(120, "Nameplate Ratings", 40132)
      .put("WRtg", 10000.0f)
      .build();

    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(120)))
      .thenReturn(data);

    given()
      .when().get("/api/devices/{id}/sunspec/model/{modelId}", DEVICE_ID, 120)
      .then()
      .statusCode(200)
      .body("modelId", is(120));
  }

  @Test
  void testReadModelByIdNotFound() throws Exception {
    Mockito.when(sunSpecService.readModel(any(DeviceAddress.class), eq(999)))
      .thenThrow(new IllegalArgumentException("No definition for model 999"));

    given()
      .when().get("/api/devices/{id}/sunspec/model/{modelId}", DEVICE_ID, 999)
      .then()
      .statusCode(404);
  }

  // ========== Read All Models ==========

  @Test
  void testReadAllModels() throws Exception {
    SunSpecModelData common = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .build();
    SunSpecModelData inverter = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .build();

    Mockito.when(sunSpecService.readAllModels(any(DeviceAddress.class)))
      .thenReturn(List.of(common, inverter));

    given()
      .when().get("/api/devices/{id}/sunspec/models", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("$", hasSize(2))
      .body("[0].modelId", is(1))
      .body("[0].fields.Mn", is("Fronius"))
      .body("[1].modelId", is(113))
      .body("[1].fields.A", is(12.5f));
  }

  @Test
  void testReadAllModelsEmpty() throws Exception {
    Mockito.when(sunSpecService.readAllModels(any(DeviceAddress.class)))
      .thenReturn(List.of());

    given()
      .when().get("/api/devices/{id}/sunspec/models", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("$", hasSize(0));
  }

  @Test
  void testReadAllModelsDeviceNotFound() {
    given()
      .when().get("/api/devices/{id}/sunspec/models", 999)
      .then()
      .statusCode(404);
  }
}
