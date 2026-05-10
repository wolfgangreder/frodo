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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static at.or.reder.frodo.modbus.sunspec.SunSpecConstants.*;
import static at.or.reder.frodo.modbus.sunspec.SunSpecDataType.*;
import static at.or.reder.frodo.modbus.sunspec.SunSpecFieldDefinition.*;

/**
 * Registry of all known SunSpec model definitions for Fronius Gen24 inverters.
 *
 * <p>Model field layouts are derived from the Fronius Gen24 Modbus register
 * maps (version 1.2.7-2). Both Float and Int&amp;SF formats are supported.</p>
 *
 * <p><b>Protocol References:</b></p>
 * <ul>
 *   <li>Common Model (1): {@code refdoc/gen24-modbus-api-external-docs/Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx}</li>
 *   <li>Inverter Float (111-113): Same Excel, sheets "Float"</li>
 *   <li>Inverter Int+SF (101-103): {@code refdoc/gen24-modbus-api-external-docs/Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_ROW.xlsx}</li>
 *   <li>Nameplate (120): Both register maps, sheet "Nameplate"</li>
 *   <li>Settings (121-123): Both register maps, sheets "Settings"</li>
 *   <li>Status (124-126): Both register maps, sheets "Status"</li>
 *   <li>Controls (127-132): Both register maps, sheets "Controls"</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * SunSpecModelDefinition commonModel = SunSpecModelRegistry.get(1);
 * SunSpecFieldDefinition mnField = commonModel.field("Mn");
 * </pre>
 */
public final class SunSpecModelRegistry {

  private static final Map<Integer, SunSpecModelDefinition> REGISTRY = new HashMap<>();

  static {
    registerCommonModel();
    registerInverterFloatModels();
    registerInverterIntSfModels();
    registerMeterFloatModels();
    registerMeterIntSfModels();
    registerNameplateModel();
    registerSettingsModel();
    registerStatusModel();
    registerControlsModel();
    registerStorageModel();
    registerMpptModel();
  }

  private SunSpecModelRegistry() {
    // Utility class
  }

  /**
   * Gets the model definition for a given model ID.
   *
   * @param modelId SunSpec model ID
   * @return Optional containing the model definition, or empty if unknown
   */
  public static Optional<SunSpecModelDefinition> get(int modelId) {
    return Optional.ofNullable(REGISTRY.get(modelId));
  }

  /**
   * Gets a model definition, throwing if not found.
   *
   * @param modelId SunSpec model ID
   * @return model definition
   * @throws IllegalArgumentException if model ID is not registered
   */
  public static SunSpecModelDefinition require(int modelId) {
    SunSpecModelDefinition def = REGISTRY.get(modelId);
    if (def == null) {
      throw new IllegalArgumentException("Unknown SunSpec model ID: " + modelId);
    }
    return def;
  }

  /**
   * Returns all registered model definitions.
   *
   * @return unmodifiable map of model ID to definition
   */
  public static Map<Integer, SunSpecModelDefinition> all() {
    return Collections.unmodifiableMap(REGISTRY);
  }

  /**
   * Checks whether a model ID is registered.
   *
   * @param modelId SunSpec model ID
   * @return true if the model is known
   */
  public static boolean isKnown(int modelId) {
    return REGISTRY.containsKey(modelId);
  }

  // ---- Model 1: Common ----

  private static void registerCommonModel() {
    List<SunSpecFieldDefinition> fields = List.of(
      readOnly("Mn", 0, 16, STRING, null, "Manufacturer"),
      readOnly("Md", 16, 16, STRING, null, "Device model"),
      readOnly("Opt", 32, 8, STRING, null, "Options"),
      readOnly("Vr", 40, 8, STRING, null, "SW version of inverter"),
      readOnly("SN", 48, 16, STRING, null, "Serial number of the inverter"),
      readOnly("DA", 64, 1, UINT16, null, "Modbus Device Address")
    );
    REGISTRY.put(MODEL_COMMON, SunSpecModelDefinition.of(MODEL_COMMON, "Common", fields));
  }

  // ---- Models 111-113: Inverter (Float format) ----

  private static void registerInverterFloatModels() {
    List<SunSpecFieldDefinition> fields = new ArrayList<>();
    fields.add(readOnly("A", 0, 2, FLOAT32, "A", "AC Current"));
    fields.add(readOnly("AphA", 2, 2, FLOAT32, "A", "Phase A Current"));
    fields.add(readOnly("AphB", 4, 2, FLOAT32, "A", "Phase B Current"));
    fields.add(readOnly("AphC", 6, 2, FLOAT32, "A", "Phase C Current"));
    fields.add(readOnly("PPVphAB", 8, 2, FLOAT32, "V", "Phase Voltage AB"));
    fields.add(readOnly("PPVphBC", 10, 2, FLOAT32, "V", "Phase Voltage BC"));
    fields.add(readOnly("PPVphCA", 12, 2, FLOAT32, "V", "Phase Voltage CA"));
    fields.add(readOnly("PhVphA", 14, 2, FLOAT32, "V", "Phase Voltage AN"));
    fields.add(readOnly("PhVphB", 16, 2, FLOAT32, "V", "Phase Voltage BN"));
    fields.add(readOnly("PhVphC", 18, 2, FLOAT32, "V", "Phase Voltage CN"));
    fields.add(readOnly("W", 20, 2, FLOAT32, "W", "AC Power"));
    fields.add(readOnly("Hz", 22, 2, FLOAT32, "Hz", "Line Frequency"));
    fields.add(readOnly("VA", 24, 2, FLOAT32, "VA", "AC Apparent Power"));
    fields.add(readOnly("VAr", 26, 2, FLOAT32, "var", "AC Reactive Power"));
    fields.add(readOnly("PF", 28, 2, FLOAT32, "Pct", "AC Power Factor"));
    fields.add(readOnly("WH", 30, 2, FLOAT32, "Wh", "AC Energy"));
    fields.add(readOnly("DCA", 32, 2, FLOAT32, "A", "DC Current"));
    fields.add(readOnly("DCV", 34, 2, FLOAT32, "V", "DC Voltage"));
    fields.add(readOnly("DCW", 36, 2, FLOAT32, "W", "DC Power"));
    fields.add(readOnly("TmpCab", 38, 2, FLOAT32, "C", "Cabinet Temperature"));
    fields.add(readOnly("TmpSnk", 40, 2, FLOAT32, "C", "Heat Sink Temperature"));
    fields.add(readOnly("TmpTrns", 42, 2, FLOAT32, "C", "Transformer Temperature"));
    fields.add(readOnly("TmpOt", 44, 2, FLOAT32, "C", "Other Temperature"));
    fields.add(readOnly("St", 46, 1, ENUM16, null, "Operating state"));
    fields.add(readOnly("StVnd", 47, 1, ENUM16, null, "Vendor specific operating state code"));
    fields.add(readOnly("Evt1", 48, 2, BITFIELD32, null, "Event fields"));
    fields.add(readOnly("Evt2", 50, 2, BITFIELD32, null, "Reserved for future use"));
    fields.add(readOnly("EvtVnd1", 52, 2, BITFIELD32, null, "Vendor defined events"));
    fields.add(readOnly("EvtVnd2", 54, 2, BITFIELD32, null, "Vendor defined events"));
    fields.add(readOnly("EvtVnd3", 56, 2, BITFIELD32, null, "Vendor defined events"));
    fields.add(readOnly("EvtVnd4", 58, 2, BITFIELD32, null, "Vendor defined events"));

    List<SunSpecFieldDefinition> immutableFields = List.copyOf(fields);
    REGISTRY.put(MODEL_INVERTER_SINGLE_PHASE_FLOAT,
      SunSpecModelDefinition.of(MODEL_INVERTER_SINGLE_PHASE_FLOAT, "Inverter (Single Phase, Float)", immutableFields));
    REGISTRY.put(MODEL_INVERTER_SPLIT_PHASE_FLOAT,
      SunSpecModelDefinition.of(MODEL_INVERTER_SPLIT_PHASE_FLOAT, "Inverter (Split Phase, Float)", immutableFields));
    REGISTRY.put(MODEL_INVERTER_THREE_PHASE_FLOAT,
      SunSpecModelDefinition.of(MODEL_INVERTER_THREE_PHASE_FLOAT, "Inverter (Three Phase, Float)", immutableFields));
  }

