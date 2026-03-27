package at.or.reder.frodo.modbus.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadDeviceIdCodeTest {

  @Test
  void testBasicCode() {
    assertEquals(0x01, ReadDeviceIdCode.BASIC.getCode());
  }

  @Test
  void testRegularCode() {
    assertEquals(0x02, ReadDeviceIdCode.REGULAR.getCode());
  }

  @Test
  void testExtendedCode() {
    assertEquals(0x03, ReadDeviceIdCode.EXTENDED.getCode());
  }

  @Test
  void testSpecificCode() {
    assertEquals(0x04, ReadDeviceIdCode.SPECIFIC.getCode());
  }

  @Test
  void testFromCodeValid() {
    assertEquals(ReadDeviceIdCode.BASIC, ReadDeviceIdCode.fromCode(0x01));
    assertEquals(ReadDeviceIdCode.REGULAR, ReadDeviceIdCode.fromCode(0x02));
    assertEquals(ReadDeviceIdCode.EXTENDED, ReadDeviceIdCode.fromCode(0x03));
    assertEquals(ReadDeviceIdCode.SPECIFIC, ReadDeviceIdCode.fromCode(0x04));
  }

  @Test
  void testFromCodeInvalid() {
    assertThrows(IllegalArgumentException.class, () -> ReadDeviceIdCode.fromCode(0x00));
    assertThrows(IllegalArgumentException.class, () -> ReadDeviceIdCode.fromCode(0x05));
    assertThrows(IllegalArgumentException.class, () -> ReadDeviceIdCode.fromCode(0xFF));
  }

  @Test
  void testEnumValues() {
    ReadDeviceIdCode[] values = ReadDeviceIdCode.values();
    assertEquals(4, values.length);
  }
}
