package at.or.reder.frodo.modbus.sunspec;

import org.junit.jupiter.api.Test;

import static at.or.reder.frodo.modbus.sunspec.SunSpecConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecModelDataDecoder}.
 */
class SunSpecModelDataDecoderTest {

  // ---- Common Model (1) - String decoding ----

  @Test
  void testDecodeCommonModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_COMMON);
    // Build 65 registers for the Common model
    int[] registers = new int[65];

    // Mn at offset 0, 16 registers: "Fronius"
    encodeString(registers, 0, 16, "Fronius");

    // Md at offset 16, 16 registers: "Symo 10.0-3-M"
    encodeString(registers, 16, 16, "Symo 10.0-3-M");

    // Opt at offset 32, 8 registers: ""
    encodeString(registers, 32, 8, "");

    // Vr at offset 40, 8 registers: "1.32.4-1"
    encodeString(registers, 40, 8, "1.32.4-1");

    // SN at offset 48, 16 registers: "29303030313233343536"
    encodeString(registers, 48, 16, "12345678ABCD");

    // DA at offset 64, 1 register: unit ID = 1
    registers[64] = 1;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40002);

    assertEquals(MODEL_COMMON, data.modelId());
    assertEquals("Common", data.modelName());
    assertEquals(40002, data.address());
    assertEquals("Fronius", data.getString("Mn"));
    assertEquals("Symo 10.0-3-M", data.getString("Md"));
    assertEquals("", data.getString("Opt"));
    assertEquals("1.32.4-1", data.getString("Vr"));
    assertEquals("12345678ABCD", data.getString("SN"));
    assertEquals(1, data.getInt("DA"));
  }

  // ---- Inverter Float Model (113) ----

  @Test
  void testDecodeInverterFloatModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_INVERTER_THREE_PHASE_FLOAT);
    int[] registers = new int[60];

    // A at offset 0: 12.5 A
    encodeFloat32(registers, 0, 12.5f);

    // AphA at offset 2: 4.2 A
    encodeFloat32(registers, 2, 4.2f);

    // W at offset 20: 3500.0 W
    encodeFloat32(registers, 20, 3500.0f);

    // Hz at offset 22: 50.0 Hz
    encodeFloat32(registers, 22, 50.0f);

    // WH at offset 30: 123456.0 Wh
    encodeFloat32(registers, 30, 123456.0f);

    // DCA at offset 32: 8.5 A
    encodeFloat32(registers, 32, 8.5f);

    // DCV at offset 34: 420.0 V
    encodeFloat32(registers, 34, 420.0f);

    // DCW at offset 36: 3570.0 W
    encodeFloat32(registers, 36, 3570.0f);

    // St at offset 46: operating state = 4 (MPPT)
    registers[46] = 4;

    // StVnd at offset 47: vendor state = 0
    registers[47] = 0;

    // Evt1 at offset 48: no events
    registers[48] = 0;
    registers[49] = 0;

    // Fill remaining with NaN (not implemented) for float fields
    encodeFloat32(registers, 4, Float.NaN);   // AphB
    encodeFloat32(registers, 6, Float.NaN);   // AphC
    encodeFloat32(registers, 8, Float.NaN);   // PPVphAB
    encodeFloat32(registers, 10, Float.NaN);  // PPVphBC
    encodeFloat32(registers, 12, Float.NaN);  // PPVphCA
    encodeFloat32(registers, 14, Float.NaN);  // PhVphA
    encodeFloat32(registers, 16, Float.NaN);  // PhVphB
    encodeFloat32(registers, 18, Float.NaN);  // PhVphC

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40070);

    assertEquals(MODEL_INVERTER_THREE_PHASE_FLOAT, data.modelId());
    assertEquals(12.5f, data.getFloat("A"));
    assertEquals(4.2f, data.getFloat("AphA"));
    assertNull(data.getFloat("AphB")); // NaN -> null
    assertNull(data.getFloat("AphC")); // NaN -> null
    assertEquals(3500.0f, data.getFloat("W"));
    assertEquals(50.0f, data.getFloat("Hz"));
    assertEquals(123456.0f, data.getFloat("WH"));
    assertEquals(8.5f, data.getFloat("DCA"));
    assertEquals(420.0f, data.getFloat("DCV"));
    assertEquals(3570.0f, data.getFloat("DCW"));
    assertEquals(4, data.getInt("St"));
    assertEquals(0, data.getInt("StVnd"));
  }

  // ---- Inverter Int&SF Model (103) with Scale Factors ----

  @Test
  void testDecodeInverterIntSfModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_INVERTER_THREE_PHASE);
    int[] registers = new int[50];

    // A at offset 0: raw=1250, A_SF at offset 4: -1 -> 1250 * 10^-1 = 125.0 A
    registers[0] = 1250;
    // A_SF at offset 4: -1
    registers[4] = encodeInt16(-1);

    // W at offset 12: raw=3500, W_SF at offset 13: 0 -> 3500 * 10^0 = 3500.0 W
    registers[12] = encodeInt16(3500);
    registers[13] = encodeInt16(0);

    // Hz at offset 14: raw=5000, Hz_SF at offset 15: -2 -> 5000 * 10^-2 = 50.0 Hz
    registers[14] = 5000;
    registers[15] = encodeInt16(-2);

    // V_SF at offset 11: -1
    registers[11] = encodeInt16(-1);

    // PhVphA at offset 8: raw=2300 -> 2300 * 10^-1 = 230.0 V
    registers[8] = 2300;

    // St at offset 36: operating state = 4 (MPPT)
    registers[36] = 4;

    // Tmp_SF at offset 35: -1
    registers[35] = encodeInt16(-1);

    // TmpCab at offset 31: raw=350 -> 350 * 10^-1 = 35.0 C
    registers[31] = encodeInt16(350);

    // Fill "not implemented" sentinel for unused uint16 fields
    registers[1] = 0xFFFF;  // AphA not implemented
    registers[2] = 0xFFFF;  // AphB not implemented
    registers[3] = 0xFFFF;  // AphC not implemented

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40070);

    // Verify scaled values
    assertEquals(125.0, data.getDouble("A"), 0.01);
    assertEquals(3500.0, data.getDouble("W"), 0.01);
    assertEquals(50.0, data.getDouble("Hz"), 0.01);
    assertEquals(230.0, data.getDouble("PhVphA"), 0.01);
    assertEquals(35.0, data.getDouble("TmpCab"), 0.01);

    // Not implemented fields with SF should still be null (uint16 = 0xFFFF -> null)
    assertNull(data.getDouble("AphA"));

    // Enum field (not scaled)
    assertEquals(4, data.getInt("St"));
  }

  // ---- MPPT Model (160) with Modules ----

  @Test
  void testDecodeMpptModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_MPPT);
    int[] registers = new int[48];

    // Header scale factors
    registers[0] = encodeInt16(-2);  // DCA_SF
    registers[1] = encodeInt16(-1);  // DCV_SF
    registers[2] = encodeInt16(0);   // DCW_SF
    registers[3] = encodeInt16(0);   // DCWH_SF

    // Evt at offset 4 (2 regs): no events
    registers[4] = 0;
    registers[5] = 0;

    // N at offset 6: 2 modules
    registers[6] = 2;

    // TmsPer at offset 7: 0
    registers[7] = 0;

    // Module 1 (offset 8-27)
    registers[8] = 1;                 // module/1/ID
    encodeString(registers, 9, 8, "String1");  // module/1/IDStr
    registers[17] = 850;              // module/1/DCA: raw=850 * 10^-2 = 8.5 A
    registers[18] = 4200;             // module/1/DCV: raw=4200 * 10^-1 = 420.0 V
    registers[19] = 3570;             // module/1/DCW: raw=3570 * 10^0 = 3570 W
    registers[20] = 0x0001;           // module/1/DCWH high (acc32)
    registers[21] = 0x0000;           // module/1/DCWH low -> 65536 Wh

    // Module 2 (offset 28-47)
    registers[28] = 2;                // module/2/ID
    encodeString(registers, 29, 8, "String2");  // module/2/IDStr
    registers[37] = 750;              // module/2/DCA: raw=750 * 10^-2 = 7.5 A
    registers[38] = 4100;             // module/2/DCV: raw=4100 * 10^-1 = 410.0 V
    registers[39] = 3075;             // module/2/DCW: raw=3075 * 10^0 = 3075 W

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40264);

    assertEquals(MODEL_MPPT, data.modelId());

    // Header
    assertEquals(-2, data.getInt("DCA_SF"));
    assertEquals(-1, data.getInt("DCV_SF"));
    assertEquals(2, data.getInt("N"));

    // Module 1
    assertEquals(1, data.getInt("module/1/ID"));
    assertEquals("String1", data.getString("module/1/IDStr"));
    assertEquals(8.5, data.getDouble("module/1/DCA"), 0.01);
    assertEquals(420.0, data.getDouble("module/1/DCV"), 0.01);
    assertEquals(3570.0, data.getDouble("module/1/DCW"), 0.01);

    // Module 2
    assertEquals(2, data.getInt("module/2/ID"));
    assertEquals("String2", data.getString("module/2/IDStr"));
    assertEquals(7.5, data.getDouble("module/2/DCA"), 0.01);
    assertEquals(410.0, data.getDouble("module/2/DCV"), 0.01);
    assertEquals(3075.0, data.getDouble("module/2/DCW"), 0.01);
  }

  // ---- Nameplate Model (120) with Scale Factors ----

  @Test
  void testDecodeNameplateModel() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_NAMEPLATE);
    int[] registers = new int[26];

    // DERTyp at offset 0: type 4 = PV
    registers[0] = 4;

    // WRtg at offset 1: raw=10000, WRtg_SF at offset 2: 0 -> 10000 W = 10 kW
    registers[1] = 10000;
    registers[2] = encodeInt16(0);  // WRtg_SF

    // VARtg at offset 3: raw=10000, VARtg_SF at offset 4: 0
    registers[3] = 10000;
    registers[4] = encodeInt16(0);

    // ARtg at offset 10: raw=145, ARtg_SF at offset 11: -1 -> 14.5 A
    registers[10] = 145;
    registers[11] = encodeInt16(-1);

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40132);

    assertEquals(4, data.getInt("DERTyp"));
    assertEquals(10000.0, data.getDouble("WRtg"), 0.01);
    assertEquals(10000.0, data.getDouble("VARtg"), 0.01);
    assertEquals(14.5, data.getDouble("ARtg"), 0.01);
  }

  // ---- Status Model (122) with ACC64 ----

  @Test
  void testDecodeStatusModelAcc64() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_STATUS);
    int[] registers = new int[44];

    // PVConn at offset 0: connected (bits set)
    registers[0] = 0x0003;

    // ActWh at offset 3, 4 registers (acc64): 1000000 Wh
    // 1000000 = 0x00000000000F4240
    registers[3] = 0x0000;
    registers[4] = 0x0000;
    registers[5] = 0x000F;
    registers[6] = 0x4240;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40192);

    assertEquals(3, data.getInt("PVConn"));  // bitfield16
    assertEquals(1000000L, data.getLong("ActWh"));
  }

  // ---- Edge case: field exceeds register length ----

  @Test
  void testDecodeFieldExceedsRegisters() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_COMMON);
    // Provide only 40 registers instead of 65 needed
    int[] registers = new int[40];
    encodeString(registers, 0, 16, "Fronius");
    encodeString(registers, 16, 16, "Symo");

    // Should not throw, fields beyond register data get null
    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40002);

    assertEquals("Fronius", data.getString("Mn"));
    assertEquals("Symo", data.getString("Md"));
    // Opt at offset 32 with size 8 -> 32+8=40, just fits
    assertNotNull(data.getString("Opt"));
    // Vr at offset 40 -> 40+8=48 > 40, exceeds
    assertNull(data.get("Vr", String.class));
    // SN at offset 48 -> exceeds
    assertNull(data.get("SN", String.class));
    // DA at offset 64 -> exceeds
    assertNull(data.getInt("DA"));
  }

  // ---- Pad fields ----

  @Test
  void testDecodeSkipsPadFields() {
    SunSpecModelDefinition def = SunSpecModelRegistry.require(MODEL_NAMEPLATE);
    int[] registers = new int[26];
    // Pad at offset 25 should decode to null
    registers[25] = 0x1234;

    SunSpecModelData data = SunSpecModelDataDecoder.decode(def, registers, 40132);
    // PAD fields should produce null values
    assertFalse(data.hasValue("Pad"));
  }

  // ---- decodeField internal ----

  @Test
  void testDecodeFieldEnum16() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "St", 0, 1, SunSpecDataType.ENUM16, null, "State");
    int[] registers = {4};

    Object result = SunSpecModelDataDecoder.decodeField(field, registers);
    assertEquals(4, result);
  }

  @Test
  void testDecodeFieldBitfield16() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "Flags", 0, 1, SunSpecDataType.BITFIELD16, null, "Flags");
    int[] registers = {0x000F};

    Object result = SunSpecModelDataDecoder.decodeField(field, registers);
    assertEquals(15, result);
  }

  @Test
  void testDecodeFieldCount() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "N", 0, 1, SunSpecDataType.COUNT, null, "Count");
    int[] registers = {5};

    Object result = SunSpecModelDataDecoder.decodeField(field, registers);
    assertEquals(5, result);
  }

  @Test
  void testDecodeFieldPad() {
    SunSpecFieldDefinition field = SunSpecFieldDefinition.readOnly(
      "Pad", 0, 1, SunSpecDataType.PAD, null, "Padding");
    int[] registers = {0x1234};

    Object result = SunSpecModelDataDecoder.decodeField(field, registers);
    assertNull(result);
  }

  // ---- Helpers ----

  /**
   * Encodes an ASCII string into register array positions.
   */
  private static void encodeString(int[] registers, int offset, int size, String value) {
    byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    for (int i = 0; i < size; i++) {
      int high = (i * 2 < bytes.length) ? (bytes[i * 2] & 0xFF) : 0;
      int low = (i * 2 + 1 < bytes.length) ? (bytes[i * 2 + 1] & 0xFF) : 0;
      registers[offset + i] = (high << 8) | low;
    }
  }

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
