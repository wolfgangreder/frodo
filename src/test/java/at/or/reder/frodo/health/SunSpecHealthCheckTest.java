package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SunSpecHealthCheck}.
 *
 * <p>Tests health check logic using Mockito mocks for CDI dependencies.</p>
 */
@ExtendWith(MockitoExtension.class)
class SunSpecHealthCheckTest {

  private SunSpecHealthCheck healthCheck;

  @Mock
  SunSpecService sunSpecService;

  @BeforeEach
  void setUp() {
    healthCheck = new SunSpecHealthCheck();
    healthCheck.sunSpecService = sunSpecService;
    healthCheck.maxCacheAgeHours = 24;
  }

  @Test
  void testUp_WhenModbusDisabled() {
    healthCheck.modbusEnabled = false;
    healthCheck.discoveryRequired = false;

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    assertEquals("sunspec-discovery", response.getName());
    assertEquals(false, response.getData().get().get("modbus.enabled"));
  }

  @Test
  void testUp_WhenNoDiscoveriesAndNotRequired() {
    healthCheck.modbusEnabled = true;
    healthCheck.discoveryRequired = false;
    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(0);
    when(sunSpecService.getCachedDeviceKeys()).thenReturn(Set.of());

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
  }

  @Test
  void testDown_WhenNoDiscoveriesButRequired() {
    healthCheck.modbusEnabled = true;
    healthCheck.discoveryRequired = true;
    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(0);
    when(sunSpecService.getCachedDeviceKeys()).thenReturn(Set.of());

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    String reason = (String) response.getData().get().get("reason");
    assertTrue(reason.contains("No SunSpec device"), "Reason: " + reason);
  }

  @Test
  void testUp_WhenValidDiscoveryPresent() {
    healthCheck.modbusEnabled = true;
    healthCheck.discoveryRequired = true;

    String deviceKey = "localhost:502/1";
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 66),
      new SunSpecModelBlock(113, 40070, 60)
    );
    SunSpecDiscoveryResult recent = new SunSpecDiscoveryResult(
      40000, models, Instant.now().minus(1, ChronoUnit.HOURS));

    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(1);
    when(sunSpecService.getCachedDeviceKeys()).thenReturn(Set.of(deviceKey));
    when(sunSpecService.getCachedDiscovery(deviceKey)).thenReturn(Optional.of(recent));

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    assertEquals(1L, response.getData().get().get("discovery.valid.count"));
    assertEquals(0L, response.getData().get().get("discovery.expired.count"));
  }

  @Test
  void testDown_WhenAllDiscoveriesExpired() {
    healthCheck.modbusEnabled = true;
    healthCheck.discoveryRequired = true;

    String deviceKey = "localhost:502/1";
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 66)
    );
    SunSpecDiscoveryResult expired = new SunSpecDiscoveryResult(
      40000, models, Instant.now().minus(48, ChronoUnit.HOURS));

    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(1);
    when(sunSpecService.getCachedDeviceKeys()).thenReturn(Set.of(deviceKey));
    when(sunSpecService.getCachedDiscovery(deviceKey)).thenReturn(Optional.of(expired));

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    assertEquals(0L, response.getData().get().get("discovery.valid.count"));
    assertEquals(1L, response.getData().get().get("discovery.expired.count"));
  }

  @Test
  void testUp_WhenMixedValidAndExpiredDiscoveries() {
    healthCheck.modbusEnabled = true;
    healthCheck.discoveryRequired = true;

    String deviceKey1 = "localhost:502/1";
    String deviceKey2 = "localhost:502/2";
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 66)
    );

    SunSpecDiscoveryResult expired = new SunSpecDiscoveryResult(
      40000, models, Instant.now().minus(48, ChronoUnit.HOURS));
    SunSpecDiscoveryResult valid = new SunSpecDiscoveryResult(
      40000, models, Instant.now().minus(2, ChronoUnit.HOURS));

    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(2);
    when(sunSpecService.getCachedDeviceKeys()).thenReturn(Set.of(deviceKey1, deviceKey2));
    when(sunSpecService.getCachedDiscovery(deviceKey1)).thenReturn(Optional.of(expired));
    when(sunSpecService.getCachedDiscovery(deviceKey2)).thenReturn(Optional.of(valid));

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    assertEquals(1L, response.getData().get().get("discovery.valid.count"));
    assertEquals(1L, response.getData().get().get("discovery.expired.count"));
  }

  @Test
  void testReportsModelCount() {
    healthCheck.modbusEnabled = true;
    healthCheck.discoveryRequired = false;

    String deviceKey = "localhost:502/1";
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 66),
      new SunSpecModelBlock(113, 40070, 60),
      new SunSpecModelBlock(120, 40132, 26)
    );
    SunSpecDiscoveryResult result = new SunSpecDiscoveryResult(
      40000, models, Instant.now());

    when(sunSpecService.getDiscoveryCacheSize()).thenReturn(1);
    when(sunSpecService.getCachedDeviceKeys()).thenReturn(Set.of(deviceKey));
    when(sunSpecService.getCachedDiscovery(deviceKey)).thenReturn(Optional.of(result));

    HealthCheckResponse response = healthCheck.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    assertEquals(3L, response.getData().get().get("device." + deviceKey + ".models"));
  }
}
