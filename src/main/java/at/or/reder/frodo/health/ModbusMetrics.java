package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.connection.ConnectionState;
import at.or.reder.frodo.modbus.connection.ConnectionStats;
import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import at.or.reder.frodo.modbus.service.DeviceInfoCacheService;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Registers and manages Micrometer/Prometheus metrics for Modbus and SunSpec operations.
 *
 * <p>Provides gauges, counters, and timers that expose connection pool state,
 * request statistics, device info cache status, and SunSpec discovery/read
 * performance data to the {@code /q/metrics} Prometheus endpoint.</p>
 *
 * <p><b>Registered Metrics:</b></p>
 *
 * <p><em>Gauges:</em></p>
 * <ul>
 *   <li>{@code frodo.modbus.connection.active} — 1 if connected, 0 otherwise</li>
 *   <li>{@code frodo.modbus.queue.size} — current request queue depth</li>
 *   <li>{@code frodo.modbus.devices.cache.total} — total device info cache entries</li>
 *   <li>{@code frodo.modbus.devices.cache.active} — active (non-expired) cache entries</li>
 *   <li>{@code frodo.sunspec.discovery.cached} — number of cached SunSpec discoveries</li>
 * </ul>
 *
 * <p><em>Counters:</em></p>
 * <ul>
 *   <li>{@code frodo.modbus.requests.total} — total Modbus requests (tag: status=success|failure)</li>
 *   <li>{@code frodo.sunspec.discovery.total} — SunSpec discovery attempts (tag: status)</li>
 *   <li>{@code frodo.sunspec.model.reads.total} — SunSpec model read attempts (tag: status, model_id)</li>
 *   <li>{@code frodo.sunspec.cache.invalidations.total} — SunSpec cache invalidations</li>
 * </ul>
 *
 * <p><em>Timers:</em></p>
 * <ul>
 *   <li>{@code frodo.modbus.request.duration} — Modbus request latency</li>
 *   <li>{@code frodo.modbus.device_info.read.duration} — device info read latency</li>
 *   <li>{@code frodo.sunspec.discovery.duration} — SunSpec discovery latency</li>
 *   <li>{@code frodo.sunspec.model.read.duration} — SunSpec model read latency</li>
 * </ul>
 */
@ApplicationScoped
@Startup
public class ModbusMetrics {

  private static final Logger LOG = Logger.getLogger(ModbusMetrics.class);

  private final MeterRegistry registry;
  private final ModbusConnectionPool connectionPool;
  private final DeviceInfoCacheService deviceInfoCacheService;
  private final SunSpecService sunSpecService;

  // Counters
  private final Counter requestsSuccessCounter;
  private final Counter requestsFailureCounter;
  private final Counter sunspecDiscoverySuccessCounter;
  private final Counter sunspecDiscoveryFailureCounter;
  private final Counter sunspecCacheInvalidationsCounter;

  // Per-model counters cache (created lazily)
  private final Map<String, Counter> modelReadCounters = new ConcurrentHashMap<>();

  // Timers
  private final Timer requestDurationTimer;
  private final Timer deviceInfoReadDurationTimer;
  private final Timer sunspecDiscoveryDurationTimer;

  // Per-model timers cache (created lazily)
  private final Map<String, Timer> modelReadTimers = new ConcurrentHashMap<>();

  @Inject
  public ModbusMetrics(MeterRegistry registry,
                       ModbusConnectionPool connectionPool,
                       DeviceInfoCacheService deviceInfoCacheService,
                       SunSpecService sunSpecService) {
    this.registry = registry;
    this.connectionPool = connectionPool;
    this.deviceInfoCacheService = deviceInfoCacheService;
    this.sunSpecService = sunSpecService;

    // --- Register Gauges ---
    Gauge.builder("frodo.modbus.connection.active", this, ModbusMetrics::connectionActive)
      .description("Whether the Modbus TCP connection is active (1=connected, 0=not)")
      .register(registry);

    Gauge.builder("frodo.modbus.queue.size", this, ModbusMetrics::queueSize)
      .description("Number of requests waiting in the Modbus request queue")
      .register(registry);

    Gauge.builder("frodo.modbus.devices.cache.total", this, ModbusMetrics::deviceCacheTotal)
      .description("Total number of device info cache entries")
      .register(registry);

    Gauge.builder("frodo.modbus.devices.cache.active", this, ModbusMetrics::deviceCacheActive)
      .description("Number of active (non-expired) device info cache entries")
      .register(registry);

    Gauge.builder("frodo.sunspec.discovery.cached", this, ModbusMetrics::sunspecDiscoveryCached)
      .description("Number of cached SunSpec discovery results")
      .register(registry);

    // --- Register Counters ---
    requestsSuccessCounter = Counter.builder("frodo.modbus.requests.total")
      .description("Total Modbus requests")
      .tag("status", "success")
      .register(registry);

    requestsFailureCounter = Counter.builder("frodo.modbus.requests.total")
      .description("Total Modbus requests")
      .tag("status", "failure")
      .register(registry);

    sunspecDiscoverySuccessCounter = Counter.builder("frodo.sunspec.discovery.total")
      .description("SunSpec discovery attempts")
      .tag("status", "success")
      .register(registry);

    sunspecDiscoveryFailureCounter = Counter.builder("frodo.sunspec.discovery.total")
      .description("SunSpec discovery attempts")
      .tag("status", "failure")
      .register(registry);

    sunspecCacheInvalidationsCounter = Counter.builder("frodo.sunspec.cache.invalidations.total")
      .description("SunSpec discovery cache invalidations")
      .register(registry);

    // --- Register Timers ---
    requestDurationTimer = Timer.builder("frodo.modbus.request.duration")
      .description("Modbus request latency")
      .register(registry);

    deviceInfoReadDurationTimer = Timer.builder("frodo.modbus.device_info.read.duration")
      .description("Device info read latency")
      .register(registry);

    sunspecDiscoveryDurationTimer = Timer.builder("frodo.sunspec.discovery.duration")
      .description("SunSpec model chain discovery latency")
      .register(registry);

    LOG.info("Modbus and SunSpec metrics registered");
  }

