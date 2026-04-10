# AGENTS.md - Frodo Modbus Protocol Connector

This file provides guidance for AI coding agents working on this codebase.

## Project Overview

Frodo is a **Quarkus 3.x server application** for Modbus protocol communication with PV (photovoltaic) devices. It collects information and provides control capabilities via REST APIs, MQTT messaging, and a React frontend.

**Key Technologies:**
- Java 21, Quarkus 3.34.x, Gradle (Groovy DSL)
- Modbus TCP protocol (via Vert.x raw sockets + j2mod library)
- MQTT messaging (SmallRye Reactive Messaging)
- FirebirdSQL database (Jaybird JDBC driver)
- React 18 frontend (via Quinoa extension)

**Reference Documentation:**
- `refdoc/modbus.pdf` - Fronius Modbus TCP protocol specification
- `refdoc/solar_api.pdf` - Fronius Solar API documentation
- `refdoc/sunspec/` - SunSpec Alliance specifications:
  - `SunSpec_Information_Model_Reference_20240701_-1.xlsx` - Complete model reference
  - `SunSpec-Device-Information-Model-Specificiation-V1-4.pdf` - Core device models
  - `SunSpec-DER-Information-Model-Specification-V1-2.pdf` - DER (inverter) models
  - `SunSpec-Modbus-FactSheet-RevA-2019-07-web.pdf` - Quick reference
- `refdoc/gen24-modbus-api-external-docs/` - Fronius Gen24 register maps
- GitHub Repository: https://github.com/sunspec/models - Official SunSpec model definitions (JSON/XML)

## Build & Run Commands

```bash
./gradlew quarkusDev          # Development mode with hot reload
./gradlew build               # Build the project
./gradlew clean build         # Clean build
./gradlew build -Dquarkus.container-image.build=true  # Build Docker image
```

## Testing Commands

```bash
./gradlew test                # Run all tests
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest"           # Single test class
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest.testBuildMbapFrame"  # Single method
./gradlew test --tests "*ModbusTest*"   # Pattern matching
./gradlew testNative          # Integration tests (native mode)
```

In Quarkus dev mode, press `r` to re-run tests.

## Code Style Guidelines

