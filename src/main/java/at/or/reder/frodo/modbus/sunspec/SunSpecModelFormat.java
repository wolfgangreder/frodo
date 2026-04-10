package at.or.reder.frodo.modbus.sunspec;

/**
 * SunSpec register map format for inverter models.
 *
 * <p>SunSpec defines two variants of inverter models:</p>
 * <ul>
 *   <li><b>INT_SF</b> — Integer values with separate Scale Factor registers
 *       (models 101, 102, 103). Uses {@code UINT16}/{@code INT16} data types
 *       with {@code SUNSSF} scale factor fields. 50 registers per model.</li>
 *   <li><b>FLOAT</b> — IEEE 754 floating-point values
 *       (models 111, 112, 113). Uses {@code FLOAT32} data type.
 *       60 registers per model.</li>
 * </ul>
 *
 * <p>A device exposes either Int&amp;SF or Float inverter models in its
 * SunSpec model chain, not both. Models 120–160 (Nameplate, Settings,
 * Status, Controls, Storage, MPPT) are format-agnostic and identical
 * in both register maps.</p>
 *
 * <p>Configure via {@code frodo.sunspec.model-format} property.
 * Default is {@link #INT_SF}.</p>
 *
 * @see SunSpecConstants#isIntSfInverterModel(int)
 * @see SunSpecConstants#isFloatInverterModel(int)
 */
public enum SunSpecModelFormat {

  /**
   * Integer with Scale Factor format (models 101, 102, 103).
   */
  INT_SF,

  /**
   * IEEE 754 Float format (models 111, 112, 113).
   */
  FLOAT;

  /**
   * Checks whether the given model ID matches this format.
   *
   * <p>Only applies to inverter models (101-103, 111-113).
   * Non-inverter models are considered format-agnostic and
   * this method returns {@code false} for them.</p>
   *
   * @param modelId the SunSpec model ID to check
   * @return true if the model ID is an inverter model matching this format
   */
  public boolean matchesInverterModel(int modelId) {
    return switch (this) {
      case INT_SF -> SunSpecConstants.isIntSfInverterModel(modelId);
      case FLOAT -> SunSpecConstants.isFloatInverterModel(modelId);
    };
  }

  /**
   * Checks whether the given model ID is a meter model matching this format.
   *
   * <p>Only applies to meter models (201-204, 211-214).
   * Non-meter models are considered format-agnostic and
   * this method returns {@code false} for them.</p>
   *
   * @param modelId the SunSpec model ID to check
   * @return true if the model ID is a meter model matching this format
   */
  public boolean matchesMeterModel(int modelId) {
    return switch (this) {
      case INT_SF -> SunSpecConstants.isIntSfMeterModel(modelId);
      case FLOAT -> SunSpecConstants.isFloatMeterModel(modelId);
    };
  }
}
