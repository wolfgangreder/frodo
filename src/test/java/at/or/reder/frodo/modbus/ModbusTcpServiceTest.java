package at.or.reder.frodo.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModbusTcpServiceTest {

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
    void testParseReadHoldingRegistersResponseTooShort() {
        byte[] shortResponse = {0x00, 0x01, 0x00, 0x00, 0x00, 0x05, 0x01};
        assertThrows(IllegalArgumentException.class,
                () -> ModbusTcpService.parseReadHoldingRegistersResponse(shortResponse, 1));
    }
}
