package at.or.reder.frodo.modbus.connection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConnectionStateTest {

  @Test
  void testAllStates() {
    assertEquals(4, ConnectionState.values().length);
    assertNotNull(ConnectionState.valueOf("DISCONNECTED"));
    assertNotNull(ConnectionState.valueOf("CONNECTING"));
    assertNotNull(ConnectionState.valueOf("CONNECTED"));
    assertNotNull(ConnectionState.valueOf("FAILED"));
  }

  @Test
  void testStateOrdering() {
    ConnectionState[] states = ConnectionState.values();
    assertEquals(ConnectionState.DISCONNECTED, states[0]);
    assertEquals(ConnectionState.CONNECTING, states[1]);
    assertEquals(ConnectionState.CONNECTED, states[2]);
    assertEquals(ConnectionState.FAILED, states[3]);
  }
}
