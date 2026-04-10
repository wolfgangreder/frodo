package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.connection.ConnectionState;
import at.or.reder.frodo.modbus.connection.ConnectionStats;
import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.service.DeviceInfoCacheService;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModbusMetrics}.
 *
 * <p>Uses Micrometer's {@link SimpleMeterRegistry} for in-memory metric
 * verification and Mockito mocks for CDI dependencies.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModbusMetricsTest {

  private SimpleMeterRegistry registry;
  private ModbusMetrics metrics;

  @Mock
  ModbusConnectionPool connectionPool;

  @Mock
  DeviceInfoCacheService cacheService;

  @Mock
  SunSpecService sunSpecService;

  @Mock
  ModbusDeviceRepository deviceRepository;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();

    // Default stubs for gauge readings during registration
    when(connectionPool.getAggregatedStats()).thenReturn(new ConnectionStats(
      ConnectionState.DISCONNECTED, 0, null, 0, 0));
    when(cacheService.getStats()).thenReturn(
      new DeviceInfoCacheService.CacheStats(0, 0, 0));
    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(0);

    metrics = new ModbusMetrics(registry, connectionPool, cacheService, sunSpecService, deviceRepository);
  }

  // --- Gauge Tests ---

  @Test
  void testConnectionActiveGauge_WhenConnected() {
    when(connectionPool.getAggregatedStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 0, Instant.now(), 10, 0));

    Gauge gauge = registry.find("frodo.modbus.connection.active").gauge();
    assertNotNull(gauge, "frodo.modbus.connection.active gauge should be registered");
    assertEquals(1.0, gauge.value());
  }

  @Test
  void testConnectionActiveGauge_WhenDisconnected() {
    when(connectionPool.getAggregatedStats()).thenReturn(new ConnectionStats(
      ConnectionState.DISCONNECTED, 0, null, 0, 0));

    Gauge gauge = registry.find("frodo.modbus.connection.active").gauge();
    assertNotNull(gauge);
    assertEquals(0.0, gauge.value());
  }

  @Test
  void testConnectionActiveGauge_WhenFailed() {
    when(connectionPool.getAggregatedStats()).thenReturn(new ConnectionStats(
      ConnectionState.FAILED, 0, null, 5, 5));

    Gauge gauge = registry.find("frodo.modbus.connection.active").gauge();
    assertNotNull(gauge);
    assertEquals(0.0, gauge.value());
  }

  @Test
  void testQueueSizeGauge() {
    when(connectionPool.getAggregatedStats()).thenReturn(new ConnectionStats(
      ConnectionState.CONNECTED, 7, Instant.now(), 100, 2));

    Gauge gauge = registry.find("frodo.modbus.queue.size").gauge();
    assertNotNull(gauge);
    assertEquals(7.0, gauge.value());
  }

  @Test
  void testDeviceCacheTotalGauge() {
    when(cacheService.getStats()).thenReturn(
      new DeviceInfoCacheService.CacheStats(5, 3, 2));

    Gauge gauge = registry.find("frodo.modbus.devices.cache.total").gauge();
    assertNotNull(gauge);
    assertEquals(5.0, gauge.value());
  }

  @Test
  void testDeviceCacheActiveGauge() {
    when(cacheService.getStats()).thenReturn(
      new DeviceInfoCacheService.CacheStats(5, 3, 2));

    Gauge gauge = registry.find("frodo.modbus.devices.cache.active").gauge();
    assertNotNull(gauge);
    assertEquals(3.0, gauge.value());
  }

  @Test
  void testSunspecDiscoveryCachedGauge() {
    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(2);

    Gauge gauge = registry.find("frodo.sunspec.discovery.cached").gauge();
    assertNotNull(gauge);
    assertEquals(2.0, gauge.value());
  }

  // --- Counter Tests ---

  @Test
  void testRequestSuccessCounter() {
    metrics.recordRequestSuccess();
    metrics.recordRequestSuccess();

    Counter counter = registry.find("frodo.modbus.requests.total")
      .tag("status", "success").counter();
    assertNotNull(counter);
    assertEquals(2.0, counter.count());
  }

  @Test
  void testRequestFailureCounter() {
    metrics.recordRequestFailure();

    Counter counter = registry.find("frodo.modbus.requests.total")
      .tag("status", "failure").counter();
    assertNotNull(counter);
    assertEquals(1.0, counter.count());
  }

  @Test
  void testSunspecDiscoveryCounters() {
    metrics.recordSunSpecDiscoverySuccess();
    metrics.recordSunSpecDiscoverySuccess();
    metrics.recordSunSpecDiscoveryFailure();

    Counter success = registry.find("frodo.sunspec.discovery.total")
      .tag("status", "success").counter();
    Counter failure = registry.find("frodo.sunspec.discovery.total")
      .tag("status", "failure").counter();

    assertNotNull(success);
    assertNotNull(failure);
    assertEquals(2.0, success.count());
    assertEquals(1.0, failure.count());
  }

  @Test
  void testSunspecModelReadCounter() {
    metrics.recordSunSpecModelRead(113, true);
    metrics.recordSunSpecModelRead(113, true);
    metrics.recordSunSpecModelRead(113, false);

    Counter success = registry.find("frodo.sunspec.model.reads.total")
      .tag("model_id", "113").tag("status", "success").counter();
    Counter failure = registry.find("frodo.sunspec.model.reads.total")
      .tag("model_id", "113").tag("status", "failure").counter();

    assertNotNull(success);
    assertNotNull(failure);
    assertEquals(2.0, success.count());
    assertEquals(1.0, failure.count());
  }

  @Test
  void testSunspecCacheInvalidationCounter() {
    metrics.recordSunSpecCacheInvalidation();
    metrics.recordSunSpecCacheInvalidation();
    metrics.recordSunSpecCacheInvalidation();

    Counter counter = registry.find("frodo.sunspec.cache.invalidations.total").counter();
    assertNotNull(counter);
    assertEquals(3.0, counter.count());
  }

  // --- Timer Tests ---

  @Test
  void testRequestDurationTimer() {
    metrics.recordRequestDuration(TimeUnit.MILLISECONDS.toNanos(150));

    Timer timer = registry.find("frodo.modbus.request.duration").timer();
    assertNotNull(timer);
    assertEquals(1, timer.count());
    assertEquals(150.0, timer.totalTime(TimeUnit.MILLISECONDS), 1.0);
  }

  @Test
  void testDeviceInfoReadDurationTimer() {
    metrics.recordDeviceInfoReadDuration(TimeUnit.MILLISECONDS.toNanos(250));

    Timer timer = registry.find("frodo.modbus.device_info.read.duration").timer();
    assertNotNull(timer);
    assertEquals(1, timer.count());
  }

  @Test
  void testSunspecDiscoveryDurationTimer() {
    metrics.recordSunSpecDiscoveryDuration(TimeUnit.MILLISECONDS.toNanos(500));

    Timer timer = registry.find("frodo.sunspec.discovery.duration").timer();
    assertNotNull(timer);
    assertEquals(1, timer.count());
  }

  @Test
  void testSunspecModelReadDurationTimer() {
    metrics.recordSunSpecModelReadDuration(113, TimeUnit.MILLISECONDS.toNanos(75));
    metrics.recordSunSpecModelReadDuration(113, TimeUnit.MILLISECONDS.toNanos(100));

    Timer timer = registry.find("frodo.sunspec.model.read.duration")
      .tag("model_id", "113").timer();
    assertNotNull(timer);
    assertEquals(2, timer.count());
  }

  @Test
  void testGetRegistry() {
    assertSame(registry, metrics.getRegistry());
  }
}