### Package Structure
```
at.or.reder.frodo/
├── api/                        # REST endpoints (JAX-RS resources)
│   ├── FrodoResource            # /api/info
│   ├── DeviceResource           # /api/devices CRUD + info
│   ├── SunSpecResource          # /api/devices/{id}/sunspec/*
│   ├── dto/                     # Request/response DTOs (records)
│   └── exception/               # Exception mappers
├── solarapi/                   # Fronius Solar API integration
│   ├── SolarApiClient           # HTTP client for Solar API
│   ├── model/                   # Solar API data models
│   │   ├── PowerFlowRealtimeData # Power flow response
│   │   ├── SmartloadsData       # Ohmpilot/smartload data
│   │   └── SolarApiResponse     # Generic response wrapper
│   └── SolarApiHealthCheck      # Solar API health monitoring
├── health/                     # Health & monitoring
│   ├── FrodoHealthCheck         # Application readiness
│   ├── ModbusHealthCheck        # Modbus connection pool health
│   ├── SunSpecHealthCheck       # SunSpec discovery cache health
│   └── ModbusMetrics            # Micrometer gauges, counters, timers
├── modbus/                     # Modbus TCP protocol core
│   ├── ModbusTcpService         # Core Modbus service (FC 0x03/0x06/0x10/0x2B)
│   ├── ModbusResource           # Raw register access endpoint
│   ├── ModbusException          # Protocol error exceptions
│   ├── connection/              # Connection pool & request queue
│   │   ├── ModbusConnectionPool  # Pool lifecycle, health, stats
│   │   ├── ModbusConnection      # Single TCP connection
│   │   ├── ModbusRequestQueue    # Bounded request queue
│   │   ├── ModbusRequest         # Request envelope
│   │   ├── QueuedRequest         # Queued request wrapper
│   │   ├── ConnectionStats       # Pool statistics record
│   │   └── ConnectionState       # Connection state enum
│   ├── service/                 # Device info collection
│   │   ├── DeviceInfoCollectorService  # Scheduled collection
│   │   └── DeviceInfoCacheService      # In-memory cache
│   ├── entity/                  # JPA entities
│   │   ├── ModbusDeviceEntity    # Device configuration
│   │   └── ModbusDeviceInfoEntity # Device identification cache
│   ├── repository/              # Panache repositories
│   ├── config/                  # Device config initializer
│   ├── model/                   # Domain models
│   │   ├── DeviceIdentification  # FC 0x2B result
│   │   ├── ReadDeviceIdCode      # Read Device ID codes enum
│   │   └── ModbusObjectId        # Modbus object ID enum
│   ├── cache/                   # Cache models
│   │   └── CachedDeviceInfo      # Cached device info record
│   └── sunspec/                 # SunSpec protocol support
│       ├── SunSpecService         # Discovery, model reading, caching
│       ├── SunSpecModelRegistry   # Model definitions (all supported)
│       ├── SunSpecModelDataDecoder # Model data decoder
│       ├── SunSpecRegisterDecoder  # Data type decoder
│       ├── SunSpecConstants       # Model IDs, base addresses
│       ├── SunSpecDataType        # Data type enum
│       ├── SunSpecDiscoveryResult  # Discovery result record
│       ├── SunSpecModelBlock      # Model location record
│       ├── SunSpecModelData       # Decoded model data record
│       ├── SunSpecModelDefinition  # Model metadata record
│       └── SunSpecFieldDefinition  # Field metadata record
└── mqtt/                       # MQTT messaging service
    └── MqttService              # Publish/subscribe
```

### Import Order
1. `io.vertx.*`, `io.smallrye.*`, `io.quarkus.*` (framework imports)
2. `jakarta.*` (Jakarta EE APIs)
3. `org.eclipse.microprofile.*` (MicroProfile APIs)
4. `org.jboss.logging.*` (logging)
5. `java.*` (standard library - last)

Static imports go after regular imports.

### Formatting
- **Indentation:** 2 spaces (no tabs)
- **Encoding:** UTF-8
- **Braces:** Opening brace on same line (K&R style)

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `ModbusTcpService` |
| Methods/Variables | camelCase | `readHoldingRegisters`, `startAddr` |
| Constants | UPPER_SNAKE_CASE | `LOG`, `DEFAULT_PORT` |
| Config properties | kebab-case | `frodo.modbus.host` |
| REST paths | kebab-case | `/api/modbus/{unitId}/holding-registers` |

### Type Conventions
- Use Java **records** for DTOs and response objects
- Use `Uni<T>` for async operations (Mutiny reactive type)
- Use primitive types (`int`, `boolean`) for simple values
- Use `int[]` internally, `List<Integer>` in JSON responses

### CDI & Dependency Injection
```java
@ApplicationScoped
public class ModbusTcpService {
    @Inject
    Vertx vertx;

    @ConfigProperty(name = "frodo.modbus.host", defaultValue = "localhost")
    String modbusHost;
}
```

### REST Resources (JAX-RS)
```java
@Path("/api/modbus")
@Tag(name = "Modbus", description = "Modbus TCP device access endpoints")
public class ModbusResource {
    @GET
    @Path("/{unitId}/holding-registers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Read holding registers")
    public Uni<ModbusRegisterResponse> readHoldingRegisters(...) { }
}
```

### Logging
- Use `org.jboss.logging.Logger` (not SLF4J)
- Static logger: `private static final Logger LOG = Logger.getLogger(ClassName.class);`
- Formatted logging: `LOG.debugf("Message: %s", value);`

### Error Handling
- Throw `IllegalArgumentException` for invalid input
- Use `Uni.onFailure()` for async error handling
- Log errors: `LOG.errorf(exception, "Message: %s", context);`

