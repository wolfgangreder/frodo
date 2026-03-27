package at.or.reder.frodo.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ModbusException.
 */
class ModbusExceptionTest {

  @Test
  void testExceptionCreation() {
    ModbusException ex = new ModbusException(0x2B, 0x01);

    assertEquals(0x2B, ex.getFunctionCode());
    assertEquals(0x01, ex.getExceptionCode());
    assertTrue(ex.getMessage().contains("0x2B") || ex.getMessage().contains("0x2b"),
      "Message should contain function code: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("Illegal Function"));
  }

  @Test
  void testExceptionCodes() {
    assertEquals("Illegal Function", ModbusException.describeExceptionCode(0x01));
    assertEquals("Illegal Data Address", ModbusException.describeExceptionCode(0x02));
    assertEquals("Illegal Data Value", ModbusException.describeExceptionCode(0x03));
    assertEquals("Server Device Failure", ModbusException.describeExceptionCode(0x04));
    assertEquals("Acknowledge", ModbusException.describeExceptionCode(0x05));
    assertEquals("Server Device Busy", ModbusException.describeExceptionCode(0x06));
  }

  @Test
  void testUnknownExceptionCode() {
    String desc = ModbusException.describeExceptionCode(0xAA);
    assertTrue(desc.startsWith("Unknown"));
    assertTrue(desc.contains("0xaa"));
  }

  @Test
  void testMessageFormat() {
    ModbusException ex = new ModbusException(0x03, 0x04);
    // Message should contain FC and exception code
    assertTrue(ex.getMessage().contains("FC=0x03"));
    assertTrue(ex.getMessage().contains("0x04"));
    assertTrue(ex.getMessage().contains("Server Device Failure"));
  }
}
