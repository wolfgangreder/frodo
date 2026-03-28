package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecDiscoveryResponse} and {@link SunSpecModelResponse} DTOs.
 */
class SunSpecDtoTest {

  // ========== SunSpecDiscoveryResponse ==========

  @Test
  void testDiscoveryResponseFromResult() {
    List<SunSpecModelBlock> blocks = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60),
      new SunSpecModelBlock(120, 40131, 26)
    );
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, blocks);

    SunSpecDiscoveryResponse response = SunSpecDiscoveryResponse.fromResult(42L, 1, result);

    assertEquals(42L, response.deviceId());
    assertEquals(1, response.unitId());
    assertEquals(40000, response.baseAddress());
    assertEquals(3, response.modelCount());
    assertEquals(3, response.models().size());
    assertNotNull(response.discoveryTime());
  }

  @Test
  void testDiscoveryResponseEmptyModels() {
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, List.of());

    SunSpecDiscoveryResponse response = SunSpecDiscoveryResponse.fromResult(1L, 1, result);

    assertEquals(0, response.modelCount());
    assertTrue(response.models().isEmpty());
  }

  @Test
  void testModelSummaryFromBlock() {
    SunSpecModelBlock block = new SunSpecModelBlock(113, 40069, 60);

    SunSpecDiscoveryResponse.ModelSummary summary =
      SunSpecDiscoveryResponse.ModelSummary.fromBlock(block);

    assertEquals(113, summary.modelId());
    assertEquals("Inverter (Three Phase, Float)", summary.name());
    assertEquals(40069, summary.address());
    assertEquals(60, summary.length());
    assertTrue(summary.known());
    assertFalse(summary.hasWritableFields());
  }

  @Test
  void testModelSummaryCommonModel() {
    SunSpecModelBlock block = new SunSpecModelBlock(1, 40002, 65);

    SunSpecDiscoveryResponse.ModelSummary summary =
      SunSpecDiscoveryResponse.ModelSummary.fromBlock(block);

    assertEquals(1, summary.modelId());
    assertEquals("Common", summary.name());
    assertTrue(summary.known());
    assertFalse(summary.hasWritableFields());
  }

  @Test
  void testModelSummaryControlsModelHasWritableFields() {
    SunSpecModelBlock block = new SunSpecModelBlock(
      SunSpecConstants.MODEL_CONTROLS, 40238, 24);

    SunSpecDiscoveryResponse.ModelSummary summary =
      SunSpecDiscoveryResponse.ModelSummary.fromBlock(block);

    assertEquals(123, summary.modelId());
    assertEquals("Immediate Controls", summary.name());
    assertTrue(summary.known());
    assertTrue(summary.hasWritableFields());
  }

  @Test
  void testModelSummaryStorageModelHasWritableFields() {
    SunSpecModelBlock block = new SunSpecModelBlock(
      SunSpecConstants.MODEL_STORAGE, 40264, 24);

    SunSpecDiscoveryResponse.ModelSummary summary =
      SunSpecDiscoveryResponse.ModelSummary.fromBlock(block);

    assertEquals(124, summary.modelId());
    assertTrue(summary.known());
    assertTrue(summary.hasWritableFields());
  }

  @Test
  void testModelSummaryUnknownModel() {
    SunSpecModelBlock block = new SunSpecModelBlock(9999, 40500, 10);

    SunSpecDiscoveryResponse.ModelSummary summary =
      SunSpecDiscoveryResponse.ModelSummary.fromBlock(block);

    assertEquals(9999, summary.modelId());
    assertEquals("Unknown (9999)", summary.name());
    assertFalse(summary.known());
    assertFalse(summary.hasWritableFields());
  }

  @Test
  void testDiscoveryResponseModelOrder() {
    List<SunSpecModelBlock> blocks = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60),
      new SunSpecModelBlock(120, 40131, 26),
      new SunSpecModelBlock(121, 40159, 30),
      new SunSpecModelBlock(122, 40191, 44),
      new SunSpecModelBlock(123, 40237, 24),
      new SunSpecModelBlock(160, 40263, 48)
    );
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, blocks);
    SunSpecDiscoveryResponse response = SunSpecDiscoveryResponse.fromResult(1L, 1, result);

    // Verify order is preserved
    assertEquals(1, response.models().get(0).modelId());
    assertEquals(113, response.models().get(1).modelId());
    assertEquals(120, response.models().get(2).modelId());
    assertEquals(121, response.models().get(3).modelId());
    assertEquals(122, response.models().get(4).modelId());
    assertEquals(123, response.models().get(5).modelId());
    assertEquals(160, response.models().get(6).modelId());
  }

  // ========== SunSpecModelResponse ==========

  @Test
  void testModelResponseFromModelData() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter (Three Phase, Float)", 40070)
      .put("A", 12.5f)
      .put("W", 3500.0f)
      .put("Hz", 50.0f)
      .put("St", 4)
      .build();

    SunSpecModelResponse response = SunSpecModelResponse.fromModelData(42L, 1, data);

    assertEquals(42L, response.deviceId());
    assertEquals(1, response.unitId());
    assertEquals(113, response.modelId());
    assertEquals("Inverter (Three Phase, Float)", response.modelName());
    assertEquals(40070, response.address());
    assertEquals(4, response.fieldCount());
    assertFalse(response.hasWritableFields());
    assertNotNull(response.readTime());
    assertNotNull(response.fields());
  }

  @Test
  void testModelResponseFieldValues() {
    SunSpecModelData data = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .put("Md", "Symo 10.0-3-M")
      .put("SN", "12345678")
      .put("Vr", "1.2.7")
      .build();

    SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);

    assertEquals("Fronius", response.fields().get("Mn"));
    assertEquals("Symo 10.0-3-M", response.fields().get("Md"));
    assertEquals("12345678", response.fields().get("SN"));
    assertEquals("1.2.7", response.fields().get("Vr"));
  }

  @Test
  void testModelResponseNullFieldValues() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .put("AphB", null)
      .put("AphC", null)
      .build();

    SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);

    assertEquals(12.5f, response.fields().get("A"));
    assertNull(response.fields().get("AphB"));
    assertNull(response.fields().get("AphC"));
    assertEquals(3, response.fieldCount());
  }

  @Test
  void testModelResponseControlsHasWritableFields() {
    SunSpecModelData data = SunSpecModelData.builder(123, "Immediate Controls", 40238)
      .put("WMaxLimPct", 100.0f)
      .build();

    SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);

    assertTrue(response.hasWritableFields());
  }

  @Test
  void testModelResponseStorageHasWritableFields() {
    SunSpecModelData data = SunSpecModelData.builder(124, "Basic Storage Controls", 40264)
      .put("ChaState", 50)
      .build();

    SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);

    assertTrue(response.hasWritableFields());
  }

  @Test
  void testModelResponseFromModelDataList() {
    SunSpecModelData common = SunSpecModelData.builder(1, "Common", 40003)
      .put("Mn", "Fronius")
      .build();
    SunSpecModelData inverter = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .build();

    List<SunSpecModelResponse> responses = SunSpecModelResponse.fromModelDataList(
      42L, 1, List.of(common, inverter));

    assertEquals(2, responses.size());
    assertEquals(1, responses.get(0).modelId());
    assertEquals(113, responses.get(1).modelId());
    assertEquals(42L, responses.get(0).deviceId());
    assertEquals(42L, responses.get(1).deviceId());
  }

  @Test
  void testModelResponseFromModelDataListEmpty() {
    List<SunSpecModelResponse> responses = SunSpecModelResponse.fromModelDataList(
      1L, 1, List.of());

    assertTrue(responses.isEmpty());
  }

  @Test
  void testModelResponseFieldOrderPreserved() {
    SunSpecModelData data = SunSpecModelData.builder(113, "Inverter", 40070)
      .put("A", 12.5f)
      .put("AphA", 4.2f)
      .put("AphB", 4.1f)
      .put("AphC", 4.2f)
      .put("W", 3500.0f)
      .put("Hz", 50.0f)
      .build();

    SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);

    // LinkedHashMap preserves insertion order
    List<String> keys = response.fields().keySet().stream().toList();
    assertEquals("A", keys.get(0));
    assertEquals("AphA", keys.get(1));
    assertEquals("AphB", keys.get(2));
    assertEquals("AphC", keys.get(3));
    assertEquals("W", keys.get(4));
    assertEquals("Hz", keys.get(5));
  }

  @Test
  void testModelResponseAllInverterModelIds() {
    // Float models (111-113) should all report hasWritableFields = false
    for (int modelId : new int[]{111, 112, 113}) {
      SunSpecModelData data = SunSpecModelData.builder(modelId, "Inverter", 40070)
        .put("A", 12.5f)
        .build();
      SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);
      assertFalse(response.hasWritableFields(),
        "Float inverter model " + modelId + " should not have writable fields");
    }

    // Int+SF models (101-103) should also report hasWritableFields = false
    for (int modelId : new int[]{101, 102, 103}) {
      SunSpecModelData data = SunSpecModelData.builder(modelId, "Inverter", 40070)
        .put("A", 234.5)
        .build();
      SunSpecModelResponse response = SunSpecModelResponse.fromModelData(1L, 1, data);
      assertFalse(response.hasWritableFields(),
        "Int+SF inverter model " + modelId + " should not have writable fields");
    }
  }
}
