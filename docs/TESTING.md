# Testing Guide

This document describes how to run tests, the testing architecture, and how to test specific features in the Frodo project.

## Running Tests

### All Tests

```bash
./gradlew test
```

### Single Test Class

```bash
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest"
```

### Single Test Method

```bash
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest.testBuildMbapFrame"
```

### Pattern Matching

```bash
./gradlew test --tests "*SunSpec*"           # All SunSpec tests
./gradlew test --tests "*HealthCheck*"       # All health check tests
./gradlew test --tests "*ModbusMetrics*"     # Metrics tests
```

### Native Integration Tests

```bash
./gradlew testNative
```

### Dev Mode (Continuous Testing)

```bash
./gradlew quarkusDev
# Press 'r' to re-run tests
```

## Test Architecture

### Test Types

| Type | Suffix | Location | Framework |
|------|--------|----------|-----------|
| Unit tests | `*Test.java` | `src/test/java/` | JUnit 5 + Mockito |
| Integration tests | `*IT.java` | `src/native-test/java/` | QuarkusTest + RestAssured |

### Test Profile Configuration

Tests run with the `%test` profile, which disables external dependencies:

- **Database**: Hibernate ORM and datasource are disabled (`%test.quarkus.datasource.active=false`)
- **MQTT**: Messaging connectors are disabled
- **Quinoa**: React UI build is disabled
- **Device seeding**: Config-based device creation is disabled

This means unit tests do not require a running Firebird database or MQTT broker.

### Mocking Strategy

Tests use Mockito for dependency injection:

```java
@ExtendWith(MockitoExtension.class)
class ModbusHealthCheckTest {

  @Mock
  ModbusConnectionPool connectionPool;

  @Mock
  ModbusDeviceRepository deviceRepository;

  // Test subject constructed with mocked dependencies
  private ModbusHealthCheck healthCheck;

  @BeforeEach
  void setUp() {
    healthCheck = new ModbusHealthCheck();
    // Inject mocks via reflection or constructor
  }
}
```

For CDI beans that cannot be easily constructed, use `@InjectMock` in `@QuarkusTest` classes.

## Test Inventory

### Modbus Protocol Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `ModbusTcpServiceFrameTest` | Frame building/parsing | MBAP header construction, PDU validation |
| `ModbusTcpServiceTest` | Frame building | `buildMbapFrame()` static method tests |
| `ModbusTcpServiceWriteTest` | Write frame building | FC 0x06 and FC 0x10 PDU construction |
| `ModbusDeviceIdentificationTest` | FC 0x2B parsing | Device identification response parsing |
| `ModbusExceptionTest` | Exception codes | Modbus error code handling |

### Connection Infrastructure Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `ConnectionStatsTest` | Record equality | ConnectionStats record tests |
| `ConnectionStateTest` | Enum values | Connection state transitions |
| `ModbusRequestTest` | Request envelope | Request creation and properties |

### Device Management Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `ModbusDeviceEntityTest` | Entity CRUD | Device entity field validation |
| `ModbusDeviceInfoEntityTest` | Info entity | Device identification entity |
| `DeviceIdentificationTest` | Domain model | DeviceIdentification record |
| `ReadDeviceIdCodeTest` | Enum mapping | FC 0x2B ID code categories |
| `ModbusObjectIdTest` | Enum mapping | Modbus object ID constants |
| `CachedDeviceInfoTest` | Cache model | CachedDeviceInfo record |

### Service Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `DeviceInfoCollectorServiceTest` | Scheduling | Scheduled collection logic |
| `DeviceInfoCacheServiceTest` | Cache TTL | Cache expiry and refresh |

### SunSpec Protocol Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `SunSpecConstantsTest` | Constants | Model IDs, base addresses, helper methods |
| `SunSpecDataTypeTest` | Data types | Type parsing, register counts, signed flags |
| `SunSpecFieldDefinitionTest` | Field defs | Field metadata creation and validation |
| `SunSpecModelDefinitionTest` | Model defs | Model metadata, field lookup |
| `SunSpecModelRegistryTest` | Registry | All model definitions, field counts |
| `SunSpecRegisterDecoderTest` | Decoding | Raw registers to typed values |
| `SunSpecModelDataDecoderTest` | Model decoding | Full model data decoding pipeline |
| `SunSpecModelDataTest` | Data model | Decoded model data record |
| `SunSpecDiscoveryResultTest` | Discovery | Discovery result caching |

### REST API Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `FrodoResourceTest` | `/api/info` | Application info endpoint |
| `SunSpecResourceTest` | `/api/devices/{id}/sunspec/*` | SunSpec endpoint error handling |
| `SunSpecDtoTest` | DTOs | Response DTO serialization |

