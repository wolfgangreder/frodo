package at.or.reder.frodo.modbus.sunspec;

import org.junit.jupiter.api.Test;

import static at.or.reder.frodo.modbus.sunspec.SunSpecConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for SunSpec Meter models (201-204 Int+SF, 211-214 Float).
 *
 * <p>Verifies correct decoding of Single Phase, Split Phase, Three Phase WYE,
 * and Three Phase Delta meter models in both Int+SF and Float variants.</p>
 */
class SunSpecMeterModelTest {

  // ---- Meter Float Model (211) - Single Phase ----

  @Test
  void testDecodeMeterSinglePhaseFloatModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_METER_SINGLE_PHASE_FLOAT);
    int[] registers = new int[124];

    // A at offset 0: 10.5 A
    encodeFloat32(registers, 0, 10.5f);

    // AphA at offset 2: 10.5 A (same as total for single phase)
    encodeFloat32(registers, 2, 10.5f);

    // PhV at offset 8: 230.0 V
    encodeFloat32(registers, 8, 230.0f);

    // PhVphA at offset 10: 230.0 V
    encodeFloat32(registers, 10, 230.0f);

    // Hz at offset 24: 50.0 Hz
    encodeFloat32(registers, 24, 50.0f);

    // W at offset 26: 2415.0 W
    encodeFloat32(registers, 26, 2415.0f);

    // VA at offset 34: 2415.0 VA
    encodeFloat32(registers, 34, 2415.0f);

    // VAR at offset 42: 0.0 VAR
    encodeFloat32(registers, 42, 0.0f);

    // PF at offset 50: 1.0
    encodeFloat32(registers, 50, 1.0f);

    // TotWhExp at offset 58: 123456.0 Wh
    encodeFloat32(registers, 58, 123456.0f);

    // TotWhImp at offset 66: 654321.0 Wh
    encodeFloat32(registers, 66, 654321.0f);

    // Evt at offset 122: no events
    registers[122] = 0;
    registers[123] = 0;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40121);

    assertEquals(MODEL_METER_SINGLE_PHASE_FLOAT, data.modelId());
    assertEquals("Meter (Single Phase AN/AB, Float)", data.modelName());
    assertEquals(10.5f, data.getFloat("A"));
    assertEquals(10.5f, data.getFloat("AphA"));
    assertEquals(230.0f, data.getFloat("PhV"));
    assertEquals(230.0f, data.getFloat("PhVphA"));
    assertEquals(50.0f, data.getFloat("Hz"));
    assertEquals(2415.0f, data.getFloat("W"));
    assertEquals(2415.0f, data.getFloat("VA"));
    assertEquals(0.0f, data.getFloat("VAR"));
    assertEquals(1.0f, data.getFloat("PF"));
    assertEquals(123456.0f, data.getFloat("TotWhExp"));
    assertEquals(654321.0f, data.getFloat("TotWhImp"));
  }

  // ---- Meter Int+SF Model (201) - Single Phase with Scale Factors ----

  @Test
  void testDecodeMeterSinglePhaseIntSfModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_METER_SINGLE_PHASE);
    int[] registers = new int[105];

    // A at offset 0: raw=1050, A_SF at offset 4: -2 -> 1050 * 10^-2 = 10.5 A
    registers[0] = encodeInt16(1050);
    registers[4] = encodeInt16(-2);  // A_SF

    // AphA at offset 1: raw=1050, uses A_SF
    registers[1] = encodeInt16(1050);

    // PhV at offset 5: raw=2300, V_SF at offset 13: -1 -> 2300 * 10^-1 = 230.0 V
    registers[5] = encodeInt16(2300);
    registers[13] = encodeInt16(-1);  // V_SF

    // PhVphA at offset 6: raw=2300, uses V_SF
    registers[6] = encodeInt16(2300);

    // Hz at offset 14: raw=5000, Hz_SF at offset 15: -2 -> 5000 * 10^-2 = 50.0 Hz
    registers[14] = encodeInt16(5000);
    registers[15] = encodeInt16(-2);  // Hz_SF

    // W at offset 16: raw=2415, W_SF at offset 20: 0 -> 2415 W
    registers[16] = encodeInt16(2415);
    registers[20] = encodeInt16(0);  // W_SF

    // VA at offset 21: raw=2415, VA_SF at offset 25: 0 -> 2415 VA
    registers[21] = encodeInt16(2415);
    registers[25] = encodeInt16(0);  // VA_SF

    // VAR at offset 26: raw=0, VAR_SF at offset 30: 0 -> 0 VAR
    registers[26] = encodeInt16(0);
    registers[30] = encodeInt16(0);  // VAR_SF

    // PF at offset 31: raw=100, PF_SF at offset 35: -2 -> 100 * 10^-2 = 1.0
    registers[31] = encodeInt16(100);
    registers[35] = encodeInt16(-2);  // PF_SF

    // TotWhExp at offset 36 (acc32): 123456 Wh, TotWh_SF at offset 52: 0
    registers[36] = 0x0001;  // high word
    registers[37] = 0xE240;  // low word = 123456
    registers[52] = encodeInt16(0);  // TotWh_SF

    // Evt at offset 103: no events
    registers[103] = 0;
    registers[104] = 0;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40121);

    assertEquals(MODEL_METER_SINGLE_PHASE, data.modelId());
    assertEquals("Meter (Single Phase AN/AB, Int+SF)", data.modelName());
    assertEquals(10.5, data.getDouble("A"), 0.01);
    assertEquals(10.5, data.getDouble("AphA"), 0.01);
    assertEquals(230.0, data.getDouble("PhV"), 0.01);
    assertEquals(230.0, data.getDouble("PhVphA"), 0.01);
    assertEquals(50.0, data.getDouble("Hz"), 0.01);
    assertEquals(2415.0, data.getDouble("W"), 0.01);
    assertEquals(2415.0, data.getDouble("VA"), 0.01);
    assertEquals(0.0, data.getDouble("VAR"), 0.01);
    assertEquals(1.0, data.getDouble("PF"), 0.01);
  }

  // ---- Meter Float Model (213) - Three Phase WYE ----

  @Test
  void testDecodeMeterThreePhaseWyeFloatModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_WYE_FLOAT);
    int[] registers = new int[124];

    // Total current A at offset 0: 30.0 A
    encodeFloat32(registers, 0, 30.0f);

    // AphA at offset 2: 10.0 A
    encodeFloat32(registers, 2, 10.0f);

    // AphB at offset 4: 10.0 A
    encodeFloat32(registers, 4, 10.0f);

    // AphC at offset 6: 10.0 A
    encodeFloat32(registers, 6, 10.0f);

    // PhV at offset 8: 230.0 V (average)
    encodeFloat32(registers, 8, 230.0f);

    // PhVphA at offset 10: 230.0 V
    encodeFloat32(registers, 10, 230.0f);

    // PhVphB at offset 12: 230.0 V
    encodeFloat32(registers, 12, 230.0f);

    // PhVphC at offset 14: 230.0 V
    encodeFloat32(registers, 14, 230.0f);

    // PPV at offset 16: 398.4 V (line-to-line average)
    encodeFloat32(registers, 16, 398.4f);

    // PPVphAB at offset 18: 398.4 V
    encodeFloat32(registers, 18, 398.4f);

    // PPVphBC at offset 20: 398.4 V
    encodeFloat32(registers, 20, 398.4f);

    // PPVphCA at offset 22: 398.4 V
    encodeFloat32(registers, 22, 398.4f);

    // Hz at offset 24: 50.0 Hz
    encodeFloat32(registers, 24, 50.0f);

    // W at offset 26: 6900.0 W (3 phases)
    encodeFloat32(registers, 26, 6900.0f);

    // VA at offset 34: 6900.0 VA
    encodeFloat32(registers, 34, 6900.0f);

    // VAR at offset 42: 0.0 VAR
    encodeFloat32(registers, 42, 0.0f);

    // PF at offset 50: 1.0
    encodeFloat32(registers, 50, 1.0f);

    // TotWhExp at offset 58: 500000.0 Wh
    encodeFloat32(registers, 58, 500000.0f);

    // TotWhImp at offset 66: 100000.0 Wh
    encodeFloat32(registers, 66, 100000.0f);

    // Evt at offset 122: no events
    registers[122] = 0;
    registers[123] = 0;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40121);

    assertEquals(MODEL_METER_THREE_PHASE_WYE_FLOAT, data.modelId());
    assertEquals("Meter (Three Phase WYE, Float)", data.modelName());
    assertEquals(30.0f, data.getFloat("A"));
    assertEquals(10.0f, data.getFloat("AphA"));
    assertEquals(10.0f, data.getFloat("AphB"));
    assertEquals(10.0f, data.getFloat("AphC"));
    assertEquals(230.0f, data.getFloat("PhV"));
    assertEquals(230.0f, data.getFloat("PhVphA"));
    assertEquals(230.0f, data.getFloat("PhVphB"));
    assertEquals(230.0f, data.getFloat("PhVphC"));
    assertEquals(398.4f, data.getFloat("PPV"));
    assertEquals(398.4f, data.getFloat("PPVphAB"));
    assertEquals(398.4f, data.getFloat("PPVphBC"));
    assertEquals(398.4f, data.getFloat("PPVphCA"));
    assertEquals(50.0f, data.getFloat("Hz"));
    assertEquals(6900.0f, data.getFloat("W"));
    assertEquals(6900.0f, data.getFloat("VA"));
    assertEquals(0.0f, data.getFloat("VAR"));
    assertEquals(1.0f, data.getFloat("PF"));
    assertEquals(500000.0f, data.getFloat("TotWhExp"));
    assertEquals(100000.0f, data.getFloat("TotWhImp"));
  }

  // ---- Meter Int+SF Model (203) - Three Phase WYE with Scale Factors ----

  @Test
  void testDecodeMeterThreePhaseWyeIntSfModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_WYE);
    int[] registers = new int[105];

    // A at offset 0: raw=3000, A_SF at offset 4: -2 -> 3000 * 10^-2 = 30.0 A
    registers[0] = encodeInt16(3000);
    registers[4] = encodeInt16(-2);  // A_SF

    // AphA at offset 1: raw=1000, uses A_SF -> 1000 * 10^-2 = 10.0 A
    registers[1] = encodeInt16(1000);

    // AphB at offset 2: raw=1000, uses A_SF
    registers[2] = encodeInt16(1000);

    // AphC at offset 3: raw=1000, uses A_SF
    registers[3] = encodeInt16(1000);

    // PhV at offset 5: raw=2300, V_SF at offset 13: -1 -> 2300 * 10^-1 = 230.0 V
    registers[5] = encodeInt16(2300);
    registers[13] = encodeInt16(-1);  // V_SF

    // PhVphA at offset 6: raw=2300, uses V_SF
    registers[6] = encodeInt16(2300);

    // PhVphB at offset 7: raw=2300, uses V_SF
    registers[7] = encodeInt16(2300);

    // PhVphC at offset 8: raw=2300, uses V_SF
    registers[8] = encodeInt16(2300);

    // Hz at offset 14: raw=5000, Hz_SF at offset 15: -2 -> 5000 * 10^-2 = 50.0 Hz
    registers[14] = encodeInt16(5000);
    registers[15] = encodeInt16(-2);  // Hz_SF

    // W at offset 16: raw=6900, W_SF at offset 20: 0 -> 6900 W
    registers[16] = encodeInt16(6900);
    registers[20] = encodeInt16(0);  // W_SF

    // Evt at offset 103: no events
    registers[103] = 0;
    registers[104] = 0;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40121);

    assertEquals(MODEL_METER_THREE_PHASE_WYE, data.modelId());
    assertEquals("Meter (Three Phase WYE, Int+SF)", data.modelName());
    assertEquals(30.0, data.getDouble("A"), 0.01);
    assertEquals(10.0, data.getDouble("AphA"), 0.01);
    assertEquals(10.0, data.getDouble("AphB"), 0.01);
    assertEquals(10.0, data.getDouble("AphC"), 0.01);
    assertEquals(230.0, data.getDouble("PhV"), 0.01);
    assertEquals(230.0, data.getDouble("PhVphA"), 0.01);
    assertEquals(230.0, data.getDouble("PhVphB"), 0.01);
    assertEquals(230.0, data.getDouble("PhVphC"), 0.01);
    assertEquals(50.0, data.getDouble("Hz"), 0.01);
    assertEquals(6900.0, data.getDouble("W"), 0.01);
  }

  // ---- Meter Float Model (214) - Three Phase Delta ----

  @Test
  void testDecodeMeterThreePhaseDeltaFloatModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_DELTA_FLOAT);
    int[] registers = new int[124];

    // Total current A at offset 0: 30.0 A
    encodeFloat32(registers, 0, 30.0f);

    // AphA at offset 2: 10.0 A
    encodeFloat32(registers, 2, 10.0f);

    // AphB at offset 4: 10.0 A
    encodeFloat32(registers, 4, 10.0f);

    // AphC at offset 6: 10.0 A
    encodeFloat32(registers, 6, 10.0f);

    // PPV at offset 16: 400.0 V (line-to-line average, no neutral)
    encodeFloat32(registers, 16, 400.0f);

    // PPVphAB at offset 18: 400.0 V
    encodeFloat32(registers, 18, 400.0f);

    // PPVphBC at offset 20: 400.0 V
    encodeFloat32(registers, 20, 400.0f);

    // PPVphCA at offset 22: 400.0 V
    encodeFloat32(registers, 22, 400.0f);

    // Hz at offset 24: 50.0 Hz
    encodeFloat32(registers, 24, 50.0f);

    // W at offset 26: 12000.0 W (3 phases)
    encodeFloat32(registers, 26, 12000.0f);

    // Evt at offset 122: no events
    registers[122] = 0;
    registers[123] = 0;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40121);

    assertEquals(MODEL_METER_THREE_PHASE_DELTA_FLOAT, data.modelId());
    assertEquals("Meter (Three Phase Delta, Float)", data.modelName());
    assertEquals(30.0f, data.getFloat("A"));
    assertEquals(10.0f, data.getFloat("AphA"));
    assertEquals(10.0f, data.getFloat("AphB"));
    assertEquals(10.0f, data.getFloat("AphC"));
    assertEquals(400.0f, data.getFloat("PPV"));
    assertEquals(400.0f, data.getFloat("PPVphAB"));
    assertEquals(400.0f, data.getFloat("PPVphBC"));
    assertEquals(400.0f, data.getFloat("PPVphCA"));
    assertEquals(50.0f, data.getFloat("Hz"));
    assertEquals(12000.0f, data.getFloat("W"));
  }

  // ---- Model Registry Verification ----

  @Test
  void testMeterModelRegistryContainsAllMeterModels() {
    // Verify all 8 meter models are registered
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_SINGLE_PHASE).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_SPLIT_PHASE).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_THREE_PHASE_WYE).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_THREE_PHASE_DELTA).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_SINGLE_PHASE_FLOAT).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_SPLIT_PHASE_FLOAT).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_THREE_PHASE_WYE_FLOAT).orElse(null));
    assertNotNull(SunSpecModelRegistry.get(MODEL_METER_THREE_PHASE_DELTA_FLOAT).orElse(null));

    // Verify model names
    assertEquals("Meter (Single Phase AN/AB, Int+SF)",
      SunSpecModelRegistry.require(MODEL_METER_SINGLE_PHASE).name());
    assertEquals("Meter (Split Phase ABN, Int+SF)",
      SunSpecModelRegistry.require(MODEL_METER_SPLIT_PHASE).name());
    assertEquals("Meter (Three Phase WYE, Int+SF)",
      SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_WYE).name());
    assertEquals("Meter (Three Phase Delta, Int+SF)",
      SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_DELTA).name());

    // Verify register sizes
    assertEquals(105, SunSpecModelRegistry.require(MODEL_METER_SINGLE_PHASE).totalRegisters());
    assertEquals(105, SunSpecModelRegistry.require(MODEL_METER_SPLIT_PHASE).totalRegisters());
    assertEquals(105, SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_WYE).totalRegisters());
    assertEquals(105, SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_DELTA).totalRegisters());
    assertEquals(124, SunSpecModelRegistry.require(MODEL_METER_SINGLE_PHASE_FLOAT).totalRegisters());
    assertEquals(124, SunSpecModelRegistry.require(MODEL_METER_SPLIT_PHASE_FLOAT).totalRegisters());
    assertEquals(124, SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_WYE_FLOAT).totalRegisters());
    assertEquals(124, SunSpecModelRegistry.require(MODEL_METER_THREE_PHASE_DELTA_FLOAT).totalRegisters());
  }

  // ---- Helpers ----

  /**
   * Encodes a float32 value into two consecutive registers.
   */
  private static void encodeFloat32(int[] registers, int offset, float value) {
    int bits = Float.floatToIntBits(value);
    registers[offset] = (bits >> 16) & 0xFFFF;
    registers[offset + 1] = bits & 0xFFFF;
  }

  /**
   * Encodes a signed int16 value as an unsigned register value.
   */
  private static int encodeInt16(int value) {
    return value & 0xFFFF;
  }
}
