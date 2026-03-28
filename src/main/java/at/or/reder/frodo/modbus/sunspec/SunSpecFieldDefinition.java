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
 */
public record SunSpecFieldDefinition(
  String name,
  int offset,
  int size,
  SunSpecDataType dataType,
  String units,
  String scaleFactor,
  boolean writable,
  String description
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
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, null, false, description);
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
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, scaleFactor, false, description);
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
    return new SunSpecFieldDefinition(name, offset, size, dataType, units, scaleFactor, true, description);
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