  // ---- Models 101-103: Inverter (Int&SF format) ----

  private static void registerInverterIntSfModels() {
    List<SunSpecFieldDefinition> fields = new ArrayList<>();
    fields.add(readOnlyScaled("A", 0, 1, UINT16, "A", "A_SF", "AC Total Current"));
    fields.add(readOnlyScaled("AphA", 1, 1, UINT16, "A", "A_SF", "Phase A Current"));
    fields.add(readOnlyScaled("AphB", 2, 1, UINT16, "A", "A_SF", "Phase B Current"));
    fields.add(readOnlyScaled("AphC", 3, 1, UINT16, "A", "A_SF", "Phase C Current"));
    fields.add(readOnly("A_SF", 4, 1, SUNSSF, null, "Current scale factor"));
    fields.add(readOnlyScaled("PPVphAB", 5, 1, UINT16, "V", "V_SF", "Phase Voltage AB"));
    fields.add(readOnlyScaled("PPVphBC", 6, 1, UINT16, "V", "V_SF", "Phase Voltage BC"));
    fields.add(readOnlyScaled("PPVphCA", 7, 1, UINT16, "V", "V_SF", "Phase Voltage CA"));
    fields.add(readOnlyScaled("PhVphA", 8, 1, UINT16, "V", "V_SF", "Phase Voltage AN"));
    fields.add(readOnlyScaled("PhVphB", 9, 1, UINT16, "V", "V_SF", "Phase Voltage BN"));
    fields.add(readOnlyScaled("PhVphC", 10, 1, UINT16, "V", "V_SF", "Phase Voltage CN"));
    fields.add(readOnly("V_SF", 11, 1, SUNSSF, null, "Voltage scale factor"));
    fields.add(readOnlyScaled("W", 12, 1, INT16, "W", "W_SF", "AC Power"));
    fields.add(readOnly("W_SF", 13, 1, SUNSSF, null, "Power scale factor"));
    fields.add(readOnlyScaled("Hz", 14, 1, UINT16, "Hz", "Hz_SF", "Line Frequency"));
    fields.add(readOnly("Hz_SF", 15, 1, SUNSSF, null, "Frequency scale factor"));
    fields.add(readOnlyScaled("VA", 16, 1, INT16, "VA", "VA_SF", "AC Apparent Power"));
    fields.add(readOnly("VA_SF", 17, 1, SUNSSF, null, "Apparent power scale factor"));
    fields.add(readOnlyScaled("VAr", 18, 1, INT16, "var", "VAr_SF", "AC Reactive Power"));
    fields.add(readOnly("VAr_SF", 19, 1, SUNSSF, null, "Reactive power scale factor"));
    fields.add(readOnlyScaled("PF", 20, 1, INT16, "Pct", "PF_SF", "AC Power Factor"));
    fields.add(readOnly("PF_SF", 21, 1, SUNSSF, null, "Power factor scale factor"));
    fields.add(readOnlyScaled("WH", 22, 2, ACC32, "Wh", "WH_SF", "AC Energy"));
    fields.add(readOnly("WH_SF", 24, 1, SUNSSF, null, "Energy scale factor"));
    fields.add(readOnlyScaled("DCA", 25, 1, UINT16, "A", "DCA_SF", "DC Current"));
    fields.add(readOnly("DCA_SF", 26, 1, SUNSSF, null, "DC current scale factor"));
    fields.add(readOnlyScaled("DCV", 27, 1, UINT16, "V", "DCV_SF", "DC Voltage"));
    fields.add(readOnly("DCV_SF", 28, 1, SUNSSF, null, "DC voltage scale factor"));
    fields.add(readOnlyScaled("DCW", 29, 1, INT16, "W", "DCW_SF", "DC Power"));
    fields.add(readOnly("DCW_SF", 30, 1, SUNSSF, null, "DC power scale factor"));
    fields.add(readOnlyScaled("TmpCab", 31, 1, INT16, "C", "Tmp_SF", "Cabinet Temperature"));
    fields.add(readOnlyScaled("TmpSnk", 32, 1, INT16, "C", "Tmp_SF", "Heat Sink Temperature"));
    fields.add(readOnlyScaled("TmpTrns", 33, 1, INT16, "C", "Tmp_SF", "Transformer Temperature"));
    fields.add(readOnlyScaled("TmpOt", 34, 1, INT16, "C", "Tmp_SF", "Other Temperature"));
    fields.add(readOnly("Tmp_SF", 35, 1, SUNSSF, null, "Temperature scale factor"));
    fields.add(readOnly("St", 36, 1, ENUM16, null, "Operating state"));
    fields.add(readOnly("StVnd", 37, 1, ENUM16, null, "Vendor specific operating state code"));
    fields.add(readOnly("Evt1", 38, 2, BITFIELD32, null, "Event fields"));
    fields.add(readOnly("Evt2", 40, 2, BITFIELD32, null, "Reserved for future use"));
    fields.add(readOnly("EvtVnd1", 42, 2, BITFIELD32, null, "Vendor defined events"));
    fields.add(readOnly("EvtVnd2", 44, 2, BITFIELD32, null, "Vendor defined events"));
    fields.add(readOnly("EvtVnd3", 46, 2, BITFIELD32, null, "Vendor defined events"));
    fields.add(readOnly("EvtVnd4", 48, 2, BITFIELD32, null, "Vendor defined events"));

    List<SunSpecFieldDefinition> immutableFields = List.copyOf(fields);
    REGISTRY.put(MODEL_INVERTER_SINGLE_PHASE,
      SunSpecModelDefinition.of(MODEL_INVERTER_SINGLE_PHASE, "Inverter (Single Phase, Int+SF)", immutableFields));
    REGISTRY.put(MODEL_INVERTER_SPLIT_PHASE,
      SunSpecModelDefinition.of(MODEL_INVERTER_SPLIT_PHASE, "Inverter (Split Phase, Int+SF)", immutableFields));
    REGISTRY.put(MODEL_INVERTER_THREE_PHASE,
      SunSpecModelDefinition.of(MODEL_INVERTER_THREE_PHASE, "Inverter (Three Phase, Int+SF)", immutableFields));
  }

