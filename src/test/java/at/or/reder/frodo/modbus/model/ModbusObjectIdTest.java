package at.or.reder.frodo.modbus.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModbusObjectIdTest {

  @Test
  void testStandardObjectIds() {
    assertEquals(0x00, ModbusObjectId.VENDOR_NAME);
    assertEquals(0x01, ModbusObjectId.PRODUCT_CODE);
    assertEquals(0x02, ModbusObjectId.MAJOR_MINOR_REVISION);
    assertEquals(0x03, ModbusObjectId.VENDOR_URL);
    assertEquals(0x04, ModbusObjectId.PRODUCT_NAME);
    assertEquals(0x05, ModbusObjectId.MODEL_NAME);
    assertEquals(0x06, ModbusObjectId.USER_APPLICATION_NAME);
  }

  @Test
  void testVendorSpecificRange() {
    assertEquals(0x80, ModbusObjectId.VENDOR_SPECIFIC_START);
    assertEquals(0xFF, ModbusObjectId.VENDOR_SPECIFIC_END);
  }

  @Test
  void testIsVendorSpecific() {
    assertFalse(ModbusObjectId.isVendorSpecific(0x00));
    assertFalse(ModbusObjectId.isVendorSpecific(0x06));
    assertFalse(ModbusObjectId.isVendorSpecific(0x7F));
    assertTrue(ModbusObjectId.isVendorSpecific(0x80));
    assertTrue(ModbusObjectId.isVendorSpecific(0xAB));
    assertTrue(ModbusObjectId.isVendorSpecific(0xFF));
  }

  @Test
  void testNameOfStandardObjects() {
    assertEquals("VendorName", ModbusObjectId.nameOf(0x00));
    assertEquals("ProductCode", ModbusObjectId.nameOf(0x01));
    assertEquals("MajorMinorRevision", ModbusObjectId.nameOf(0x02));
    assertEquals("VendorUrl", ModbusObjectId.nameOf(0x03));
    assertEquals("ProductName", ModbusObjectId.nameOf(0x04));
    assertEquals("ModelName", ModbusObjectId.nameOf(0x05));
    assertEquals("UserApplicationName", ModbusObjectId.nameOf(0x06));
  }

  @Test
  void testNameOfVendorSpecific() {
    String name = ModbusObjectId.nameOf(0x80);
    assertTrue(name.startsWith("VendorSpecific"));
    assertTrue(name.contains("0x80"));
  }

  @Test
  void testNameOfUnknown() {
    String name = ModbusObjectId.nameOf(0x07);
    assertTrue(name.startsWith("Unknown"));
    assertTrue(name.contains("0x7"));
  }
}
