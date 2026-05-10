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