  // ---- Models 211-214: Meter (Float format) ----

  private static void registerMeterFloatModels() {
    List<SunSpecFieldDefinition> fields = new ArrayList<>();
    
    // Current measurements (Float32, no scale factors)
    fields.add(readOnly("A", 0, 2, FLOAT32, "A", "Total AC Current"));
    fields.add(readOnly("AphA", 2, 2, FLOAT32, "A", "Phase A Current"));
    fields.add(readOnly("AphB", 4, 2, FLOAT32, "A", "Phase B Current"));
    fields.add(readOnly("AphC", 6, 2, FLOAT32, "A", "Phase C Current"));
    
    // Voltage measurements
    fields.add(readOnly("PhV", 8, 2, FLOAT32, "V", "Line to Neutral AC Voltage (avg)"));
    fields.add(readOnly("PhVphA", 10, 2, FLOAT32, "V", "Phase Voltage AN"));
    fields.add(readOnly("PhVphB", 12, 2, FLOAT32, "V", "Phase Voltage BN"));
    fields.add(readOnly("PhVphC", 14, 2, FLOAT32, "V", "Phase Voltage CN"));
    fields.add(readOnly("PPV", 16, 2, FLOAT32, "V", "Line to Line AC Voltage (avg)"));
    fields.add(readOnly("PPVphAB", 18, 2, FLOAT32, "V", "Phase Voltage AB"));
    fields.add(readOnly("PPVphBC", 20, 2, FLOAT32, "V", "Phase Voltage BC"));
    fields.add(readOnly("PPVphCA", 22, 2, FLOAT32, "V", "Phase Voltage CA"));
    
    // Frequency
    fields.add(readOnly("Hz", 24, 2, FLOAT32, "Hz", "Frequency"));
    
    // Power measurements
    fields.add(readOnly("W", 26, 2, FLOAT32, "W", "Total Real Power"));
    fields.add(readOnly("WphA", 28, 2, FLOAT32, "W", "Watts phase A"));
    fields.add(readOnly("WphB", 30, 2, FLOAT32, "W", "Watts phase B"));
    fields.add(readOnly("WphC", 32, 2, FLOAT32, "W", "Watts phase C"));
    fields.add(readOnly("VA", 34, 2, FLOAT32, "VA", "AC Apparent Power"));
    fields.add(readOnly("VAphA", 36, 2, FLOAT32, "VA", "VA phase A"));
    fields.add(readOnly("VAphB", 38, 2, FLOAT32, "VA", "VA phase B"));
    fields.add(readOnly("VAphC", 40, 2, FLOAT32, "VA", "VA phase C"));
    fields.add(readOnly("VAR", 42, 2, FLOAT32, "var", "Reactive Power"));
    fields.add(readOnly("VARphA", 44, 2, FLOAT32, "var", "VAR phase A"));
    fields.add(readOnly("VARphB", 46, 2, FLOAT32, "var", "VAR phase B"));
    fields.add(readOnly("VARphC", 48, 2, FLOAT32, "var", "VAR phase C"));
    
    // Power Factor
    fields.add(readOnly("PF", 50, 2, FLOAT32, "PF", "Power Factor"));
    fields.add(readOnly("PFphA", 52, 2, FLOAT32, "PF", "PF phase A"));
    fields.add(readOnly("PFphB", 54, 2, FLOAT32, "PF", "PF phase B"));
    fields.add(readOnly("PFphC", 56, 2, FLOAT32, "PF", "PF phase C"));
    
    // Energy measurements
    fields.add(readOnly("TotWhExp", 58, 2, FLOAT32, "Wh", "Total Real Energy Exported"));
    fields.add(readOnly("TotWhExpPhA", 60, 2, FLOAT32, "Wh", "Total Wh Exported phase A"));
    fields.add(readOnly("TotWhExpPhB", 62, 2, FLOAT32, "Wh", "Total Wh Exported phase B"));
    fields.add(readOnly("TotWhExpPhC", 64, 2, FLOAT32, "Wh", "Total Wh Exported phase C"));
    fields.add(readOnly("TotWhImp", 66, 2, FLOAT32, "Wh", "Total Real Energy Imported"));
    fields.add(readOnly("TotWhImpPhA", 68, 2, FLOAT32, "Wh", "Total Wh Imported phase A"));
    fields.add(readOnly("TotWhImpPhB", 70, 2, FLOAT32, "Wh", "Total Wh Imported phase B"));
    fields.add(readOnly("TotWhImpPhC", 72, 2, FLOAT32, "Wh", "Total Wh Imported phase C"));
    
    fields.add(readOnly("TotVAhExp", 74, 2, FLOAT32, "VAh", "Total Apparent Energy Exported"));
    fields.add(readOnly("TotVAhExpPhA", 76, 2, FLOAT32, "VAh", "Total VAh Exported phase A"));
    fields.add(readOnly("TotVAhExpPhB", 78, 2, FLOAT32, "VAh", "Total VAh Exported phase B"));
    fields.add(readOnly("TotVAhExpPhC", 80, 2, FLOAT32, "VAh", "Total VAh Exported phase C"));
    fields.add(readOnly("TotVAhImp", 82, 2, FLOAT32, "VAh", "Total Apparent Energy Imported"));
    fields.add(readOnly("TotVAhImpPhA", 84, 2, FLOAT32, "VAh", "Total VAh Imported phase A"));
    fields.add(readOnly("TotVAhImpPhB", 86, 2, FLOAT32, "VAh", "Total VAh Imported phase B"));
    fields.add(readOnly("TotVAhImpPhC", 88, 2, FLOAT32, "VAh", "Total VAh Imported phase C"));
    
    // Reactive energy by quadrant
    fields.add(readOnly("TotVArhImpQ1", 90, 2, FLOAT32, "varh", "Total VAR-hours Imported Q1"));
    fields.add(readOnly("TotVArhImpQ1phA", 92, 2, FLOAT32, "varh", "Total VArh Imported Q1 phase A"));
    fields.add(readOnly("TotVArhImpQ1phB", 94, 2, FLOAT32, "varh", "Total VArh Imported Q1 phase B"));
    fields.add(readOnly("TotVArhImpQ1phC", 96, 2, FLOAT32, "varh", "Total VArh Imported Q1 phase C"));
    fields.add(readOnly("TotVArhImpQ2", 98, 2, FLOAT32, "varh", "Total VAR-hours Imported Q2"));
    fields.add(readOnly("TotVArhImpQ2phA", 100, 2, FLOAT32, "varh", "Total VArh Imported Q2 phase A"));
    fields.add(readOnly("TotVArhImpQ2phB", 102, 2, FLOAT32, "varh", "Total VArh Imported Q2 phase B"));
    fields.add(readOnly("TotVArhImpQ2phC", 104, 2, FLOAT32, "varh", "Total VArh Imported Q2 phase C"));
    fields.add(readOnly("TotVArhExpQ3", 106, 2, FLOAT32, "varh", "Total VAR-hours Exported Q3"));
    fields.add(readOnly("TotVArhExpQ3phA", 108, 2, FLOAT32, "varh", "Total VArh Exported Q3 phase A"));
    fields.add(readOnly("TotVArhExpQ3phB", 110, 2, FLOAT32, "varh", "Total VArh Exported Q3 phase B"));
    fields.add(readOnly("TotVArhExpQ3phC", 112, 2, FLOAT32, "varh", "Total VArh Exported Q3 phase C"));
    fields.add(readOnly("TotVArhExpQ4", 114, 2, FLOAT32, "varh", "Total VAR-hours Exported Q4"));
    fields.add(readOnly("TotVArhExpQ4phA", 116, 2, FLOAT32, "varh", "Total VArh Exported Q4 phase A"));
    fields.add(readOnly("TotVArhExpQ4phB", 118, 2, FLOAT32, "varh", "Total VArh Exported Q4 phase B"));
    fields.add(readOnly("TotVArhExpQ4phC", 120, 2, FLOAT32, "varh", "Total VArh Exported Q4 phase C"));
    
    // Event flags
    fields.add(readOnly("Evt", 122, 2, BITFIELD32, null, "Meter Event Flags"));
    
    List<SunSpecFieldDefinition> immutableFields = List.copyOf(fields);
    REGISTRY.put(MODEL_METER_SINGLE_PHASE_FLOAT,
      SunSpecModelDefinition.of(MODEL_METER_SINGLE_PHASE_FLOAT, "Meter (Single Phase AN/AB, Float)", immutableFields));
    REGISTRY.put(MODEL_METER_SPLIT_PHASE_FLOAT,
      SunSpecModelDefinition.of(MODEL_METER_SPLIT_PHASE_FLOAT, "Meter (Split Phase ABN, Float)", immutableFields));
    REGISTRY.put(MODEL_METER_THREE_PHASE_WYE_FLOAT,
      SunSpecModelDefinition.of(MODEL_METER_THREE_PHASE_WYE_FLOAT, "Meter (Three Phase WYE, Float)", immutableFields));
    REGISTRY.put(MODEL_METER_THREE_PHASE_DELTA_FLOAT,
      SunSpecModelDefinition.of(MODEL_METER_THREE_PHASE_DELTA_FLOAT, "Meter (Three Phase Delta, Float)", immutableFields));
  }

