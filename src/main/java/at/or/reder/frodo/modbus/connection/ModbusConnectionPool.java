package at.or.reder.frodo.modbus.connection;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Manages Modbus TCP connections keyed by host:port.
 *
 * <p>Multiple unit IDs on the same host:port share a single
 * {@link ModbusConnection}, since the unit ID is encoded in the
 * MBAP frame header. Connections are created lazily on the first
 * request to a given host:port and reused for subsequent requests.</p>
 *
 * <p>Each {@link ModbusConnection} internally serializes requests
 * via a fair lock with inter-request delay.</p>
 */
@ApplicationScoped
public class ModbusConnectionPool {

  private static final Logger LOG = Logger.getLogger(ModbusConnectionPool.class);

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "frodo.modbus.connection.timeout-seconds", defaultValue = "30")
  int connectionTimeoutSeconds;

  @ConfigProperty(name = "frodo.modbus.connection.reconnect-initial-delay-seconds", defaultValue = "1")
  int reconnectInitialDelaySeconds;

  @ConfigProperty(name = "frodo.modbus.connection.reconnect-max-delay-seconds", defaultValue = "60")
  int reconnectMaxDelaySeconds;

  @ConfigProperty(name = "frodo.modbus.request.timeout-seconds", defaultValue = "10")
  int requestTimeoutSeconds;

  /** Active connections keyed by "host:port". */
  private final Map<String, ModbusConnection> connections = new ConcurrentHashMap<>();

  void onStop(@Observes ShutdownEvent event) {
    LOG.info("Shutting down Modbus connection pool");
    for (Map.Entry<String, ModbusConnection> entry : connections.entrySet()) {
      entry.getValue().disconnect();
      LOG.debugf("Disconnected connection to %s", entry.getKey());
    }
    connections.clear();
    LOG.info("Modbus connection pool stopped");
  }

  /**
   * Executes a Modbus request routed to the correct device.
   *
   * <p>Looks up (or creates) a connection for the device's host:port,
   * then delegates request execution to that connection. The connection
   * handles serialization, reconnection, and timeout internally.</p>
   *
   * @param address        target device address (host, port, unitId)
   * @param requestFrame   raw Modbus TCP frame (MBAP + PDU)
   * @param transactionId  transaction ID for response correlation
   * @return response bytes
   * @throws IOException          if an I/O error occurs
   * @throws TimeoutException     if the request times out
   * @throws IllegalStateException if Modbus is disabled
   */
  public byte[] executeRequest(DeviceAddress address, byte[] requestFrame, int transactionId)
    throws IOException, TimeoutException {

    if (!modbusEnabled) {
      throw new IllegalStateException("Modbus is disabled");
    }

    ModbusConnection connection = getOrCreateConnection(address);
    ModbusRequest request = new ModbusRequest(
      requestFrame,
      transactionId,
      Duration.ofSeconds(requestTimeoutSeconds)
    );

    return connection.executeRequest(request);
  }

  /**
   * Gets an existing connection for the host:port, or creates a new one.
   *
   * @param address device address (host and port are used as the key)
   * @return the connection for this host:port
   */
  private ModbusConnection getOrCreateConnection(DeviceAddress address) {
    String key = address.connectionKey();
    return connections.computeIfAbsent(key, k -> {
      LOG.infof("Creating new Modbus connection for %s", key);
      return new ModbusConnection(
        address.host(),
        address.port(),
        Duration.ofSeconds(connectionTimeoutSeconds),
        reconnectInitialDelaySeconds,
        reconnectMaxDelaySeconds
      );
    });
  }

  /**
   * Returns current connection statistics for a specific host:port.
   *
   * @param connectionKey the connection key ("host:port")
   * @return ConnectionStats, or stats with DISCONNECTED state if no connection exists
   */
  public ConnectionStats getStats(String connectionKey) {
    ModbusConnection connection = connections.get(connectionKey);
    if (connection == null) {
      return new ConnectionStats(ConnectionState.DISCONNECTED, 0, null, 0, 0);
    }
    return connection.getStats();
  }

  /**
   * Returns aggregated connection statistics across all connections.
   *
   * <p>Used by health checks and metrics. The state is CONNECTED if any
   * connection is connected, FAILED if any failed, DISCONNECTED otherwise.</p>
   *
   * @return aggregated ConnectionStats
   */
  public ConnectionStats getAggregatedStats() {
    if (connections.isEmpty()) {
      return new ConnectionStats(ConnectionState.DISCONNECTED, 0, null, 0, 0);
    }

    ConnectionState worstState = ConnectionState.DISCONNECTED;
    int totalQueueSize = 0;
    long totalRequests = 0;
    long totalFailed = 0;
    java.time.Instant latestSuccess = null;

    for (ModbusConnection conn : connections.values()) {
      ConnectionStats stats = conn.getStats();
      totalQueueSize += stats.queueSize();
      totalRequests += stats.totalRequests();
      totalFailed += stats.failedRequests();

      if (stats.lastSuccessTime() != null) {
        if (latestSuccess == null || stats.lastSuccessTime().isAfter(latestSuccess)) {
          latestSuccess = stats.lastSuccessTime();
        }
      }

      // Determine worst state: FAILED > CONNECTING > CONNECTED > DISCONNECTED
      if (stats.state() == ConnectionState.CONNECTED && worstState == ConnectionState.DISCONNECTED) {
        worstState = ConnectionState.CONNECTED;
      } else if (stats.state() == ConnectionState.FAILED) {
        worstState = ConnectionState.FAILED;
      }
    }

    return new ConnectionStats(worstState, totalQueueSize, latestSuccess, totalRequests, totalFailed);
  }

  /**
   * Checks if Modbus is enabled and at least one connection is healthy.
   *
   * @return true if Modbus is enabled and at least one connection is healthy
   */
  public boolean isHealthy() {
    if (!modbusEnabled) {
      return false;
    }
    if (connections.isEmpty()) {
      // No connections yet is OK (lazy creation)
      return true;
    }
    return connections.values().stream().anyMatch(ModbusConnection::isHealthy);
  }

  /**
   * Returns the number of active connections.
   *
   * @return number of host:port connections currently managed
   */
  public int getConnectionCount() {
    return connections.size();
  }

  /**
   * Returns all active connection keys.
   *
   * @return collection of "host:port" keys
   */
  public Collection<String> getConnectionKeys() {
    return connections.keySet();
  }

  /**
   * Removes and disconnects the connection for a specific host:port.
   *
   * <p>Useful when a device is deleted or its connection settings change.</p>
   *
   * @param connectionKey the connection key ("host:port")
   */
  public void removeConnection(String connectionKey) {
    ModbusConnection connection = connections.remove(connectionKey);
    if (connection != null) {
      connection.disconnect();
      LOG.infof("Removed connection for %s", connectionKey);
    }
  }
}
