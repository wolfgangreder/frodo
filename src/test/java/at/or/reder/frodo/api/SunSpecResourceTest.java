package at.or.reder.frodo.api;

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

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
  void testDiscoveryReturnsModels() {
    List<SunSpecModelBlock> blocks = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60),
      new SunSpecModelBlock(120, 40131, 26)
    );
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, blocks);

    Mockito.when(sunSpecService.getOrDiscover(UNIT_ID))
      .thenReturn(Uni.createFrom().item(result));

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
  void testDiscoveryRefresh() {
    List<SunSpecModelBlock> blocks = List.of(
      new SunSpecModelBlock(1, 40002, 65)
    );
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, blocks);

    Mockito.when(sunSpecService.getOrDiscover(UNIT_ID))
      .thenReturn(Uni.createFrom().item(result));

    given()
      .queryParam("refresh", "true")
      .when().get("/api/devices/{id}/sunspec/discovery", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelCount", is(1));

    Mockito.verify(sunSpecService).invalidateDiscovery(UNIT_ID);
  }

  @Test
  void testDiscoveryDeviceNotFound() {
    given()
      .when().get("/api/devices/{id}/sunspec/discovery", 999)
      .then()
      .statusCode(404);
  }

  @Test
  void testDiscoveryConnectionFailed() {
    Mockito.when(sunSpecService.getOrDiscover(UNIT_ID))
      .thenReturn(Uni.createFrom().failure(
        new IllegalStateException("SunSpec device not found at any known base address")));

    given()
      .when().get("/api/devices/{id}/sunspec/discovery", DEVICE_ID)
      .then()
      .statusCode(503);
  }

  // ========== Common Model ==========

  @Test
  void testReadCommonModel() {
    SunSpecModelData data = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .put("Md", "Symo 10.0-3-M")
      .put("SN", "12345678")
      .put("Vr", "1.2.7")
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_COMMON))
      .thenReturn(Uni.createFrom().item(data));

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
  void testReadInverterModel() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter (Three Phase, Float)", 40070)
      .put("A", 12.5f)
      .put("W", 3500.0f)
      .put("Hz", 50.0f)
      .put("St", 4)
      .build();

    Mockito.when(sunSpecService.readInverterModel(UNIT_ID))
      .thenReturn(Uni.createFrom().item(data));

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
  void testReadInverterNotFound() {
    Mockito.when(sunSpecService.readInverterModel(UNIT_ID))
      .thenReturn(Uni.createFrom().failure(
        new IllegalArgumentException("No inverter model found on unit 1")));

    given()
      .when().get("/api/devices/{id}/sunspec/inverter", DEVICE_ID)
      .then()
      .statusCode(404);
  }

  // ========== Nameplate Model ==========

  @Test
  void testReadNameplateModel() {
    SunSpecModelData data = SunSpecModelData.builder(120, "Nameplate Ratings", 40132)
      .put("WRtg", 10000.0f)
      .put("VARtg", 10000.0f)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_NAMEPLATE))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/nameplate", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(120))
      .body("fields.WRtg", is(10000.0f));
  }

  // ========== Settings Model ==========

  @Test
  void testReadSettingsModel() {
    SunSpecModelData data = SunSpecModelData.builder(121, "Basic Settings", 40160)
      .put("WMax", 10000.0f)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_SETTINGS))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/settings", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(121));
  }

  // ========== Status Model ==========

  @Test
  void testReadStatusModel() {
    SunSpecModelData data = SunSpecModelData.builder(122, "Extended Measurements & Status", 40192)
      .put("PVV", 450.0f)
      .put("PVA", 7.5f)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_STATUS))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/status", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(122))
      .body("fields.PVV", is(450.0f));
  }

  // ========== Controls Model ==========

  @Test
  void testReadControlsModel() {
    SunSpecModelData data = SunSpecModelData.builder(123, "Immediate Controls", 40238)
      .put("WMaxLimPct", 100.0f)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_CONTROLS))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/controls", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(123))
      .body("hasWritableFields", is(true));
  }

  // ========== Storage Model ==========

  @Test
  void testReadStorageModel() {
    SunSpecModelData data = SunSpecModelData.builder(124, "Basic Storage Controls", 40264)
      .put("ChaState", 50)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_STORAGE))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/storage", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(124))
      .body("hasWritableFields", is(true));
  }

  @Test
  void testReadStorageModelNotPresent() {
    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_STORAGE))
      .thenReturn(Uni.createFrom().failure(
        new IllegalArgumentException("Model 124 not found on unit 1")));

    given()
      .when().get("/api/devices/{id}/sunspec/storage", DEVICE_ID)
      .then()
      .statusCode(404);
  }

  // ========== MPPT Model ==========

  @Test
  void testReadMpptModel() {
    SunSpecModelData data = SunSpecModelData.builder(160, "Multiple MPPT Inverter Extension", 40264)
      .put("DCA_1", 7.5f)
      .put("DCV_1", 450.0f)
      .put("DCW_1", 3375.0f)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, SunSpecConstants.MODEL_MPPT))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/mppt", DEVICE_ID)
      .then()
      .statusCode(200)
      .body("modelId", is(160))
      .body("fields.DCA_1", is(7.5f));
  }

  // ========== Generic Model Reader ==========

  @Test
  void testReadModelById() {
    SunSpecModelData data = SunSpecModelData.builder(120, "Nameplate Ratings", 40132)
      .put("WRtg", 10000.0f)
      .build();

    Mockito.when(sunSpecService.readModel(UNIT_ID, 120))
      .thenReturn(Uni.createFrom().item(data));

    given()
      .when().get("/api/devices/{id}/sunspec/model/{modelId}", DEVICE_ID, 120)
      .then()
      .statusCode(200)
      .body("modelId", is(120));
  }

  @Test
  void testReadModelByIdNotFound() {
    Mockito.when(sunSpecService.readModel(UNIT_ID, 999))
      .thenReturn(Uni.createFrom().failure(
        new IllegalArgumentException("No definition for model 999")));

    given()
      .when().get("/api/devices/{id}/sunspec/model/{modelId}", DEVICE_ID, 999)
      .then()
      .statusCode(404);
  }

  // ========== Read All Models ==========

  @Test
  void testReadAllModels() {
    SunSpecModelData common = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .build();
    SunSpecModelData inverter = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .build();

    Mockito.when(sunSpecService.readAllModels(UNIT_ID))
      .thenReturn(Uni.createFrom().item(List.of(common, inverter)));

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
  void testReadAllModelsEmpty() {
    Mockito.when(sunSpecService.readAllModels(UNIT_ID))
      .thenReturn(Uni.createFrom().item(List.of()));

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