### Health & Monitoring Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `ModbusHealthCheckTest` | 7 tests | Connection pool health: UP/DOWN/WARN scenarios |
| `SunSpecHealthCheckTest` | 8 tests | Discovery cache health: UP/DOWN/WARN scenarios |
| `ModbusMetricsTest` | 17 tests | Gauge, counter, timer registration and updates |

### Other Tests

| Test Class | Tests | Description |
|------------|-------|-------------|
| `FrodoVersionTest` | Version | Application version resolution |

## Testing SunSpec Endpoints

SunSpec endpoints require a real or simulated Modbus device. For manual testing:

### With a Real Device

1. Configure a device in the database or via the seed configuration
2. Start the server: `./gradlew quarkusDev`
3. Test endpoints:

```bash
# Discover SunSpec models
curl -s http://localhost:8080/api/devices/1/sunspec/discovery | jq .

# Expected response:
# {
#   "unitId": 1,
#   "baseAddress": 40000,
#   "models": [
#     { "modelId": 1, "name": "Common", "address": 40002, "length": 66 },
#     { "modelId": 113, "name": "Inverter (Three Phase, Float)", "address": 40070, "length": 60 },
#     ...
#   ],
#   "discoveryTime": "2026-03-29T14:00:00Z"
# }

# Read Common model (device identification)
curl -s http://localhost:8080/api/devices/1/sunspec/common | jq .

# Read inverter data (auto-detects Float vs Int+SF)
curl -s http://localhost:8080/api/devices/1/sunspec/inverter | jq .

# Read nameplate ratings
curl -s http://localhost:8080/api/devices/1/sunspec/nameplate | jq .

# Force cache refresh
curl -s "http://localhost:8080/api/devices/1/sunspec/discovery?refresh=true" | jq .
```

### With a Modbus Simulator

For testing without real hardware, use [pymodbus](https://github.com/pymodbus-dev/pymodbus) or similar:

```bash
# Install pymodbus
pip install pymodbus

# Start a Modbus TCP simulator on port 502
# Configure SunSpec registers at address 40000+
```

### Testing Health Endpoints

```bash
# Check all health checks (includes Modbus + SunSpec)
curl -s http://localhost:8080/q/health | jq .

# Check readiness only
curl -s http://localhost:8080/q/health/ready | jq .

# Expected output includes:
# - "frodo-readiness" (always UP)
# - "modbus-connection" (UP/DOWN based on pool status)
# - "sunspec-discovery" (UP/DOWN based on cache status)
```

### Testing Metrics

```bash
# Prometheus metrics endpoint
curl -s http://localhost:8080/q/metrics

# Filter for Frodo-specific metrics
curl -s http://localhost:8080/q/metrics | grep "frodo\."

# Key metrics:
# frodo_modbus_connection_active          - Active connections (gauge)
# frodo_modbus_queue_size                 - Queue depth (gauge)
# frodo_modbus_requests_total             - Total requests (counter)
# frodo_modbus_request_duration_seconds   - Request latency (timer)
# frodo_sunspec_discovery_cached          - Cached discoveries (gauge)
# frodo_sunspec_discovery_total           - Discovery attempts (counter)
# frodo_sunspec_model_reads_total         - Model read attempts (counter)
```

## Writing New Tests

### Unit Test Template

```java
package at.or.reder.frodo.modbus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyFeatureTest {

  @Test
  void testExpectedBehavior() {
    // Arrange
    var input = ...;

    // Act
    var result = MyFeature.process(input);

    // Assert
    assertEquals(expected, result);
  }
}
```

### Mocked Service Test Template

```java
package at.or.reder.frodo.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyServiceTest {

  @Mock
  SomeDependency dependency;

  private MyService service;

  @BeforeEach
  void setUp() {
    service = new MyService(dependency);
  }

  @Test
  void testWithMockedDependency() {
    when(dependency.getData()).thenReturn("test");

    var result = service.process();

    assertEquals("expected", result);
    verify(dependency).getData();
  }
}
```

### QuarkusTest Endpoint Template

```java
package at.or.reder.frodo.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class MyResourceTest {

  @Test
  void testEndpoint() {
    given()
      .when().get("/api/my-endpoint")
      .then()
      .statusCode(200)
      .body("field", is("value"));
  }
}
```

## Known Test Considerations

- **CDI ambiguity**: Avoid creating inner stub classes that extend CDI beans in test classes. Quarkus scans test classes and will cause `AmbiguousResolutionException`. Use Mockito `@Mock` instead.
- **HealthCheckResponse types**: `withData(String, long)` stores `Long`. Test assertions must compare with `1L` not `1` to avoid `Integer` vs `Long` mismatches.
- **Lenient mocking**: Some test classes use `@MockitoSettings(strictness = Strictness.LENIENT)` when `@BeforeEach` stubs are overridden by individual tests (e.g., `ModbusMetricsTest`).
- **No database in tests**: The `%test` profile disables all database access. Tests that need repositories must mock them.