### Database Naming Convention

To support integration into pre-existing databases without schema support, **all database objects use "Fro" prefix**:

| Object Type | Naming Convention | Example |
|-------------|-------------------|---------|
| Tables | `Fro<EntityName>` | `FroModbusDevice`, `FroModbusDeviceInfo` |
| Sequences | `Fro<EntityName>_SEQ` | `FroModbusDevice_SEQ`, `FroModbusDeviceInfo_SEQ` |
| Indexes | `idx_Fro<EntityName>_<column>` | `idx_FroModbusDevice_enabled` |
| Unique constraints | `uk_Fro<Short>_<purpose>` | `uk_FroDevice_connection` |
| Foreign keys | `fk_Fro<Short>_<reference>` | `fk_FroDeviceInfo_device` |
| Check constraints | `ck_Fro<Short>_<purpose>` | `ck_FroDevice_port_range` |

**Rationale:** FirebirdSQL versions before 3.0 do not support database schemas. The "Fro" prefix allows Frodo tables to coexist safely with other application data in shared databases, preventing name collisions.

**Implementation:**
- Entity classes use `@Table(name = "Fro...")` annotations
- Liquibase changesets use prefixed table/constraint names
- See `src/main/resources/db/changelog/v1.0.1-rename-tables-fro-prefix.xml` for migration from legacy names

## Git Workflow Guidelines

### CRITICAL: Code Review Before Commit

**IMPORTANT:** Before creating any git commit, you MUST:

1. **Perform a self-review:**
   - Run `git status` and `git diff --stat` to see what changed
   - Review the changes using `git diff` for key files
   - Verify all tests pass with `./gradlew test`
   - Verify build succeeds with `./gradlew build`

2. **Present changes to user for review:**
   - Show a summary of what was implemented
   - List all files created, modified, or deleted
   - Highlight key changes and design decisions
   - **WAIT for user approval before committing**

3. **Only commit after user approval:**
   - Use conventional commit format: `type(scope): description`
   - Include detailed commit body explaining the changes
   - Reference any issues or plan documents

**Never commit without explicit user approval!**

## Testing Guidelines

| Type | Suffix | Location |
|------|--------|----------|
| Unit tests | `*Test.java` | `src/test/java/` |
| Integration tests | `*IT.java` | `src/native-test/java/` |

### Unit Test Style (pure logic)
```java
class ModbusTcpServiceTest {
    @Test
    void testBuildMbapFrame() {
        byte[] frame = ModbusTcpService.buildMbapFrame(42, 1, pdu);
        assertEquals(12, frame.length);
    }
}
```

### Endpoint Test Style (QuarkusTest + RestAssured)
```java
@QuarkusTest
class FrodoResourceTest {
    @Test
    void testInfoEndpoint() {
        given()
            .when().get("/api/info")
            .then()
            .statusCode(200)
            .body("name", is("frodo"));
    }
}
```

## Project-Specific Patterns

### Modbus Protocol
- MBAP header: Transaction ID (2) + Protocol ID (2) + Length (2) + Unit ID (1)
- Function codes: 0x03 (read holding), 0x06 (write single), 0x10 (write multiple), 0x2B/0x0E (read device identification)
- Use static helper methods for frame building/parsing (testable without Vert.x)
- Protocol reference: `refdoc/modbus.pdf` (Modbus Application Protocol V1.1b3)

### SunSpec Protocol
- SunSpec "SunS" signature at register 40000 (0x53756e53)
- Model chain discovery: scan sequentially from base address
- Supported models: 
  - Common (1)
  - Inverter (101-103, 111-113)
  - Nameplate (120), Settings (121), Status (122), Controls (123), Storage (124)
  - MPPT (160)
  - Meter (201-204, 211-214) - for Smart Meter devices (Single Phase, Split Phase, 3-Phase WYE/Delta)
