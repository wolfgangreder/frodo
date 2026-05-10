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

/**
 * Constants for the SunSpec Modbus protocol.
 *
 * <p>SunSpec defines a standardized Modbus register map for solar
 * and storage devices. The map starts with a "SunS" signature at
 * a well-known base address, followed by a chain of model blocks.</p>
 *
 * <p><b>Protocol References:</b></p>
 * <ul>
 *   <li>Fronius Gen24 Register Maps: {@code refdoc/gen24-modbus-api-external-docs/}</li>
 *   <li>Float Models: Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx</li>
 *   <li>Int+SF Models: Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_ROW.xlsx</li>
 *   <li>SunSpec Base Address: Register 40000 (SunS signature 0x53756e53)</li>
 * </ul>
 *
 * @see <a href="https://sunspec.org/">SunSpec Alliance</a>
 */
public final class SunSpecConstants {

  private SunSpecConstants() {
    // Utility class
  }

  /**
   * SunSpec "SunS" signature value (0x53756e53).
   * Read as a uint32 from the first two holding registers.
   */
  public static final long SUNSPEC_SIGNATURE = 0x53756e53L;

  /**
   * Default SunSpec base address in Modbus holding register space.
   * SunSpec address 40001 corresponds to Modbus holding register 40000
   * (zero-based protocol address).
   */
  public static final int DEFAULT_BASE_ADDRESS = 40000;

  /**
   * Alternative base addresses to probe if the default fails.
   * Some older devices use 0 or 50000.
   */
  public static final int[] ALTERNATE_BASE_ADDRESSES = {0, 50000};

  /**
   * Sentinel model ID for Solar API site-level parameters.
   *
   * <p>Solar API parameters are not part of the SunSpec protocol; they are read
   * from the Fronius Solar API (cached by {@code SolarApiMetricsService}).
   * Using a negative value keeps the {@code sunspecModelId} column type unchanged
   * while cleanly separating Solar API parameters from real SunSpec models.</p>
   */
  public static final int MODEL_ID_SOLAR_API = -1;

  /**
   * Model ID indicating the end of the SunSpec model chain.
   */
  public static final int END_MODEL_ID = 0xFFFF;

  /**
   * Maximum number of models to scan before giving up.
   * Safety limit to prevent infinite loops on malformed devices.
   */
  public static final int MAX_MODEL_SCAN_DEPTH = 50;

  /**
   * Maximum number of registers to read in a single FC 0x03 request.
   * Modbus protocol limit is 125 registers per request.
   */
  public static final int MAX_REGISTERS_PER_READ = 125;

  // ---- Well-known SunSpec Model IDs ----

  /** Common model - device identification. */
  public static final int MODEL_COMMON = 1;

  /** Single phase inverter model (Int&SF format). */
  public static final int MODEL_INVERTER_SINGLE_PHASE = 101;

  /** Split phase inverter model (Int&SF format). */
  public static final int MODEL_INVERTER_SPLIT_PHASE = 102;

  /** Three phase inverter model (Int&SF format). */
  public static final int MODEL_INVERTER_THREE_PHASE = 103;

  /** Single phase inverter model (Float format). */
  public static final int MODEL_INVERTER_SINGLE_PHASE_FLOAT = 111;

  /** Split phase inverter model (Float format). */
  public static final int MODEL_INVERTER_SPLIT_PHASE_FLOAT = 112;

  /** Three phase inverter model (Float format). */
  public static final int MODEL_INVERTER_THREE_PHASE_FLOAT = 113;

  /** Nameplate ratings model. */
  public static final int MODEL_NAMEPLATE = 120;

  /** Basic settings model. */
  public static final int MODEL_SETTINGS = 121;

  /** Extended measurements and status model. */
  public static final int MODEL_STATUS = 122;

  /** Immediate controls model. */
  public static final int MODEL_CONTROLS = 123;

  /** Basic storage controls model. */
  public static final int MODEL_STORAGE = 124;

  /** Multiple MPPT inverter extension model. */
  public static final int MODEL_MPPT = 160;

  /** Single phase (AN or AB) meter model (Int+SF format). */
  public static final int MODEL_METER_SINGLE_PHASE = 201;

  /** Split single phase (ABN) meter model (Int+SF format). */
  public static final int MODEL_METER_SPLIT_PHASE = 202;

  /** Wye-connect three phase (ABCN) meter model (Int+SF format). */
  public static final int MODEL_METER_THREE_PHASE_WYE = 203;

  /** Delta-connect three phase (ABC) meter model (Int+SF format). */
  public static final int MODEL_METER_THREE_PHASE_DELTA = 204;

  /** Single phase (AN or AB) meter model (Float format). */
  public static final int MODEL_METER_SINGLE_PHASE_FLOAT = 211;

  /** Split single phase (ABN) meter model (Float format). */
  public static final int MODEL_METER_SPLIT_PHASE_FLOAT = 212;

  /** Wye-connect three phase (ABCN) meter model (Float format). */
  public static final int MODEL_METER_THREE_PHASE_WYE_FLOAT = 213;

  /** Delta-connect three phase (ABC) meter model (Float format). */
  public static final int MODEL_METER_THREE_PHASE_DELTA_FLOAT = 214;

