package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.connection.ConnectionStats;
import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.model.DeviceType;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Readiness health check for Modbus TCP connectivity and device status.
 *
 * <p>Reports the health of the Modbus connection pool and provides
 * per-device status information grouped by device type and
 * parent/sub-device hierarchy.</p>
 *
 * <p><b>Health Criteria:</b></p>
 * <ul>
 *   <li>DOWN if Modbus is disabled (no enabled devices)</li>
 *   <li>DOWN if connection pool is unhealthy</li>
 *   <li>DOWN if no successful read in the last {@code frodo.modbus.health.max-age-minutes}</li>
 *   <li>UP if connection is healthy and recent reads succeeded</li>
 * </ul>
 *
 * <p><b>Device Information:</b></p>
 * <ul>
 *   <li>Device count per type (inverter, smart_meter, ohmpilot, etc.)</li>
 *   <li>Per-device connection status grouped by parent/child hierarchy</li>
 *   <li>Auto-discovered vs manually configured device counts</li>
 * </ul>
 */
@Readiness
@ApplicationScoped
public class ModbusHealthCheck implements HealthCheck {

  private static final Logger LOG = Logger.getLogger(ModbusHealthCheck.class);
  private static final String HEALTH_CHECK_NAME = "modbus-connection";

  @Inject
  ModbusConnectionPool connectionPool;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "frodo.modbus.health.max-age-minutes", defaultValue = "15")
  int maxAgeMinutes;

  @Override
  @Transactional
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

    // Add device inventory information
    addDeviceInfo(builder);

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

  /**
   * Adds device inventory and hierarchy information to the health response.
   */
  private void addDeviceInfo(HealthCheckResponseBuilder builder) {
    try {
      List<ModbusDeviceEntity> allDevices = deviceRepository.listAllDevices();

      // Total and enabled counts
      long enabledCount = allDevices.stream().filter(d -> d.enabled).count();
      long autoDiscoveredCount = allDevices.stream().filter(d -> d.autoDiscovered).count();
      builder.withData("devices.total", allDevices.size())
        .withData("devices.enabled", enabledCount)
        .withData("devices.auto_discovered", autoDiscoveredCount);

      // Count by device type
      Map<DeviceType, Long> typeCounts = allDevices.stream()
        .filter(d -> d.deviceType != null)
        .collect(Collectors.groupingBy(d -> d.deviceType, Collectors.counting()));

      for (DeviceType type : DeviceType.values()) {
        long count = typeCounts.getOrDefault(type, 0L);
        builder.withData("devices.type." + type.name().toLowerCase(), count);
      }

      // Count devices with null type (legacy)
      long nullTypeCount = allDevices.stream()
        .filter(d -> d.deviceType == null)
        .count();
      if (nullTypeCount > 0) {
        builder.withData("devices.type.untyped", nullTypeCount);
      }

      // Per-device status (grouped by hierarchy)
      for (ModbusDeviceEntity device : allDevices) {
        // Only report top-level devices in detail; sub-devices appear under their parent
        if (device.parentDevice != null) {
          continue;
        }
        addDeviceStatus(builder, device, allDevices);
      }
    } catch (Exception ex) {
      LOG.debugf("Could not read device info for health check: %s", ex.getMessage());
      builder.withData("devices.error", ex.getMessage());
    }
  }

  /**
   * Adds status for a single device (and its children) to the health response.
   */
  private void addDeviceStatus(HealthCheckResponseBuilder builder,
                               ModbusDeviceEntity device,
                               List<ModbusDeviceEntity> allDevices) {
    String prefix = "device." + device.id;

    builder.withData(prefix + ".name", device.name)
      .withData(prefix + ".connection", device.getConnectionString())
      .withData(prefix + ".enabled", device.enabled);

    if (device.deviceType != null) {
      builder.withData(prefix + ".type", device.deviceType.name().toLowerCase());
    }

    // Connection status from cached device info
    if (device.deviceInfo != null) {
      if (device.deviceInfo.lastReadSuccess != null) {
        builder.withData(prefix + ".status",
          device.deviceInfo.lastReadSuccess ? "connected" : "failed");
      }
      if (device.deviceInfo.lastReadAt != null) {
        long ageMinutes = Duration.between(device.deviceInfo.lastReadAt, Instant.now()).toMinutes();
        builder.withData(prefix + ".last_read_age_minutes", ageMinutes);
      }
    } else {
      builder.withData(prefix + ".status", "unknown");
    }

    // Sub-devices
    List<ModbusDeviceEntity> children = allDevices.stream()
      .filter(d -> d.parentDevice != null && d.parentDevice.id.equals(device.id))
      .toList();

    if (!children.isEmpty()) {
      builder.withData(prefix + ".sub_devices", children.size());
      for (ModbusDeviceEntity child : children) {
        String childPrefix = prefix + ".sub." + child.id;
        builder.withData(childPrefix + ".name", child.name)
          .withData(childPrefix + ".unit_id", child.unitId);
        if (child.deviceType != null) {
          builder.withData(childPrefix + ".type", child.deviceType.name().toLowerCase());
        }
        if (child.deviceInfo != null && child.deviceInfo.lastReadSuccess != null) {
          builder.withData(childPrefix + ".status",
            child.deviceInfo.lastReadSuccess ? "connected" : "failed");
        } else {
          builder.withData(childPrefix + ".status", "unknown");
        }
      }
    }
  }
}