  // --- Gauge value suppliers ---

  private double connectionActive() {
    ConnectionStats stats = connectionPool.getAggregatedStats();
    return stats.state() == ConnectionState.CONNECTED ? 1.0 : 0.0;
  }

  private double queueSize() {
    return connectionPool.getAggregatedStats().queueSize();
  }

  private double deviceCacheTotal() {
    return deviceInfoCacheService.getStats().totalEntries();
  }

  private double deviceCacheActive() {
    return deviceInfoCacheService.getStats().activeEntries();
  }

  private double sunspecDiscoveryCached() {
    return sunSpecService.getDiscoveryCacheSize();
  }

  // --- Public API for recording metrics from other services ---

  /**
   * Records a successful Modbus request.
   */
  public void recordRequestSuccess() {
    requestsSuccessCounter.increment();
  }

  /**
   * Records a failed Modbus request.
   */
  public void recordRequestFailure() {
    requestsFailureCounter.increment();
  }

  /**
   * Records the duration of a Modbus request.
   *
   * @param durationNanos request duration in nanoseconds
   */
  public void recordRequestDuration(long durationNanos) {
    requestDurationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the duration of a device info read operation.
   *
   * @param durationNanos read duration in nanoseconds
   */
  public void recordDeviceInfoReadDuration(long durationNanos) {
    deviceInfoReadDurationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records a successful SunSpec discovery.
   */
  public void recordSunSpecDiscoverySuccess() {
    sunspecDiscoverySuccessCounter.increment();
  }

  /**
   * Records a failed SunSpec discovery.
   */
  public void recordSunSpecDiscoveryFailure() {
    sunspecDiscoveryFailureCounter.increment();
  }

  /**
   * Records the duration of a SunSpec discovery operation.
   *
   * @param durationNanos discovery duration in nanoseconds
   */
  public void recordSunSpecDiscoveryDuration(long durationNanos) {
    sunspecDiscoveryDurationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records a SunSpec model read attempt.
   *
   * @param modelId the SunSpec model ID that was read
   * @param success whether the read was successful
   */
  public void recordSunSpecModelRead(int modelId, boolean success) {
    String key = modelId + "." + (success ? "success" : "failure");
    modelReadCounters.computeIfAbsent(key, k ->
      Counter.builder("frodo.sunspec.model.reads.total")
        .description("SunSpec model read attempts")
        .tag("model_id", String.valueOf(modelId))
        .tag("status", success ? "success" : "failure")
        .register(registry)
    ).increment();
  }

  /**
   * Records the duration of a SunSpec model read.
   *
   * @param modelId       the SunSpec model ID
   * @param durationNanos read duration in nanoseconds
   */
  public void recordSunSpecModelReadDuration(int modelId, long durationNanos) {
    String key = String.valueOf(modelId);
    modelReadTimers.computeIfAbsent(key, k ->
      Timer.builder("frodo.sunspec.model.read.duration")
        .description("SunSpec model read latency")
        .tag("model_id", String.valueOf(modelId))
        .register(registry)
    ).record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records a SunSpec cache invalidation event.
   */
  public void recordSunSpecCacheInvalidation() {
    sunspecCacheInvalidationsCounter.increment();
  }

  /**
   * Returns the meter registry for testing purposes.
   *
   * @return the Micrometer meter registry
   */
  public MeterRegistry getRegistry() {
    return registry;
  }
}
