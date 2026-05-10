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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for FC 0x06 (Write Single Register) and FC 0x10 (Write Multiple Registers)
 * frame building and response parsing in {@link ModbusTcpService}.
 */
class ModbusTcpServiceWriteTest {

  // ---- FC 0x06: Write Single Register Request ----

  @Test
  void testBuildWriteSingleRegisterRequest() {
    byte[] frame = ModbusTcpService.buildWriteSingleRegisterRequest(1, 0x0010, 0x0003, 42);

    // MBAP (7) + PDU (5) = 12 bytes
    assertEquals(12, frame.length);

    // MBAP header
    assertEquals(0x00, frame[0]);       // Transaction ID high
    assertEquals(42, frame[1] & 0xFF);  // Transaction ID low
    assertEquals(0x00, frame[2]);       // Protocol ID high
    assertEquals(0x00, frame[3]);       // Protocol ID low
    assertEquals(0x00, frame[4]);       // Length high
    assertEquals(0x06, frame[5]);       // Length low (1 unitId + 5 PDU)
    assertEquals(0x01, frame[6]);       // Unit ID

    // PDU
    assertEquals(0x06, frame[7] & 0xFF);  // Function code: Write Single Register
    assertEquals(0x00, frame[8] & 0xFF);  // Address high
    assertEquals(0x10, frame[9] & 0xFF);  // Address low
    assertEquals(0x00, frame[10] & 0xFF); // Value high
    assertEquals(0x03, frame[11] & 0xFF); // Value low
  }

  @Test
  void testBuildWriteSingleRegisterRequestLargeAddress() {
    byte[] frame = ModbusTcpService.buildWriteSingleRegisterRequest(1, 0x9C40, 0x1234, 1);

    // Address = 40000 = 0x9C40
    assertEquals(0x9C, frame[8] & 0xFF);
    assertEquals(0x40, frame[9] & 0xFF);

    // Value = 0x1234
    assertEquals(0x12, frame[10] & 0xFF);
    assertEquals(0x34, frame[11] & 0xFF);
  }

  @Test
  void testBuildWriteSingleRegisterRequestMaxValue() {
    byte[] frame = ModbusTcpService.buildWriteSingleRegisterRequest(1, 0, 0xFFFF, 1);

    assertEquals(0xFF, frame[10] & 0xFF);
    assertEquals(0xFF, frame[11] & 0xFF);
  }

  // ---- FC 0x06: Write Single Register Response ----

  @Test
  void testParseWriteSingleRegisterResponse() {
    // Normal echo response: MBAP + FC + Address + Value
    byte[] response = {
      0x00, 0x2A,           // Transaction ID
      0x00, 0x00,           // Protocol ID
      0x00, 0x06,           // Length
      0x01,                 // Unit ID
      0x06,                 // Function code: Write Single Register
      0x00, 0x10,           // Address
      0x00, 0x03            // Value
    };

    assertDoesNotThrow(() -> ModbusTcpService.parseWriteSingleRegisterResponse(response));
  }

  @Test
  void testParseWriteSingleRegisterResponseException() {
    // Exception response: FC with high bit set (0x86 = 0x06 | 0x80)
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01,
      (byte) 0x86,          // Exception FC
      0x02                  // Exception code: Illegal Data Address
    };

