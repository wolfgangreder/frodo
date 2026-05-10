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

import java.util.Map;
import java.util.Optional;

import static at.or.reder.frodo.modbus.sunspec.SunSpecConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecModelRegistry}.
 */
class SunSpecModelRegistryTest {

  @Test
  void testAllModelsRegistered() {
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_COMMON));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_INVERTER_SINGLE_PHASE));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_INVERTER_SPLIT_PHASE));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_INVERTER_THREE_PHASE));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_INVERTER_SINGLE_PHASE_FLOAT));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_INVERTER_SPLIT_PHASE_FLOAT));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_INVERTER_THREE_PHASE_FLOAT));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_NAMEPLATE));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_SETTINGS));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_STATUS));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_CONTROLS));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_STORAGE));
    assertTrue(SunSpecModelRegistry.isKnown(MODEL_MPPT));
  }

  @Test
  void testUnknownModelNotRegistered() {
    assertFalse(SunSpecModelRegistry.isKnown(999));
    assertFalse(SunSpecModelRegistry.isKnown(0));
    assertFalse(SunSpecModelRegistry.isKnown(END_MODEL_ID));
  }

  @Test
  void testGetReturnsOptional() {
    Optional<SunSpecModelDefinition> common = SunSpecModelRegistry.get(MODEL_COMMON);
    assertTrue(common.isPresent());
    assertEquals(MODEL_COMMON, common.get().modelId());

    Optional<SunSpecModelDefinition> unknown = SunSpecModelRegistry.get(999);
    assertTrue(unknown.isEmpty());
  }

  @Test
  void testRequireReturnsDefinition() {
    SunSpecModelDefinition common = SunSpecModelRegistry.require(MODEL_COMMON);
    assertNotNull(common);
    assertEquals(MODEL_COMMON, common.modelId());
    assertEquals("Common", common.name());
  }

  @Test
  void testRequireThrowsForUnknown() {
    assertThrows(IllegalArgumentException.class, () -> SunSpecModelRegistry.require(999));
  }

  @Test
  void testAllReturnsUnmodifiableMap() {
    Map<Integer, SunSpecModelDefinition> all = SunSpecModelRegistry.all();
    assertTrue(all.size() >= 13, "Should have at least 13 registered models");
    assertThrows(UnsupportedOperationException.class,
      () -> all.put(999, null));
  }

  // ---- Common Model (1) ----

  @Test
  void testCommonModelFields() {
    SunSpecModelDefinition common = SunSpecModelRegistry.require(MODEL_COMMON);
    assertEquals(6, common.fields().size());

    assertNotNull(common.field("Mn"));
    assertEquals(SunSpecDataType.STRING, common.field("Mn").dataType());
    assertEquals(16, common.field("Mn").size());
    assertEquals(0, common.field("Mn").offset());

    assertNotNull(common.field("Md"));
    assertEquals(16, common.field("Md").offset());

    assertNotNull(common.field("Opt"));
    assertEquals(32, common.field("Opt").offset());

    assertNotNull(common.field("Vr"));
    assertEquals(40, common.field("Vr").offset());

    assertNotNull(common.field("SN"));
    assertEquals(48, common.field("SN").offset());

    assertNotNull(common.field("DA"));
    assertEquals(64, common.field("DA").offset());
    assertEquals(SunSpecDataType.UINT16, common.field("DA").dataType());
  }

  @Test
  void testCommonModelTotalRegisters() {
    SunSpecModelDefinition common = SunSpecModelRegistry.require(MODEL_COMMON);
    assertEquals(65, common.totalRegisters());
  }

  @Test
  void testCommonModelNotWritable() {
    SunSpecModelDefinition common = SunSpecModelRegistry.require(MODEL_COMMON);
    assertFalse(common.hasWritableFields());
  }

  // ---- Inverter Float Models (111-113) ----

  @Test
  void testInverterFloatModelFields() {
    SunSpecModelDefinition inv = SunSpecModelRegistry.require(MODEL_INVERTER_THREE_PHASE_FLOAT);
    assertEquals(31, inv.fields().size());

    // Spot check key fields
    assertNotNull(inv.field("A"));
    assertEquals(SunSpecDataType.FLOAT32, inv.field("A").dataType());
    assertEquals(0, inv.field("A").offset());
    assertEquals(2, inv.field("A").size());

    assertNotNull(inv.field("W"));
    assertEquals(20, inv.field("W").offset());

    assertNotNull(inv.field("Hz"));
    assertEquals(22, inv.field("Hz").offset());

    assertNotNull(inv.field("St"));
    assertEquals(SunSpecDataType.ENUM16, inv.field("St").dataType());

    assertNotNull(inv.field("Evt1"));
    assertEquals(SunSpecDataType.BITFIELD32, inv.field("Evt1").dataType());
  }

  @Test
  void testInverterFloatModelNotWritable() {
    SunSpecModelDefinition inv = SunSpecModelRegistry.require(MODEL_INVERTER_SINGLE_PHASE_FLOAT);
    assertFalse(inv.hasWritableFields());
  }

  @Test
  void testInverterFloatModelsShareFieldCount() {
    SunSpecModelDefinition sp = SunSpecModelRegistry.require(MODEL_INVERTER_SINGLE_PHASE_FLOAT);
    SunSpecModelDefinition spl = SunSpecModelRegistry.require(MODEL_INVERTER_SPLIT_PHASE_FLOAT);
    SunSpecModelDefinition tp = SunSpecModelRegistry.require(MODEL_INVERTER_THREE_PHASE_FLOAT);
    assertEquals(sp.fields().size(), spl.fields().size());
    assertEquals(sp.fields().size(), tp.fields().size());
  }

  // ---- Inverter Int&SF Models (101-103) ----

  @Test
  void testInverterIntSfModelFields() {
    SunSpecModelDefinition inv = SunSpecModelRegistry.require(MODEL_INVERTER_THREE_PHASE);

    // Should have more fields than float models (scale factors are separate fields)
    assertTrue(inv.fields().size() > 30);

    // Check scale factor fields
    assertNotNull(inv.field("A_SF"));
    assertEquals(SunSpecDataType.SUNSSF, inv.field("A_SF").dataType());

    assertNotNull(inv.field("V_SF"));
    assertNotNull(inv.field("W_SF"));

    // Verify scaled fields reference their SF
    SunSpecFieldDefinition aField = inv.field("A");
    assertNotNull(aField);
    assertEquals("A_SF", aField.scaleFactor());
    assertTrue(aField.hasScaleFactor());
  }

  @Test
  void testInverterIntSfModelsShareFieldCount() {
    SunSpecModelDefinition sp = SunSpecModelRegistry.require(MODEL_INVERTER_SINGLE_PHASE);
    SunSpecModelDefinition spl = SunSpecModelRegistry.require(MODEL_INVERTER_SPLIT_PHASE);
    SunSpecModelDefinition tp = SunSpecModelRegistry.require(MODEL_INVERTER_THREE_PHASE);
    assertEquals(sp.fields().size(), spl.fields().size());
    assertEquals(sp.fields().size(), tp.fields().size());
  }

  // ---- Nameplate Model (120) ----

  @Test
  void testNameplateModelFields() {
    SunSpecModelDefinition np = SunSpecModelRegistry.require(MODEL_NAMEPLATE);
    assertNotNull(np.field("DERTyp"));
    assertNotNull(np.field("WRtg"));
    assertNotNull(np.field("WRtg_SF"));
    assertNotNull(np.field("VARtg"));
    assertNotNull(np.field("WHRtg"));
    assertNotNull(np.field("MaxChaRte"));
    assertNotNull(np.field("MaxDisChaRte"));
    assertFalse(np.hasWritableFields());
  }

  // ---- Settings Model (121) ----

  @Test
  void testSettingsModelFields() {
    SunSpecModelDefinition settings = SunSpecModelRegistry.require(MODEL_SETTINGS);
    assertNotNull(settings.field("WMax"));
    assertNotNull(settings.field("VRef"));
    assertNotNull(settings.field("ConnPh"));
    assertNotNull(settings.field("WMax_SF"));
    assertFalse(settings.hasWritableFields());
  }

  // ---- Status Model (122) ----

  @Test
  void testStatusModelFields() {
    SunSpecModelDefinition status = SunSpecModelRegistry.require(MODEL_STATUS);
    assertNotNull(status.field("PVConn"));
    assertNotNull(status.field("ActWh"));
    assertEquals(SunSpecDataType.ACC64, status.field("ActWh").dataType());
    assertEquals(4, status.field("ActWh").size());
    assertNotNull(status.field("Tms"));
    assertNotNull(status.field("RtSt"));
    assertFalse(status.hasWritableFields());
  }

  // ---- Controls Model (123) ----

  @Test
  void testControlsModelHasWritableFields() {
    SunSpecModelDefinition controls = SunSpecModelRegistry.require(MODEL_CONTROLS);
    assertTrue(controls.hasWritableFields());

    assertTrue(controls.field("Conn").writable());
    assertTrue(controls.field("WMaxLimPct").writable());
    assertTrue(controls.field("WMaxLim_Ena").writable());
    assertTrue(controls.field("OutPFSet").writable());
    assertTrue(controls.field("VArPct_Ena").writable());

    // Scale factors should be read-only
    assertFalse(controls.field("WMaxLimPct_SF").writable());
    assertFalse(controls.field("OutPFSet_SF").writable());
  }

  // ---- Storage Model (124) ----

  @Test
  void testStorageModelHasWritableFields() {
    SunSpecModelDefinition storage = SunSpecModelRegistry.require(MODEL_STORAGE);
    assertTrue(storage.hasWritableFields());

    assertTrue(storage.field("StorCtl_Mod").writable());
    assertTrue(storage.field("VAChaMax").writable());
    assertTrue(storage.field("MinRsvPct").writable());
    assertTrue(storage.field("OutWRte").writable());
    assertTrue(storage.field("InWRte").writable());
    assertTrue(storage.field("ChaGriSet").writable());

    // Read-only fields
    assertFalse(storage.field("WChaMax").writable());
    assertFalse(storage.field("ChaState").writable());
    assertFalse(storage.field("ChaSt").writable());
  }

  // ---- MPPT Model (160) ----

  @Test
  void testMpptModelFields() {
    SunSpecModelDefinition mppt = SunSpecModelRegistry.require(MODEL_MPPT);

    // Header fields
    assertNotNull(mppt.field("DCA_SF"));
    assertNotNull(mppt.field("DCV_SF"));
    assertNotNull(mppt.field("DCW_SF"));
    assertNotNull(mppt.field("DCWH_SF"));
    assertNotNull(mppt.field("Evt"));
    assertNotNull(mppt.field("N"));
    assertEquals(SunSpecDataType.COUNT, mppt.field("N").dataType());
    assertNotNull(mppt.field("TmsPer"));
  }

  @Test
  void testMpptModelModuleFields() {
    SunSpecModelDefinition mppt = SunSpecModelRegistry.require(MODEL_MPPT);

    // Module 1
    assertNotNull(mppt.field("module/1/ID"));
    assertNotNull(mppt.field("module/1/IDStr"));
    assertEquals(SunSpecDataType.STRING, mppt.field("module/1/IDStr").dataType());
    assertEquals(8, mppt.field("module/1/IDStr").size());
    assertNotNull(mppt.field("module/1/DCA"));
    assertTrue(mppt.field("module/1/DCA").hasScaleFactor());
    assertEquals("DCA_SF", mppt.field("module/1/DCA").scaleFactor());
    assertNotNull(mppt.field("module/1/DCV"));
    assertNotNull(mppt.field("module/1/DCW"));
    assertNotNull(mppt.field("module/1/DCWH"));
    assertNotNull(mppt.field("module/1/Tms"));
    assertNotNull(mppt.field("module/1/Tmp"));
    assertNotNull(mppt.field("module/1/DCSt"));
    assertNotNull(mppt.field("module/1/DCEvt"));

    // Module 2
    assertNotNull(mppt.field("module/2/ID"));
    assertNotNull(mppt.field("module/2/DCA"));
    assertNotNull(mppt.field("module/2/DCEvt"));
  }

  @Test
  void testMpptModelModuleOffsets() {
    SunSpecModelDefinition mppt = SunSpecModelRegistry.require(MODEL_MPPT);

    // Module 1 starts at offset 8
    assertEquals(8, mppt.field("module/1/ID").offset());

    // Module 2 starts at offset 28
    assertEquals(28, mppt.field("module/2/ID").offset());
  }

  @Test
  void testMpptModelNotWritable() {
    SunSpecModelDefinition mppt = SunSpecModelRegistry.require(MODEL_MPPT);
    assertFalse(mppt.hasWritableFields());
  }
}