- Float and Int+SF register map variants supported
- Official SunSpec model definitions: https://github.com/sunspec/models (JSON/XML/SMDX formats)
- Register maps: `refdoc/gen24-modbus-api-external-docs/` (Fronius Gen24 Excel files)
- SunSpec specifications: `refdoc/sunspec/` (PDF documentation and Excel reference)
- See `docs/SUNSPEC_MODELS.md` for detailed model documentation
- See `docs/DEVICE_DISCOVERY_PLAN.md` for multi-device architecture

### Fronius Solar API
- **Base URL**: `http://{inverter-ip}/solar_api/v1/` (HTTP, port 80)
- **Key Endpoints**:
  - `GET /solar_api/v1/GetPowerFlowRealtimeData.fcgi` - Unified power flow data (all devices)
  - `GET /solar_api/v1/GetOhmPilotRealtimeData.cgi` - Ohmpilot-specific data
  - `GET /solar_api/v1/GetActiveDeviceInfo.cgi?DeviceClass={class}` - Device discovery
- **PowerFlowRealtimeData Structure**:
  - `Inverters` - Map of inverter data by DeviceId
  - `Site` - Aggregated site-level metrics (P_Grid, P_Load, P_PV, P_Akku)
  - `Smartloads.Ohmpilots` - Map of Ohmpilot data by ComponentId
    - `P_AC_Total` - Power consumption [W]
    - `State` - Operating state (normal, boost, fault, etc.)
    - `Temperature` - Tank/storage temperature [°C]
  - `Smartloads.OhmpilotEcos` - Ohmpilot Eco devices (dual heating rods)
  - `SecondaryMeters` - Additional meter data
- **Use Case**: Device discovery and metrics collection without Modbus complexity
- **Integration**: Complementary to Modbus (Solar API for discovery, Modbus for control)

### Async/Reactive
- Use Vert.x Mutiny APIs (`io.vertx.mutiny.*`)
- Return `Uni<T>` from async methods
- Chain: `.onItem().transform()`, `.onFailure().invoke()`
- Close resources: `socket.closeAndForget()`

### Configuration Profiles
- `%dev.` prefix for development overrides
- `%test.` prefix for test overrides
- External services disabled in dev/test by default

## Key Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/info` | Application info |
| `GET /api/devices` | List all devices |
| `POST /api/devices` | Create a device |
| `GET /api/devices/{id}` | Get device details |
| `PUT /api/devices/{id}` | Update a device |
| `DELETE /api/devices/{id}` | Delete a device |
| `GET /api/devices/{id}/info` | Cached device identification (FC 0x2B) |
| `POST /api/devices/{id}/info/refresh` | Force refresh device identification |
| `GET /api/devices/{id}/sunspec/discovery` | SunSpec model chain discovery |
| `GET /api/devices/{id}/sunspec/common` | SunSpec Common model (1) |
| `GET /api/devices/{id}/sunspec/inverter` | Auto-detect inverter model |
| `GET /api/devices/{id}/sunspec/nameplate` | Nameplate ratings (120) |
| `GET /api/devices/{id}/sunspec/settings` | Basic settings (121) |
| `GET /api/devices/{id}/sunspec/status` | Extended measurements & status (122) |
| `GET /api/devices/{id}/sunspec/controls` | Immediate controls (123) |
| `GET /api/devices/{id}/sunspec/storage` | Basic storage controls (124) |
| `GET /api/devices/{id}/sunspec/mppt` | Multiple MPPT extension (160) |
| `GET /api/devices/{id}/sunspec/model/{modelId}` | Read any model by ID |
| `GET /api/devices/{id}/sunspec/models` | List all available models |
| `GET /api/modbus/{unitId}/holding-registers` | Read Modbus registers (FC 0x03) |
| `GET /q/health` | Health check |
| `GET /q/metrics` | Prometheus metrics |
| `GET /swagger-ui` | Swagger UI |
