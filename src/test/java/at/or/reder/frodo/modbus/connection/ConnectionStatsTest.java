package at.or.reder.frodo.modbus.connection;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionStatsTest {

  @Test
  void testRecordCreation() {
    Instant now = Instant.now();
    ConnectionStats stats = new ConnectionStats(
      ConnectionState.CONNECTED,
      5,
      now,
      100,
      2
    );

    assertEquals(ConnectionState.CONNECTED, stats.state());
    assertEquals(5, stats.queueSize());
    assertEquals(now, stats.lastSuccessTime());
    assertEquals(100, stats.totalRequests());
    assertEquals(2, stats.failedRequests());
  }

  @Test
  void testRecordWithNullLastSuccess() {
    ConnectionStats stats = new ConnectionStats(
      ConnectionState.CONNECTING,
      0,
      null,
      0,
      0
    );

    assertNull(stats.lastSuccessTime());
  }
}
