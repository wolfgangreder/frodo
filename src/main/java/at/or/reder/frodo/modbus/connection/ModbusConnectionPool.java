package at.or.reder.frodo.modbus.connection;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Manages the lifecycle of Modbus connection and request queue.
 * Integrates connection establishment, request serialization, and statistics.
 */
@ApplicationScoped
@Startup
public class ModbusConnectionPool {

  private static final Logger LOG = Logger.getLogger(ModbusConnectionPool.class);

  @Inject
  ModbusConnection connection;

  @Inject
  ModbusRequestQueue requestQueue;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "frodo.modbus.device.host", defaultValue = "localhost")
  String deviceHost;

  @ConfigProperty(name = "frodo.modbus.device.port", defaultValue = "502")
  int devicePort;

  @ConfigProperty(name = "frodo.modbus.connection.timeout-seconds", defaultValue = "30")
  int connectionTimeoutSeconds;

  @ConfigProperty(name = "frodo.modbus.connection.reconnect-initial-delay-seconds", defaultValue = "1")
  int reconnectInitialDelaySeconds;

  @ConfigProperty(name = "frodo.modbus.connection.reconnect-max-delay-seconds", defaultValue = "60")
  int reconnectMaxDelaySeconds;

  @ConfigProperty(name = "frodo.modbus.request.queue-capacity", defaultValue = "50")
  int queueCapacity;

  @ConfigProperty(name = "frodo.modbus.request.timeout-seconds", defaultValue = "10")
  int requestTimeoutSeconds;

  void onStart(@Observes StartupEvent event) {
    if (!modbusEnabled) {
      LOG.info("Modbus connection pool disabled");
      return;
    }

    LOG.infof("Initializing Modbus connection pool (host=%s, port=%d)", deviceHost, devicePort);

    // Initialize connection
    connection.initialize(
      deviceHost,
      devicePort,
      Duration.ofSeconds(connectionTimeoutSeconds),
      reconnectInitialDelaySeconds,
      reconnectMaxDelaySeconds
    );

    // Initialize and start queue
    requestQueue.initialize(queueCapacity);
    requestQueue.start();

    // Establish initial connection
    connection.connect()
      .subscribe()
      .with(
        v -> LOG.info("Modbus connection pool started successfully"),
        ex -> LOG.errorf(ex, "Failed to establish initial connection, will retry automatically")
      );
  }

  void onStop(@Observes ShutdownEvent event) {
    if (!modbusEnabled) {
      return;
    }

    LOG.info("Shutting down Modbus connection pool");

    requestQueue.stop();
    connection.disconnect()
      .subscribe()
      .with(
        v -> LOG.info("Modbus connection pool stopped"),
        ex -> LOG.warnf(ex, "Error during connection pool shutdown")
      );
  }

  /**
   * Executes a Modbus request through the connection pool.
   *
   * @param requestFrame    raw Modbus TCP frame (MBAP + PDU)
   * @param transactionId   transaction ID for response correlation
   * @return Uni resolving to response bytes
   */
  public Uni<byte[]> executeRequest(byte[] requestFrame, int transactionId) {
    if (!modbusEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Modbus is disabled")
      );
    }

    ModbusRequest request = new ModbusRequest(
      requestFrame,
      transactionId,
      Duration.ofSeconds(requestTimeoutSeconds)
    );

    return requestQueue.enqueue(request);
  }

  /**
   * Returns current connection statistics.
   *
   * @return ConnectionStats with current state, queue size, and counters
   */
  public ConnectionStats getStats() {
    return new ConnectionStats(
      connection.getState(),
      requestQueue.getQueueSize(),
      connection.getLastSuccessTime(),
      connection.getTotalRequests(),
      connection.getFailedRequests()
    );
  }

  /**
   * Checks if the connection pool is healthy.
   *
   * @return true if Modbus is enabled and connection is healthy
   */
  public boolean isHealthy() {
    return modbusEnabled && connection.isHealthy();
  }
}
