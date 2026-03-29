package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.connection.ConnectionState;
import at.or.reder.frodo.modbus.connection.ConnectionStats;
import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModbusHealthCheck}.
 *
 * <p>Tests health check logic using Mockito mocks for CDI dependencies.
 * Fields are set directly to avoid CDI container startup.</p>
 */
@ExtendWith(MockitoExtension.class)
class ModbusHealthCheckTest {

  private ModbusHealthCheck healthCheck;

  @Mock
  ModbusConnectionPool connectionPool;

  @BeforeEach
  void setUp() {
    healthCheck = new ModbusHealthCheck();
    healthCheck.connectionPool = connectionPool;
    healthCheck.maxAgeMinutes = 15;
  }

  @Test
  void testDown_WhenModbusDisabled() {
    healthCheck.modbusEnabled = false;

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    assertEquals("modbus-connection", response.getName());
    assertTrue(response.getData().isPresent());
    assertEquals(false, response.getData().get().get("modbus.enabled"));
  }

  @Test
  void testUp_WhenConnectedAndHealthy() {
    healthCheck.modbusEnabled = true;
    when(connectionPool.getStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 2, Instant.now(), 100, 3));
    when(connectionPool.isHealthy()).thenReturn(true);

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    assertTrue(response.getData().isPresent());
    assertEquals(true, response.getData().get().get("modbus.enabled"));
    assertEquals("CONNECTED", response.getData().get().get("connection.state"));
  }

  @Test
  void testDown_WhenConnectionPoolUnhealthy() {
    healthCheck.modbusEnabled = true;
    when(connectionPool.getStats()).thenReturn(new ConnectionStats(
      ConnectionState.FAILED, 0, null, 10, 10));
    when(connectionPool.isHealthy()).thenReturn(false);

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    assertTrue(response.getData().isPresent());
    String reason = (String) response.getData().get().get("reason");
    assertTrue(reason.contains("FAILED"), "Reason should mention FAILED state: " + reason);
  }

  @Test
  void testDown_WhenLastSuccessTooOld() {
    healthCheck.modbusEnabled = true;
    Instant oldSuccess = Instant.now().minus(30, ChronoUnit.MINUTES);
    when(connectionPool.getStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 0, oldSuccess, 50, 5));
    when(connectionPool.isHealthy()).thenReturn(true);

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    assertTrue(response.getData().isPresent());
    String reason = (String) response.getData().get().get("reason");
    assertTrue(reason.contains("minutes"), "Reason should mention age threshold: " + reason);
  }

  @Test
  void testUp_WhenLastSuccessWithinThreshold() {
    healthCheck.modbusEnabled = true;
    Instant recentSuccess = Instant.now().minus(5, ChronoUnit.MINUTES);
    when(connectionPool.getStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 0, recentSuccess, 50, 2));
    when(connectionPool.isHealthy()).thenReturn(true);

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
  }

  @Test
  void testDown_WhenRequestsMadeButNoneSucceeded() {
    healthCheck.modbusEnabled = true;
    when(connectionPool.getStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 0, null, 10, 10));
    when(connectionPool.isHealthy()).thenReturn(true);

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    String reason = (String) response.getData().get().get("reason");
    assertTrue(reason.contains("No successful request"), "Reason: " + reason);
  }

  @Test
  void testUp_WhenNoRequestsYet() {
    healthCheck.modbusEnabled = true;
    when(connectionPool.getStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 0, null, 0, 0));
    when(connectionPool.isHealthy()).thenReturn(true);

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
  }
}
