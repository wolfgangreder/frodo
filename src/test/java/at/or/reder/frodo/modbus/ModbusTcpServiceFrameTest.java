package at.or.reder.frodo.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModbusTcpServiceFrameTest {

  @Test
  void testBuildReadHoldingRegistersRequest() {
    byte[] frame = ModbusTcpService.buildReadHoldingRegistersRequest(1, 0, 10, 1);
    // MBAP header + unitId + PDU: 7 bytes header + 5 bytes PDU = 12 bytes total
    assertEquals(12, frame.length);
    assertEquals(0x00, frame[0]); // Transaction ID high
    assertEquals(0x01, frame[1]); // Transaction ID low
    assertEquals(0x00, frame[2]); // Protocol ID high
    assertEquals(0x00, frame[3]); // Protocol ID low
    assertEquals(0x00, frame[4]); // Length high
    assertEquals(0x06, frame[5]); // Length low (1 unitId + 5 bytes PDU)
    assertEquals(0x01, frame[6]); // Unit ID
    // PDU
    assertEquals(0x03, frame[7]);  // Function code: Read Holding Registers
    assertEquals(0x00, frame[8]);  // Start address high
    assertEquals(0x00, frame[9]);  // Start address low
    assertEquals(0x00, frame[10]); // Quantity high
    assertEquals(0x0A, frame[11]); // Quantity low (10 registers)
  }

  @Test
  void testBuildRequestWithLargeTransactionId() {
    byte[] frame = ModbusTcpService.buildReadHoldingRegistersRequest(1, 0, 10, 0xABCD);
    assertEquals(0xAB, frame[0] & 0xFF); // Transaction ID high
    assertEquals(0xCD, frame[1] & 0xFF); // Transaction ID low
  }

  @Test
  void testBuildRequestWithLargeAddress() {
    byte[] frame = ModbusTcpService.buildReadHoldingRegistersRequest(1, 0x1234, 5, 1);
    assertEquals(0x12, frame[8] & 0xFF);  // Start address high
    assertEquals(0x34, frame[9] & 0xFF);  // Start address low
  }

  @Test
  void testBuildMbapFrame() {
    byte[] pdu = {0x03, 0x00, 0x00, 0x00, 0x0A};
    byte[] frame = ModbusTcpService.buildMbapFrame(42, 1, pdu);

    assertEquals(12, frame.length); // 7 (MBAP + unitId) + 5 (PDU) bytes
    assertEquals(0x00, frame[0]); // Transaction ID high
    assertEquals(0x2A, frame[1]); // Transaction ID low (42)
    assertEquals(0x00, frame[2]); // Protocol ID high
    assertEquals(0x00, frame[3]); // Protocol ID low
    assertEquals(0x00, frame[4]); // Length high
    assertEquals(0x06, frame[5]); // Length low
    assertEquals(0x01, frame[6]); // Unit ID
    // Verify PDU bytes are copied correctly
    assertArrayEquals(pdu, new byte[]{frame[7], frame[8], frame[9], frame[10], frame[11]});
  }

  @Test
  void testBuildMbapFrameWithLargePdu() {
    byte[] pdu = new byte[100];
    byte[] frame = ModbusTcpService.buildMbapFrame(1, 1, pdu);
    
    assertEquals(107, frame.length); // 7 (MBAP+unitId) + 100 (PDU)
    assertEquals(0x00, frame[4]); // Length high
    assertEquals(0x65, frame[5]); // Length low (101 = 1 unitId + 100 PDU)
  }

  @Test
  void testParseReadHoldingRegistersResponse() {
    // Simulate Modbus TCP response: MBAP (6) + unitId (1) + FC (1) + byteCount (1) + 4 bytes (2 regs)
    byte[] response = {
      0x00, 0x01,        // Transaction ID
      0x00, 0x00,        // Protocol ID
      0x00, 0x05,        // Length
      0x01,              // Unit ID
      0x03,              // Function code
      0x04,              // Byte count (4 bytes = 2 registers)
      0x00, 0x0A,        // Register 0 = 10
      0x01, 0x2C         // Register 1 = 300
    };

    int[] registers = ModbusTcpService.parseReadHoldingRegistersResponse(response, 2);
    assertEquals(2, registers.length);
    assertEquals(10, registers[0]);
    assertEquals(300, registers[1]);
  }

  @Test
  void testParseResponseWithMultipleRegisters() {
    byte[] response = {
      0x00, 0x01,        // Transaction ID
      0x00, 0x00,        // Protocol ID
      0x00, 0x09,        // Length (9 bytes)
      0x01,              // Unit ID
      0x03,              // Function code
      0x08,              // Byte count (8 bytes = 4 registers)
      0x00, 0x01,        // Register 0 = 1
      0x00, 0x02,        // Register 1 = 2
      0x00, 0x03,        // Register 2 = 3
      0x00, 0x04         // Register 3 = 4
    };

    int[] registers = ModbusTcpService.parseReadHoldingRegistersResponse(response, 4);
    assertEquals(4, registers.length);
    assertEquals(1, registers[0]);
    assertEquals(2, registers[1]);
    assertEquals(3, registers[2]);
    assertEquals(4, registers[3]);
  }

  @Test
  void testParseResponseWithLargeValues() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x05, 0x01, 0x03, 0x04,
      (byte) 0xFF, (byte) 0xFF,  // Register 0 = 65535
      (byte) 0x80, 0x00          // Register 1 = 32768
    };

    int[] registers = ModbusTcpService.parseReadHoldingRegistersResponse(response, 2);
    assertEquals(2, registers.length);
    assertEquals(65535, registers[0]);
    assertEquals(32768, registers[1]);
  }

  @Test
  void testParseReadHoldingRegistersResponseTooShort() {
    byte[] shortResponse = {0x00, 0x01, 0x00, 0x00, 0x00, 0x05, 0x01};
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadHoldingRegistersResponse(shortResponse, 1));
  }

  @Test
  void testParseIncompleteResponse() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x05, 0x01, 0x03, 0x04,
      0x00, 0x0A  // Only 1 register but byte count says 4 bytes
    };
    
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadHoldingRegistersResponse(response, 2));
  }

  @Test
  void testParseEmptyResponse() {
    byte[] response = {
      0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01, 0x03, 0x00  // Byte count = 0
    };

    int[] registers = ModbusTcpService.parseReadHoldingRegistersResponse(response, 0);
    assertEquals(0, registers.length);
  }

  @Test
  void testTransactionIdWrapping() {
    // Transaction ID should wrap at 0xFFFF
    byte[] frame1 = ModbusTcpService.buildReadHoldingRegistersRequest(1, 0, 1, 0xFFFF);
    assertEquals((byte) 0xFF, frame1[0]);
    assertEquals((byte) 0xFF, frame1[1]);

    byte[] frame2 = ModbusTcpService.buildReadHoldingRegistersRequest(1, 0, 1, 0x10000);
    assertEquals(0x00, frame2[0]); // Should wrap to 0
    assertEquals(0x00, frame2[1]);
  }
}