    ModbusException ex = assertThrows(ModbusException.class,
      () -> ModbusTcpService.parseWriteSingleRegisterResponse(response));
    assertEquals(0x06, ex.getFunctionCode());
    assertEquals(0x02, ex.getExceptionCode());
  }

  @Test
  void testParseWriteSingleRegisterResponseTooShort() {
    byte[] response = {0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x01};
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteSingleRegisterResponse(response));
  }

  @Test
  void testParseWriteSingleRegisterResponseNull() {
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteSingleRegisterResponse(null));
  }

  @Test
  void testParseWriteSingleRegisterResponseWrongFc() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01,
      0x03,                 // Wrong FC (should be 0x06)
      0x00, 0x10, 0x00, 0x03
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteSingleRegisterResponse(response));
  }

  @Test
  void testParseWriteSingleRegisterResponseBodyTooShort() {
    // FC correct but body too short (only 8 + 1 = 9 bytes, need 12)
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01,
      0x06,                 // Correct FC
      0x00, 0x10            // Only address high+low, missing value
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteSingleRegisterResponse(response));
  }

  // ---- FC 0x10: Write Multiple Registers Request ----

  @Test
  void testBuildWriteMultipleRegistersRequest() {
    int[] values = {0x000A, 0x0064};
    byte[] frame = ModbusTcpService.buildWriteMultipleRegistersRequest(1, 0x0010, values, 42);

    // MBAP (7) + FC(1) + Addr(2) + Qty(2) + ByteCount(1) + Data(4) = 17 bytes
    assertEquals(17, frame.length);

    // MBAP header
    assertEquals(42, frame[1] & 0xFF);  // Transaction ID
    assertEquals(0x01, frame[6]);       // Unit ID

    // PDU
    assertEquals(0x10, frame[7] & 0xFF);  // Function code: Write Multiple Registers
    assertEquals(0x00, frame[8] & 0xFF);  // Start address high
    assertEquals(0x10, frame[9] & 0xFF);  // Start address low
    assertEquals(0x00, frame[10] & 0xFF); // Quantity high
    assertEquals(0x02, frame[11] & 0xFF); // Quantity low (2 registers)
    assertEquals(0x04, frame[12] & 0xFF); // Byte count (4 bytes)
    assertEquals(0x00, frame[13] & 0xFF); // Value 1 high
    assertEquals(0x0A, frame[14] & 0xFF); // Value 1 low (10)
    assertEquals(0x00, frame[15] & 0xFF); // Value 2 high
    assertEquals(0x64, frame[16] & 0xFF); // Value 2 low (100)
  }

  @Test
  void testBuildWriteMultipleRegistersRequestSingleValue() {
    int[] values = {0x1234};
    byte[] frame = ModbusTcpService.buildWriteMultipleRegistersRequest(1, 0, values, 1);

    // MBAP (7) + FC(1) + Addr(2) + Qty(2) + ByteCount(1) + Data(2) = 15 bytes
    assertEquals(15, frame.length);

    assertEquals(0x00, frame[10] & 0xFF); // Quantity high = 0
    assertEquals(0x01, frame[11] & 0xFF); // Quantity low = 1
    assertEquals(0x02, frame[12] & 0xFF); // Byte count = 2
    assertEquals(0x12, frame[13] & 0xFF); // Value high
    assertEquals(0x34, frame[14] & 0xFF); // Value low
  }

  @Test
  void testBuildWriteMultipleRegistersRequestLargeValues() {
    int[] values = {0xFFFF, 0x8000, 0x0001};
    byte[] frame = ModbusTcpService.buildWriteMultipleRegistersRequest(200, 0x9C40, values, 100);

    assertEquals((byte) 200, frame[6]);    // Unit ID = 200
    assertEquals(0x03, frame[11] & 0xFF);  // Quantity = 3
    assertEquals(0x06, frame[12] & 0xFF);  // Byte count = 6
    assertEquals(0xFF, frame[13] & 0xFF);  // Value 1 high
    assertEquals(0xFF, frame[14] & 0xFF);  // Value 1 low
    assertEquals(0x80, frame[15] & 0xFF);  // Value 2 high
    assertEquals(0x00, frame[16] & 0xFF);  // Value 2 low
    assertEquals(0x00, frame[17] & 0xFF);  // Value 3 high
    assertEquals(0x01, frame[18] & 0xFF);  // Value 3 low
  }

  // ---- FC 0x10: Write Multiple Registers Response ----

  @Test
  void testParseWriteMultipleRegistersResponse() {
    byte[] response = {
      0x00, 0x2A,           // Transaction ID
      0x00, 0x00,           // Protocol ID
      0x00, 0x06,           // Length
      0x01,                 // Unit ID
      0x10,                 // Function code: Write Multiple Registers
      0x00, 0x10,           // Start address
      0x00, 0x02            // Quantity written = 2
    };

    assertDoesNotThrow(() -> ModbusTcpService.parseWriteMultipleRegistersResponse(response, 2));
  }

  @Test
  void testParseWriteMultipleRegistersResponseException() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01,
      (byte) 0x90,          // Exception FC (0x10 | 0x80)
      0x03                  // Exception code: Illegal Data Value
    };

    ModbusException ex = assertThrows(ModbusException.class,
      () -> ModbusTcpService.parseWriteMultipleRegistersResponse(response, 2));
    assertEquals(0x10, ex.getFunctionCode());
    assertEquals(0x03, ex.getExceptionCode());
  }

  @Test
  void testParseWriteMultipleRegistersResponseWrongCount() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01,
      0x10,                 // FC
      0x00, 0x10,           // Address
      0x00, 0x01            // Quantity = 1, but expected 2
    };

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteMultipleRegistersResponse(response, 2));
    assertTrue(ex.getMessage().contains("Expected 2"));
    assertTrue(ex.getMessage().contains("reported 1"));
  }

  @Test
  void testParseWriteMultipleRegistersResponseTooShort() {
    byte[] response = {0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x01};
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteMultipleRegistersResponse(response, 1));
  }

  @Test
  void testParseWriteMultipleRegistersResponseNull() {
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteMultipleRegistersResponse(null, 1));
  }

  @Test
  void testParseWriteMultipleRegistersResponseWrongFc() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01,
      0x06,                 // Wrong FC (should be 0x10)
      0x00, 0x10, 0x00, 0x02
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteMultipleRegistersResponse(response, 2));
  }

  @Test
  void testParseWriteMultipleRegistersResponseBodyTooShort() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x04, 0x01,
      0x10,                 // Correct FC
      0x00, 0x10            // Only address, missing quantity
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseWriteMultipleRegistersResponse(response, 1));
  }

  // ---- ModbusException is RuntimeException ----

  @Test
  void testModbusExceptionIsRuntimeException() {
    ModbusException ex = new ModbusException(0x03, 0x01);
    assertTrue(ex instanceof RuntimeException,
      "ModbusException should extend RuntimeException for transparent propagation");
  }
}
