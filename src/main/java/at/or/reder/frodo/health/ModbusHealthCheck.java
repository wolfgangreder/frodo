package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.connection.ConnectionStats;
import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

/**
 * Readiness health check for Modbus TCP connectivity.
 *
 * <p>Reports the health of the Modbus connection pool by evaluating:</p>
 * <ul>
 *   <li>Whether Modbus is enabled at all</li>
 *   <li>Connection pool state (CONNECTED vs FAILED/DISCONNECTED)</li>
 *   <li>Whether the last successful read is within a configurable age threshold</li>
 *   <li>Queue depth and request failure ratio</li>
 * </ul>
 *
 * <p><b>Health Criteria (from Stage 6 plan):</b></p>
 * <ul>
 *   <li>DOWN if Modbus is disabled (no enabled devices)</li>
 *   <li>DOWN if connection pool is unhealthy</li>
 *   <li>DOWN if no successful read in the last {@code frodo.modbus.health.max-age-minutes}</li>
 *   <li>UP if connection is healthy and recent reads succeeded</li>
 * </ul>
 */
@Readiness
@ApplicationScoped
public class ModbusHealthCheck implements HealthCheck {

  private static final Logger LOG = Logger.getLogger(ModbusHealthCheck.class);
  private static final String HEALTH_CHECK_NAME = "modbus-connection";

  @Inject
  ModbusConnectionPool connectionPool;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "frodo.modbus.health.max-age-minutes", defaultValue = "15")
  int maxAgeMinutes;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named(HEALTH_CHECK_NAME);

    if (!modbusEnabled) {
      LOG.debugf("Modbus health check: disabled");
      return builder.down()
        .withData("reason", "Modbus is disabled")
        .withData("modbus.enabled", false)
        .build();
    }

    ConnectionStats stats = connectionPool.getAggregatedStats();
    boolean poolHealthy = connectionPool.isHealthy();

    builder.withData("modbus.enabled", true)
      .withData("connection.state", stats.state().name())
      .withData("connection.healthy", poolHealthy)
      .withData("queue.size", stats.queueSize())
      .withData("requests.total", stats.totalRequests())
      .withData("requests.failed", stats.failedRequests());

    // Check connection pool health
    if (!poolHealthy) {
      LOG.debugf("Modbus health check: connection pool unhealthy (state=%s)", stats.state());
      return builder.down()
        .withData("reason", "Connection pool unhealthy: " + stats.state())
        .build();
    }

    // Check last successful read age
    Instant lastSuccess = stats.lastSuccessTime();
    if (lastSuccess != null) {
      Duration age = Duration.between(lastSuccess, Instant.now());
      long ageMinutes = age.toMinutes();
      builder.withData("last.success.age.minutes", ageMinutes);

      if (ageMinutes > maxAgeMinutes) {
        LOG.debugf("Modbus health check: last success too old (%d min > %d min threshold)",
          ageMinutes, maxAgeMinutes);
        return builder.down()
          .withData("reason", String.format("No successful read in %d minutes (threshold: %d)",
            ageMinutes, maxAgeMinutes))
          .build();
      }
    } else if (stats.totalRequests() > 0) {
      // Requests were made but none succeeded
      builder.withData("last.success.age.minutes", -1);
      LOG.debugf("Modbus health check: requests made but none succeeded");
      return builder.down()
        .withData("reason", "No successful request recorded")
        .build();
    }
    // If no requests have been made yet, that's OK — the system just started

    LOG.debugf("Modbus health check: UP (state=%s, queue=%d, total=%d, failed=%d)",
      stats.state(), stats.queueSize(), stats.totalRequests(), stats.failedRequests());

    return builder.up().build();
  }
}
