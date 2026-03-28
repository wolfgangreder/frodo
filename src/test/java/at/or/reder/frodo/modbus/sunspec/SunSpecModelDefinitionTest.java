package at.or.reder.frodo.modbus.sunspec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecModelDefinition}.
 */
class SunSpecModelDefinitionTest {

  @Test
  void testOfFactory() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("A", 0, 2, SunSpecDataType.FLOAT32, "A", "AC Current"),
      SunSpecFieldDefinition.readOnly("W", 2, 2, SunSpecDataType.FLOAT32, "W", "AC Power")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(113, "Test Inverter", fields);

    assertEquals(113, def.modelId());
    assertEquals("Test Inverter", def.name());
    assertEquals(2, def.fields().size());
    assertNotNull(def.fieldsByName());
    assertEquals(2, def.fieldsByName().size());
  }

  @Test
  void testFieldByName() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("A", 0, 2, SunSpecDataType.FLOAT32, "A", "AC Current"),
      SunSpecFieldDefinition.readOnly("W", 2, 2, SunSpecDataType.FLOAT32, "W", "AC Power")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(113, "Test", fields);

    assertNotNull(def.field("A"));
    assertEquals("AC Current", def.field("A").description());
    assertNotNull(def.field("W"));
    assertNull(def.field("nonexistent"));
  }

  @Test
  void testHasWritableFieldsTrue() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("A", 0, 1, SunSpecDataType.UINT16, "A", "Current"),
      SunSpecFieldDefinition.writable("Conn", 1, 1, SunSpecDataType.ENUM16, null, null, "Connect")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(123, "Controls", fields);
    assertTrue(def.hasWritableFields());
  }

  @Test
  void testHasWritableFieldsFalse() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("A", 0, 2, SunSpecDataType.FLOAT32, "A", "Current"),
      SunSpecFieldDefinition.readOnly("W", 2, 2, SunSpecDataType.FLOAT32, "W", "Power")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(113, "Inverter", fields);
    assertFalse(def.hasWritableFields());
  }

  @Test
  void testTotalRegisters() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("A", 0, 2, SunSpecDataType.FLOAT32, "A", "Current"),
      SunSpecFieldDefinition.readOnly("W", 2, 2, SunSpecDataType.FLOAT32, "W", "Power"),
      SunSpecFieldDefinition.readOnly("Hz", 4, 2, SunSpecDataType.FLOAT32, "Hz", "Frequency")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(113, "Test", fields);
    // Last field at offset 4, size 2 -> total = 4 + 2 = 6
    assertEquals(6, def.totalRegisters());
  }

  @Test
  void testTotalRegistersEmptyFields() {
    SunSpecModelDefinition def = SunSpecModelDefinition.of(1, "Empty", List.of());
    assertEquals(0, def.totalRegisters());
  }

  @Test
  void testTotalRegistersWithString() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("Mn", 0, 16, SunSpecDataType.STRING, null, "Manufacturer"),
      SunSpecFieldDefinition.readOnly("DA", 64, 1, SunSpecDataType.UINT16, null, "Device Address")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(1, "Common", fields);
    assertEquals(65, def.totalRegisters());
  }

  @Test
  void testFieldsAreUnmodifiable() {
    List<SunSpecFieldDefinition> fields = List.of(
      SunSpecFieldDefinition.readOnly("A", 0, 1, SunSpecDataType.UINT16, "A", "Current")
    );

    SunSpecModelDefinition def = SunSpecModelDefinition.of(1, "Test", fields);

    var thrown = org.junit.jupiter.api.Assertions.assertThrows(
      UnsupportedOperationException.class,
      () -> def.fields().add(SunSpecFieldDefinition.readOnly("B", 1, 1, SunSpecDataType.UINT16, "B", "Test"))
    );
    assertNotNull(thrown);
  }
}
