package at.or.reder.frodo.modbus.connection;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModbusRequestTest {

  @Test
  void testRecordCreation() {
    byte[] frame = {0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x00, 0x00, 0x0A};
    int transactionId = 42;
    Duration timeout = Duration.ofSeconds(10);

    ModbusRequest request = new ModbusRequest(frame, transactionId, timeout);

    assertArrayEquals(frame, request.requestFrame());
    assertEquals(transactionId, request.transactionId());
    assertEquals(timeout, request.timeout());
  }
}