  /**
   * Returns {@code true} when the model ID refers to a Solar API parameter
   * rather than a real SunSpec model.
   *
   * <p>All negative model IDs are reserved for non-SunSpec data sources.</p>
   *
   * @param modelId the model ID to check
   * @return true for Solar API parameters (modelId &lt; 0)
   */
  public static boolean isSolarApiModel(int modelId) {
    return modelId < 0;
  }

  /**
   * Checks whether the given model ID represents a Float-format inverter model.
   *
   * @param modelId the model ID to check
   * @return true if the model is a float-format inverter model (111, 112, 113)
   */
  public static boolean isFloatInverterModel(int modelId) {
    return modelId >= MODEL_INVERTER_SINGLE_PHASE_FLOAT
      && modelId <= MODEL_INVERTER_THREE_PHASE_FLOAT;
  }

  /**
   * Checks whether the given model ID represents an Int&SF-format inverter model.
   *
   * @param modelId the model ID to check
   * @return true if the model is an int+SF inverter model (101, 102, 103)
   */
  public static boolean isIntSfInverterModel(int modelId) {
    return modelId >= MODEL_INVERTER_SINGLE_PHASE
      && modelId <= MODEL_INVERTER_THREE_PHASE;
  }

  /**
   * Checks whether the given model ID is any inverter model (Int&SF or Float).
   *
   * @param modelId the model ID to check
   * @return true if the model is an inverter model
   */
  public static boolean isInverterModel(int modelId) {
    return isIntSfInverterModel(modelId) || isFloatInverterModel(modelId);
  }

  /**
   * Checks whether the given model ID represents a Float-format meter model.
   *
   * @param modelId the model ID to check
   * @return true if the model is a float-format meter model (211-214)
   */
  public static boolean isFloatMeterModel(int modelId) {
    return modelId >= MODEL_METER_SINGLE_PHASE_FLOAT
      && modelId <= MODEL_METER_THREE_PHASE_DELTA_FLOAT;
  }

  /**
   * Checks whether the given model ID represents an Int&SF-format meter model.
   *
   * @param modelId the model ID to check
   * @return true if the model is an int+SF meter model (201-204)
   */
  public static boolean isIntSfMeterModel(int modelId) {
    return modelId >= MODEL_METER_SINGLE_PHASE
      && modelId <= MODEL_METER_THREE_PHASE_DELTA;
  }

  /**
   * Checks whether the given model ID is any meter model (Int&SF or Float).
   *
   * @param modelId the model ID to check
   * @return true if the model is a meter model
   */
  public static boolean isMeterModel(int modelId) {
    return isIntSfMeterModel(modelId) || isFloatMeterModel(modelId);
  }

  /**
   * Returns a human-readable name for a SunSpec model ID.
   *
   * @param modelId the model ID
   * @return model name string
   */
  public static String modelName(int modelId) {
    return switch (modelId) {
      case MODEL_ID_SOLAR_API -> "Solar API Site";
      case MODEL_COMMON -> "Common";
      case MODEL_INVERTER_SINGLE_PHASE -> "Inverter (Single Phase, Int+SF)";
      case MODEL_INVERTER_SPLIT_PHASE -> "Inverter (Split Phase, Int+SF)";
      case MODEL_INVERTER_THREE_PHASE -> "Inverter (Three Phase, Int+SF)";
      case MODEL_INVERTER_SINGLE_PHASE_FLOAT -> "Inverter (Single Phase, Float)";
      case MODEL_INVERTER_SPLIT_PHASE_FLOAT -> "Inverter (Split Phase, Float)";
      case MODEL_INVERTER_THREE_PHASE_FLOAT -> "Inverter (Three Phase, Float)";
      case MODEL_NAMEPLATE -> "Nameplate Ratings";
      case MODEL_SETTINGS -> "Basic Settings";
      case MODEL_STATUS -> "Extended Measurements & Status";
      case MODEL_CONTROLS -> "Immediate Controls";
      case MODEL_STORAGE -> "Basic Storage Controls";
      case MODEL_MPPT -> "Multiple MPPT Inverter Extension";
      case MODEL_METER_SINGLE_PHASE -> "Meter (Single Phase AN/AB, Int+SF)";
      case MODEL_METER_SPLIT_PHASE -> "Meter (Split Phase ABN, Int+SF)";
      case MODEL_METER_THREE_PHASE_WYE -> "Meter (Three Phase WYE, Int+SF)";
      case MODEL_METER_THREE_PHASE_DELTA -> "Meter (Three Phase Delta, Int+SF)";
      case MODEL_METER_SINGLE_PHASE_FLOAT -> "Meter (Single Phase AN/AB, Float)";
      case MODEL_METER_SPLIT_PHASE_FLOAT -> "Meter (Split Phase ABN, Float)";
      case MODEL_METER_THREE_PHASE_WYE_FLOAT -> "Meter (Three Phase WYE, Float)";
      case MODEL_METER_THREE_PHASE_DELTA_FLOAT -> "Meter (Three Phase Delta, Float)";
      case END_MODEL_ID -> "End Block";
      default -> "Unknown (" + modelId + ")";
    };
  }
}