  // ---- Models 201-204: Meter (Int&SF format) ----

  private static void registerMeterIntSfModels() {
    List<SunSpecFieldDefinition> fields = new ArrayList<>();
    
    // Current measurements (Int16 with scale factor)
    fields.add(readOnlyScaled("A", 0, 1, INT16, "A", "A_SF", "Total AC Current"));
    fields.add(readOnlyScaled("AphA", 1, 1, INT16, "A", "A_SF", "Phase A Current"));
    fields.add(readOnlyScaled("AphB", 2, 1, INT16, "A", "A_SF", "Phase B Current"));
    fields.add(readOnlyScaled("AphC", 3, 1, INT16, "A", "A_SF", "Phase C Current"));
    fields.add(readOnly("A_SF", 4, 1, SUNSSF, null, "Current scale factor"));
    
    // Voltage measurements
    fields.add(readOnlyScaled("PhV", 5, 1, INT16, "V", "V_SF", "Line to Neutral AC Voltage (avg)"));
    fields.add(readOnlyScaled("PhVphA", 6, 1, INT16, "V", "V_SF", "Phase Voltage AN"));
    fields.add(readOnlyScaled("PhVphB", 7, 1, INT16, "V", "V_SF", "Phase Voltage BN"));
    fields.add(readOnlyScaled("PhVphC", 8, 1, INT16, "V", "V_SF", "Phase Voltage CN"));
    fields.add(readOnlyScaled("PPV", 9, 1, INT16, "V", "V_SF", "Line to Line AC Voltage (avg)"));
    fields.add(readOnlyScaled("PPVphAB", 10, 1, INT16, "V", "V_SF", "Phase Voltage AB"));
    fields.add(readOnlyScaled("PPVphBC", 11, 1, INT16, "V", "V_SF", "Phase Voltage BC"));
    fields.add(readOnlyScaled("PPVphCA", 12, 1, INT16, "V", "V_SF", "Phase Voltage CA"));
    fields.add(readOnly("V_SF", 13, 1, SUNSSF, null, "Voltage scale factor"));
    
    // Frequency
    fields.add(readOnlyScaled("Hz", 14, 1, INT16, "Hz", "Hz_SF", "Frequency"));
    fields.add(readOnly("Hz_SF", 15, 1, SUNSSF, null, "Frequency scale factor"));
    
    // Power measurements
    fields.add(readOnlyScaled("W", 16, 1, INT16, "W", "W_SF", "Total Real Power"));
    fields.add(readOnlyScaled("WphA", 17, 1, INT16, "W", "W_SF", "Watts phase A"));
    fields.add(readOnlyScaled("WphB", 18, 1, INT16, "W", "W_SF", "Watts phase B"));
    fields.add(readOnlyScaled("WphC", 19, 1, INT16, "W", "W_SF", "Watts phase C"));
    fields.add(readOnly("W_SF", 20, 1, SUNSSF, null, "Real Power scale factor"));
    
    fields.add(readOnlyScaled("VA", 21, 1, INT16, "VA", "VA_SF", "AC Apparent Power"));
    fields.add(readOnlyScaled("VAphA", 22, 1, INT16, "VA", "VA_SF", "VA phase A"));
    fields.add(readOnlyScaled("VAphB", 23, 1, INT16, "VA", "VA_SF", "VA phase B"));
    fields.add(readOnlyScaled("VAphC", 24, 1, INT16, "VA", "VA_SF", "VA phase C"));
    fields.add(readOnly("VA_SF", 25, 1, SUNSSF, null, "Apparent Power scale factor"));
    
    fields.add(readOnlyScaled("VAR", 26, 1, INT16, "var", "VAR_SF", "Reactive Power"));
    fields.add(readOnlyScaled("VARphA", 27, 1, INT16, "var", "VAR_SF", "VAR phase A"));
    fields.add(readOnlyScaled("VARphB", 28, 1, INT16, "var", "VAR_SF", "VAR phase B"));
    fields.add(readOnlyScaled("VARphC", 29, 1, INT16, "var", "VAR_SF", "VAR phase C"));
    fields.add(readOnly("VAR_SF", 30, 1, SUNSSF, null, "Reactive Power scale factor"));
    
    // Power Factor
    fields.add(readOnlyScaled("PF", 31, 1, INT16, "Pct", "PF_SF", "Power Factor"));
    fields.add(readOnlyScaled("PFphA", 32, 1, INT16, "Pct", "PF_SF", "PF phase A"));
    fields.add(readOnlyScaled("PFphB", 33, 1, INT16, "Pct", "PF_SF", "PF phase B"));
    fields.add(readOnlyScaled("PFphC", 34, 1, INT16, "Pct", "PF_SF", "PF phase C"));
    fields.add(readOnly("PF_SF", 35, 1, SUNSSF, null, "Power Factor scale factor"));
    
    // Energy measurements (ACC32 = 2 registers)
    fields.add(readOnlyScaled("TotWhExp", 36, 2, ACC32, "Wh", "TotWh_SF", "Total Real Energy Exported"));
    fields.add(readOnlyScaled("TotWhExpPhA", 38, 2, ACC32, "Wh", "TotWh_SF", "Total Wh Exported phase A"));
    fields.add(readOnlyScaled("TotWhExpPhB", 40, 2, ACC32, "Wh", "TotWh_SF", "Total Wh Exported phase B"));
    fields.add(readOnlyScaled("TotWhExpPhC", 42, 2, ACC32, "Wh", "TotWh_SF", "Total Wh Exported phase C"));
    fields.add(readOnlyScaled("TotWhImp", 44, 2, ACC32, "Wh", "TotWh_SF", "Total Real Energy Imported"));
    fields.add(readOnlyScaled("TotWhImpPhA", 46, 2, ACC32, "Wh", "TotWh_SF", "Total Wh Imported phase A"));
    fields.add(readOnlyScaled("TotWhImpPhB", 48, 2, ACC32, "Wh", "TotWh_SF", "Total Wh Imported phase B"));
    fields.add(readOnlyScaled("TotWhImpPhC", 50, 2, ACC32, "Wh", "TotWh_SF", "Total Wh Imported phase C"));
    fields.add(readOnly("TotWh_SF", 52, 1, SUNSSF, null, "Real Energy scale factor"));
    
    fields.add(readOnlyScaled("TotVAhExp", 53, 2, ACC32, "VAh", "TotVAh_SF", "Total Apparent Energy Exported"));
    fields.add(readOnlyScaled("TotVAhExpPhA", 55, 2, ACC32, "VAh", "TotVAh_SF", "Total VAh Exported phase A"));
    fields.add(readOnlyScaled("TotVAhExpPhB", 57, 2, ACC32, "VAh", "TotVAh_SF", "Total VAh Exported phase B"));
    fields.add(readOnlyScaled("TotVAhExpPhC", 59, 2, ACC32, "VAh", "TotVAh_SF", "Total VAh Exported phase C"));
    fields.add(readOnlyScaled("TotVAhImp", 61, 2, ACC32, "VAh", "TotVAh_SF", "Total Apparent Energy Imported"));
    fields.add(readOnlyScaled("TotVAhImpPhA", 63, 2, ACC32, "VAh", "TotVAh_SF", "Total VAh Imported phase A"));
    fields.add(readOnlyScaled("TotVAhImpPhB", 65, 2, ACC32, "VAh", "TotVAh_SF", "Total VAh Imported phase B"));
    fields.add(readOnlyScaled("TotVAhImpPhC", 67, 2, ACC32, "VAh", "TotVAh_SF", "Total VAh Imported phase C"));
    fields.add(readOnly("TotVAh_SF", 69, 1, SUNSSF, null, "Apparent Energy scale factor"));
    
    // Reactive energy by quadrant
    fields.add(readOnlyScaled("TotVArhImpQ1", 70, 2, ACC32, "varh", "TotVArh_SF", "Total VAR-hours Imported Q1"));
    fields.add(readOnlyScaled("TotVArhImpQ1PhA", 72, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Imported Q1 phase A"));
    fields.add(readOnlyScaled("TotVArhImpQ1PhB", 74, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Imported Q1 phase B"));
    fields.add(readOnlyScaled("TotVArhImpQ1PhC", 76, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Imported Q1 phase C"));
    fields.add(readOnlyScaled("TotVArhImpQ2", 78, 2, ACC32, "varh", "TotVArh_SF", "Total VAR-hours Imported Q2"));
    fields.add(readOnlyScaled("TotVArhImpQ2PhA", 80, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Imported Q2 phase A"));
    fields.add(readOnlyScaled("TotVArhImpQ2PhB", 82, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Imported Q2 phase B"));
    fields.add(readOnlyScaled("TotVArhImpQ2PhC", 84, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Imported Q2 phase C"));
    fields.add(readOnlyScaled("TotVArhExpQ3", 86, 2, ACC32, "varh", "TotVArh_SF", "Total VAR-hours Exported Q3"));
    fields.add(readOnlyScaled("TotVArhExpQ3PhA", 88, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Exported Q3 phase A"));
    fields.add(readOnlyScaled("TotVArhExpQ3PhB", 90, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Exported Q3 phase B"));
    fields.add(readOnlyScaled("TotVArhExpQ3PhC", 92, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Exported Q3 phase C"));
    fields.add(readOnlyScaled("TotVArhExpQ4", 94, 2, ACC32, "varh", "TotVArh_SF", "Total VAR-hours Exported Q4"));
    fields.add(readOnlyScaled("TotVArhExpQ4PhA", 96, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Exported Q4 phase A"));
    fields.add(readOnlyScaled("TotVArhExpQ4PhB", 98, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Exported Q4 phase B"));
    fields.add(readOnlyScaled("TotVArhExpQ4PhC", 100, 2, ACC32, "varh", "TotVArh_SF", "Total VArh Exported Q4 phase C"));
    fields.add(readOnly("TotVArh_SF", 102, 1, SUNSSF, null, "Reactive Energy scale factor"));
    
    // Event flags
    fields.add(readOnly("Evt", 103, 2, BITFIELD32, null, "Meter Event Flags"));
    
    List<SunSpecFieldDefinition> immutableFields = List.copyOf(fields);
    REGISTRY.put(MODEL_METER_SINGLE_PHASE,
      SunSpecModelDefinition.of(MODEL_METER_SINGLE_PHASE, "Meter (Single Phase AN/AB, Int+SF)", immutableFields));
    REGISTRY.put(MODEL_METER_SPLIT_PHASE,
      SunSpecModelDefinition.of(MODEL_METER_SPLIT_PHASE, "Meter (Split Phase ABN, Int+SF)", immutableFields));
    REGISTRY.put(MODEL_METER_THREE_PHASE_WYE,
      SunSpecModelDefinition.of(MODEL_METER_THREE_PHASE_WYE, "Meter (Three Phase WYE, Int+SF)", immutableFields));
    REGISTRY.put(MODEL_METER_THREE_PHASE_DELTA,
      SunSpecModelDefinition.of(MODEL_METER_THREE_PHASE_DELTA, "Meter (Three Phase Delta, Int+SF)", immutableFields));
  }

  // ---- Model 120: Nameplate Ratings ----

  private static void registerNameplateModel() {
    List<SunSpecFieldDefinition> fields = List.of(
      readOnly("DERTyp", 0, 1, ENUM16, null, "Type of DER device"),
      readOnlyScaled("WRtg", 1, 1, UINT16, "W", "WRtg_SF", "Continuous power output capability"),
      readOnly("WRtg_SF", 2, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("VARtg", 3, 1, UINT16, "VA", "VARtg_SF", "Continuous VA capability"),
      readOnly("VARtg_SF", 4, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("VArRtgQ1", 5, 1, INT16, "var", "VArRtg_SF", "Continuous VAR capability Q1"),
      readOnlyScaled("VArRtgQ2", 6, 1, INT16, "var", "VArRtg_SF", "Continuous VAR capability Q2"),
      readOnlyScaled("VArRtgQ3", 7, 1, INT16, "var", "VArRtg_SF", "Continuous VAR capability Q3"),
      readOnlyScaled("VArRtgQ4", 8, 1, INT16, "var", "VArRtg_SF", "Continuous VAR capability Q4"),
      readOnly("VArRtg_SF", 9, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("ARtg", 10, 1, UINT16, "A", "ARtg_SF", "Maximum RMS AC current level"),
      readOnly("ARtg_SF", 11, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("PFRtgQ1", 12, 1, INT16, "cos()", "PFRtg_SF", "Min power factor Q1"),
      readOnlyScaled("PFRtgQ2", 13, 1, INT16, "cos()", "PFRtg_SF", "Min power factor Q2"),
      readOnlyScaled("PFRtgQ3", 14, 1, INT16, "cos()", "PFRtg_SF", "Min power factor Q3"),
      readOnlyScaled("PFRtgQ4", 15, 1, INT16, "cos()", "PFRtg_SF", "Min power factor Q4"),
      readOnly("PFRtg_SF", 16, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("WHRtg", 17, 1, UINT16, "Wh", "WHRtg_SF", "Nominal energy rating of storage"),
      readOnly("WHRtg_SF", 18, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("AhrRtg", 19, 1, UINT16, "AH", "AhrRtg_SF", "Usable battery capacity"),
      readOnly("AhrRtg_SF", 20, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("MaxChaRte", 21, 1, UINT16, "W", "MaxChaRte_SF", "Max charge rate"),
      readOnly("MaxChaRte_SF", 22, 1, SUNSSF, null, "Scale factor"),
      readOnlyScaled("MaxDisChaRte", 23, 1, UINT16, "W", "MaxDisChaRte_SF", "Max discharge rate"),
      readOnly("MaxDisChaRte_SF", 24, 1, SUNSSF, null, "Scale factor"),
      readOnly("Pad", 25, 1, PAD, null, "Pad register")
    );
    REGISTRY.put(MODEL_NAMEPLATE, SunSpecModelDefinition.of(MODEL_NAMEPLATE, "Nameplate Ratings", fields));
  }

  // ---- Model 121: Basic Settings ----

  private static void registerSettingsModel() {
    List<SunSpecFieldDefinition> fields = List.of(
      readOnlyScaled("WMax", 0, 1, UINT16, "W", "WMax_SF", "Maximum power output setting"),
      readOnlyScaled("VRef", 1, 1, UINT16, "V", "VRef_SF", "Voltage at the PCC"),
      readOnlyScaled("VRefOfs", 2, 1, INT16, "V", "VRefOfs_SF", "Offset from PCC to inverter"),
      readOnlyScaled("VMax", 3, 1, UINT16, "V", "VMinMax_SF", "Maximum voltage setpoint"),
      readOnlyScaled("VMin", 4, 1, UINT16, "V", "VMinMax_SF", "Minimum voltage setpoint"),
      readOnlyScaled("VAMax", 5, 1, UINT16, "VA", "VAMax_SF", "Maximum apparent power setpoint"),
      readOnlyScaled("VArMaxQ1", 6, 1, INT16, "var", "VArMax_SF", "Max reactive power Q1"),
      readOnlyScaled("VArMaxQ2", 7, 1, INT16, "var", "VArMax_SF", "Max reactive power Q2"),
      readOnlyScaled("VArMaxQ3", 8, 1, INT16, "var", "VArMax_SF", "Max reactive power Q3"),
      readOnlyScaled("VArMaxQ4", 9, 1, INT16, "var", "VArMax_SF", "Max reactive power Q4"),
      readOnlyScaled("WGra", 10, 1, UINT16, "% WMax/sec", "WGra_SF", "Default ramp rate"),
      readOnlyScaled("PFMinQ1", 11, 1, INT16, "cos()", "PFMin_SF", "Min power factor Q1"),
      readOnlyScaled("PFMinQ2", 12, 1, INT16, "cos()", "PFMin_SF", "Min power factor Q2"),
      readOnlyScaled("PFMinQ3", 13, 1, INT16, "cos()", "PFMin_SF", "Min power factor Q3"),
      readOnlyScaled("PFMinQ4", 14, 1, INT16, "cos()", "PFMin_SF", "Min power factor Q4"),
      readOnly("VArAct", 15, 1, ENUM16, null, "VAR action on charge/discharge change"),
      readOnly("ClcTotVA", 16, 1, ENUM16, null, "Apparent power calculation method"),
      readOnlyScaled("MaxRmpRte", 17, 1, UINT16, "% WGra", "MaxRmpRte_SF", "Max ramp rate percentage"),
      readOnlyScaled("ECPNomHz", 18, 1, UINT16, "Hz", "ECPNomHz_SF", "Nominal frequency at ECP"),
      readOnly("ConnPh", 19, 1, ENUM16, null, "Connected phase identity"),
      readOnly("WMax_SF", 20, 1, SUNSSF, null, "Scale factor for real power"),
      readOnly("VRef_SF", 21, 1, SUNSSF, null, "Scale factor for PCC voltage"),
      readOnly("VRefOfs_SF", 22, 1, SUNSSF, null, "Scale factor for offset voltage"),
      readOnly("VMinMax_SF", 23, 1, SUNSSF, null, "Scale factor for min/max voltages"),
      readOnly("VAMax_SF", 24, 1, SUNSSF, null, "Scale factor for apparent power"),
      readOnly("VArMax_SF", 25, 1, SUNSSF, null, "Scale factor for reactive power"),
      readOnly("WGra_SF", 26, 1, SUNSSF, null, "Scale factor for ramp rate"),
      readOnly("PFMin_SF", 27, 1, SUNSSF, null, "Scale factor for min power factor"),
      readOnly("MaxRmpRte_SF", 28, 1, SUNSSF, null, "Scale factor for max ramp rate"),
      readOnly("ECPNomHz_SF", 29, 1, SUNSSF, null, "Scale factor for nominal frequency")
    );
    REGISTRY.put(MODEL_SETTINGS, SunSpecModelDefinition.of(MODEL_SETTINGS, "Basic Settings", fields));
  }

  // ---- Model 122: Extended Measurements & Status ----

  private static void registerStatusModel() {
    List<SunSpecFieldDefinition> fields = List.of(
      readOnly("PVConn", 0, 1, BITFIELD16, null, "PV inverter present/available status"),
      readOnly("StorConn", 1, 1, BITFIELD16, null, "Storage inverter present/available status"),
      readOnly("ECPConn", 2, 1, BITFIELD16, null, "ECP connection status"),
      readOnly("ActWh", 3, 4, ACC64, "Wh", "AC lifetime active energy output"),
      readOnly("ActVAh", 7, 4, ACC64, "VAh", "AC lifetime apparent energy output"),
      readOnly("ActVArhQ1", 11, 4, ACC64, "varh", "AC lifetime reactive energy output Q1"),
      readOnly("ActVArhQ2", 15, 4, ACC64, "varh", "AC lifetime reactive energy output Q2"),
      readOnly("ActVArhQ3", 19, 4, ACC64, "varh", "AC lifetime reactive energy output Q3"),
      readOnly("ActVArhQ4", 23, 4, ACC64, "varh", "AC lifetime reactive energy output Q4"),
      readOnlyScaled("VArAval", 27, 1, INT16, "var", "VArAval_SF", "Available VARs"),
      readOnly("VArAval_SF", 28, 1, SUNSSF, null, "Scale factor for available VARs"),
      readOnlyScaled("WAval", 29, 1, UINT16, "W", "WAval_SF", "Available Watts"),
      readOnly("WAval_SF", 30, 1, SUNSSF, null, "Scale factor for available Watts"),
      readOnly("StSetLimMsk", 31, 2, BITFIELD32, null, "Setpoint limit(s) reached bitmask"),
      readOnly("StActCtl", 33, 2, BITFIELD32, null, "Active inverter controls bitmask"),
      readOnly("TmSrc", 35, 4, STRING, null, "Source of time synchronization"),
      readOnly("Tms", 39, 2, UINT32, "Secs", "Seconds since 2000-01-01 00:00 UTC"),
      readOnly("RtSt", 41, 1, BITFIELD16, null, "Active ride-through status bitmask"),
      readOnlyScaled("Ris", 42, 1, UINT16, "ohms", "Ris_SF", "Isolation resistance"),
      readOnly("Ris_SF", 43, 1, SUNSSF, null, "Scale factor for isolation resistance")
    );
    REGISTRY.put(MODEL_STATUS, SunSpecModelDefinition.of(MODEL_STATUS, "Extended Measurements & Status", fields));
  }

  // ---- Model 123: Immediate Controls ----

  private static void registerControlsModel() {
    List<SunSpecFieldDefinition> fields = List.of(
      writable("Conn_WinTms", 0, 1, UINT16, "Secs", null, "Time window for connect/disconnect"),
      writable("Conn_RvrtTms", 1, 1, UINT16, "Secs", null, "Timeout period for connect/disconnect"),
      writable("Conn", 2, 1, ENUM16, null, null, "Connection control"),
      writable("WMaxLimPct", 3, 1, UINT16, "% WMax", "WMaxLimPct_SF", "Power output limit"),
      writable("WMaxLimPct_WinTms", 4, 1, UINT16, "Secs", null, "Time window for power limit change"),
      writable("WMaxLimPct_RvrtTms", 5, 1, UINT16, "Secs", null, "Timeout period for power limit"),
      writable("WMaxLimPct_RmpTms", 6, 1, UINT16, "Secs", null, "Ramp time for power limit"),
      writable("WMaxLim_Ena", 7, 1, ENUM16, null, null, "Throttle enable/disable"),
      writable("OutPFSet", 8, 1, INT16, "cos()", "OutPFSet_SF", "Fixed power factor setting"),
      writable("OutPFSet_WinTms", 9, 1, UINT16, "Secs", null, "Time window for PF change"),
      writable("OutPFSet_RvrtTms", 10, 1, UINT16, "Secs", null, "Timeout period for PF"),
      writable("OutPFSet_RmpTms", 11, 1, UINT16, "Secs", null, "Ramp time for PF"),
      writable("OutPFSet_Ena", 12, 1, ENUM16, null, null, "Fixed PF enable/disable"),
      readOnly("VArWMaxPct", 13, 1, INT16, "% WMax", "Reactive power in % of WMax"),
      writable("VArMaxPct", 14, 1, INT16, "% VArMax", "VArPct_SF", "Reactive power in % of VArMax"),
      readOnly("VArAvalPct", 15, 1, INT16, "% VArAval", "Reactive power in % of VArAval"),
      writable("VArPct_WinTms", 16, 1, UINT16, "Secs", null, "Time window for VAR limit change"),
      writable("VArPct_RvrtTms", 17, 1, UINT16, "Secs", null, "Timeout period for VAR limit"),
      writable("VArPct_RmpTms", 18, 1, UINT16, "Secs", null, "Ramp time for VAR limit"),
      readOnly("VArPct_Mod", 19, 1, ENUM16, null, "VAR percent limit mode"),
      writable("VArPct_Ena", 20, 1, ENUM16, null, null, "Percent limit VAR enable/disable"),
      readOnly("WMaxLimPct_SF", 21, 1, SUNSSF, null, "Scale factor for power output percent"),
      readOnly("OutPFSet_SF", 22, 1, SUNSSF, null, "Scale factor for power factor"),
      readOnly("VArPct_SF", 23, 1, SUNSSF, null, "Scale factor for reactive power percent")
    );
    REGISTRY.put(MODEL_CONTROLS, SunSpecModelDefinition.of(MODEL_CONTROLS, "Immediate Controls", fields));
  }

  // ---- Model 124: Basic Storage Controls ----

  private static void registerStorageModel() {
    List<SunSpecFieldDefinition> fields = List.of(
      readOnlyScaled("WChaMax", 0, 1, UINT16, "W", "WChaMax_SF", "Maximum charge setpoint"),
      readOnlyScaled("WChaGra", 1, 1, UINT16, "% WChaMax/sec", "WChaDisChaGra_SF", "Maximum charging rate"),
      readOnlyScaled("WDisChaGra", 2, 1, UINT16, "% WChaMax/sec", "WChaDisChaGra_SF", "Maximum discharge rate"),
      writable("StorCtl_Mod", 3, 1, BITFIELD16, null, null, "Storage control mode"),
      writable("VAChaMax", 4, 1, UINT16, "VA", "VAChaMax_SF", "Maximum charging VA"),
      writable("MinRsvPct", 5, 1, UINT16, "% WChaMax", "MinRsvPct_SF", "Minimum reserve percentage"),
      readOnlyScaled("ChaState", 6, 1, UINT16, "% AhrRtg", "ChaState_SF", "Charge state percentage"),
      readOnlyScaled("StorAval", 7, 1, UINT16, "AH", "StorAval_SF", "Available storage"),
      readOnlyScaled("InBatV", 8, 1, UINT16, "V", "InBatV_SF", "Internal battery voltage"),
      readOnly("ChaSt", 9, 1, ENUM16, null, "Charge status"),
      writable("OutWRte", 10, 1, INT16, "% WChaMax", "InOutWRte_SF", "Percent of max discharge rate"),
      writable("InWRte", 11, 1, INT16, "% WChaMax", "InOutWRte_SF", "Percent of max charging rate"),
      readOnly("InOutWRte_WinTms", 12, 1, UINT16, "Secs", "Time window for charge/discharge rate change"),
      writable("InOutWRte_RvrtTms", 13, 1, UINT16, "Secs", null, "Timeout period for charge/discharge rate"),
      readOnly("InOutWRte_RmpTms", 14, 1, UINT16, "Secs", "Ramp time for charge/discharge rate"),
      writable("ChaGriSet", 15, 1, ENUM16, null, null, "Charging from grid setting"),
      readOnly("WChaMax_SF", 16, 1, SUNSSF, null, "Scale factor for maximum charge"),
      readOnly("WChaDisChaGra_SF", 17, 1, SUNSSF, null, "Scale factor for charge/discharge rate"),
      readOnly("VAChaMax_SF", 18, 1, SUNSSF, null, "Scale factor for maximum charging VA"),
      readOnly("MinRsvPct_SF", 19, 1, SUNSSF, null, "Scale factor for minimum reserve"),
      readOnly("ChaState_SF", 20, 1, SUNSSF, null, "Scale factor for charge state"),
      readOnly("StorAval_SF", 21, 1, SUNSSF, null, "Scale factor for available storage"),
      readOnly("InBatV_SF", 22, 1, SUNSSF, null, "Scale factor for battery voltage"),
      readOnly("InOutWRte_SF", 23, 1, SUNSSF, null, "Scale factor for charge/discharge rate")
    );
    REGISTRY.put(MODEL_STORAGE, SunSpecModelDefinition.of(MODEL_STORAGE, "Basic Storage Controls", fields));
  }

  // ---- Model 160: Multiple MPPT Inverter Extension ----

  private static void registerMpptModel() {
    // MPPT model has a fixed header + repeating module blocks
    // The header contains scale factors and global events
    // Each module block has 20 registers
    List<SunSpecFieldDefinition> fields = new ArrayList<>();

    // Header fields (offsets 0-7)
    fields.add(readOnly("DCA_SF", 0, 1, SUNSSF, null, "Current Scale Factor"));
    fields.add(readOnly("DCV_SF", 1, 1, SUNSSF, null, "Voltage Scale Factor"));
    fields.add(readOnly("DCW_SF", 2, 1, SUNSSF, null, "Power Scale Factor"));
    fields.add(readOnly("DCWH_SF", 3, 1, SUNSSF, null, "Energy Scale Factor"));
    fields.add(readOnly("Evt", 4, 2, BITFIELD32, null, "Global Events"));
    fields.add(readOnly("N", 6, 1, COUNT, null, "Number of Modules"));
    fields.add(readOnly("TmsPer", 7, 1, UINT16, null, "Timestamp Period"));

    // Module 1 (offsets 8-27)
    addMpptModule(fields, 1, 8);

    // Module 2 (offsets 28-47)
    addMpptModule(fields, 2, 28);

    REGISTRY.put(MODEL_MPPT, SunSpecModelDefinition.of(MODEL_MPPT, "Multiple MPPT Inverter Extension", List.copyOf(fields)));
  }

  /**
   * Adds field definitions for a single MPPT module block.
   *
   * @param fields     list to add fields to
   * @param moduleNum  module number (1-based)
   * @param baseOffset register offset where this module starts
   */
  private static void addMpptModule(List<SunSpecFieldDefinition> fields, int moduleNum, int baseOffset) {
    String prefix = "module/" + moduleNum + "/";
    fields.add(readOnly(prefix + "ID", baseOffset, 1, UINT16, null, "Input ID"));
    fields.add(readOnly(prefix + "IDStr", baseOffset + 1, 8, STRING, null, "Input ID String"));
    fields.add(readOnlyScaled(prefix + "DCA", baseOffset + 9, 1, UINT16, "A", "DCA_SF", "DC Current"));
    fields.add(readOnlyScaled(prefix + "DCV", baseOffset + 10, 1, UINT16, "V", "DCV_SF", "DC Voltage"));
    fields.add(readOnlyScaled(prefix + "DCW", baseOffset + 11, 1, UINT16, "W", "DCW_SF", "DC Power"));
    fields.add(readOnlyScaled(prefix + "DCWH", baseOffset + 12, 2, ACC32, "Wh", "DCWH_SF", "Lifetime Energy"));
    fields.add(readOnly(prefix + "Tms", baseOffset + 14, 2, UINT32, "Secs", "Timestamp"));
    fields.add(readOnly(prefix + "Tmp", baseOffset + 16, 1, INT16, "C", "Temperature"));
    fields.add(readOnly(prefix + "DCSt", baseOffset + 17, 1, ENUM16, null, "Operating State"));
    fields.add(readOnly(prefix + "DCEvt", baseOffset + 18, 2, BITFIELD32, null, "Module Events"));
  }
}
