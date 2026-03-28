package at.or.reder.frodo.modbus.sunspec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the structure of a SunSpec model including its model ID,
 * name, expected length, and all field definitions.
 *
 * <p>The field list does NOT include the ID and L (length) header
 * registers that precede every model block. The offsets in field
 * definitions are relative to the first data register after L.</p>
 *
 * @param modelId     SunSpec model ID (e.g. 1, 113, 120)
 * @param name        human-readable model name
 * @param fields      ordered list of field definitions
 * @param fieldsByName map of field name to definition for fast lookup
 */
public record SunSpecModelDefinition(
  int modelId,
  String name,
  List<SunSpecFieldDefinition> fields,
  Map<String, SunSpecFieldDefinition> fieldsByName
) {

  /**
   * Creates a model definition from a model ID, name, and field list.
   * The fieldsByName map is automatically constructed.
   *
   * @param modelId SunSpec model ID
   * @param name    model name
   * @param fields  ordered field definitions
   * @return model definition
   */
  public static SunSpecModelDefinition of(int modelId, String name,
                                           List<SunSpecFieldDefinition> fields) {
    Map<String, SunSpecFieldDefinition> byName = new LinkedHashMap<>();
    for (SunSpecFieldDefinition field : fields) {
      byName.put(field.name(), field);
    }
    return new SunSpecModelDefinition(
      modelId,
      name,
      Collections.unmodifiableList(fields),
      Collections.unmodifiableMap(byName)
    );
  }

  /**
   * Gets a field definition by name.
   *
   * @param name field name
   * @return field definition, or null if not found
   */
  public SunSpecFieldDefinition field(String name) {
    return fieldsByName.get(name);
  }

  /**
   * Checks whether this model has any writable fields.
   *
   * @return true if at least one field is writable
   */
  public boolean hasWritableFields() {
    return fields.stream().anyMatch(SunSpecFieldDefinition::writable);
  }

  /**
   * Returns the total number of data registers (excluding ID and L header).
   *
   * @return total register count
   */
  public int totalRegisters() {
    if (fields.isEmpty()) {
      return 0;
    }
    SunSpecFieldDefinition last = fields.get(fields.size() - 1);
    return last.offset() + last.size();
  }
}
