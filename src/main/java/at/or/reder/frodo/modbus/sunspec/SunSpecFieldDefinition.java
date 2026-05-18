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
 * Defines a single data point (field) within a SunSpec model.
 *
 * <p>Each field has a name, offset within the model's data block,
 * size in registers, data type, and optional metadata such as units
 * and scale factor reference.</p>
 *
 * @param name             field name (e.g. "A", "PhVphA", "Mn")
 * @param offset           register offset within the model data block (0-based, after ID and L)
 * @param size             number of 16-bit registers this field occupies
 * @param dataType         SunSpec data type
 * @param units            measurement units (e.g. "A", "V", "W"), may be null
 * @param scaleFactor      name of the scale factor field (e.g. "A_SF"), null for Float models
 * @param writable         true if the field is writable (R/W)
 * @param description      human-readable description of the field
 * @param conversionFactor additional multiplier applied after scale factor (default 1.0).
 *                         Use 0.01 to convert Pct (0–100) to cos-phi ratio (0–1).
 */
public record SunSpecFieldDefinition(
  String name,
  int offset,
  int size,
  SunSpecDataType dataType,
  String units,
  String scaleFactor,
  boolean writable,
  String description,
  double conversionFactor
) {

  /**
   * Creates a read-only field definition without scale factor.
   *
   * @param name        field name
   * @param offset      register offset
   * @param size        register count
   * @param dataType    data type
   * @param units       units string (may be null)
   * @param description description
   * @return field definition
   */
  public static SunSpecFieldDefinition readOnly(String name, int offset, int size,
                                                 SunSpecDataType dataType, String units,
                                                 String description) {
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, null, false, description, 1.0);
  }

  /**
   * Creates a read-only field definition with a scale factor reference.
   *
   * @param name        field name
   * @param offset      register offset
   * @param size        register count
   * @param dataType    data type
   * @param units       units string (may be null)
   * @param scaleFactor name of the SF field
   * @param description description
   * @return field definition
   */
  public static SunSpecFieldDefinition readOnlyScaled(String name, int offset, int size,
                                                       SunSpecDataType dataType, String units,
                                                       String scaleFactor, String description) {
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, scaleFactor, false, description, 1.0);
  }

  /**
   * Creates a writable field definition.
   *
   * @param name        field name
   * @param offset      register offset
   * @param size        register count
   * @param dataType    data type
   * @param units       units string (may be null)
   * @param scaleFactor name of the SF field (may be null)
   * @param description description
   * @return field definition
   */
  public static SunSpecFieldDefinition writable(String name, int offset, int size,
                                                 SunSpecDataType dataType, String units,
                                                 String scaleFactor, String description) {
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, scaleFactor, true, description, 1.0);
  }

  /**
   * Creates a read-only field with an additional conversion factor applied after decoding.
   * Use this for fields reported in percent that should be exposed as a ratio (0–1),
   * e.g. PF on inverter Int+SF models where PF_SF=-1 yields values in the 0–100 range.
   *
   * @param name             field name
   * @param offset           register offset
   * @param size             register count
   * @param dataType         data type
   * @param units            target units string (e.g. "cos()")
   * @param scaleFactor      name of the SF field
   * @param description      description
   * @param conversionFactor multiplier applied after SF scaling (e.g. 0.01 to convert Pct→ratio)
   * @return field definition
   */
  public static SunSpecFieldDefinition readOnlyScaledWithFactor(String name, int offset, int size,
                                                                  SunSpecDataType dataType, String units,
                                                                  String scaleFactor, String description,
                                                                  double conversionFactor) {
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, scaleFactor, false, description, conversionFactor);
  }

  /**
   * Creates a read-only field (no SF) with an additional conversion factor applied after decoding.
   * Use this for Float-format fields reported in percent, e.g. PF on inverter Float models.
   *
   * @param name             field name
   * @param offset           register offset
   * @param size             register count
   * @param dataType         data type
   * @param units            target units string (e.g. "cos()")
   * @param description      description
   * @param conversionFactor multiplier applied after decoding (e.g. 0.01 to convert Pct→ratio)
   * @return field definition
   */
  public static SunSpecFieldDefinition readOnlyWithFactor(String name, int offset, int size,
                                                           SunSpecDataType dataType, String units,
                                                           String description, double conversionFactor) {
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, null, false, description, conversionFactor);
  }

  /**
   * Whether this field has a scale factor reference.
   *
   * @return true if scale factor is defined
   */
  public boolean hasScaleFactor() {
    return scaleFactor != null && !scaleFactor.isEmpty();
  }
}
