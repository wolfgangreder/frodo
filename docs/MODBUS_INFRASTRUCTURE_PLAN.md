# Modbus Basic Infrastructure Implementation Plan
## Frodo PV Device Connection System

**Version:** 1.0  
**Date:** March 2026  
**Status:** Ready for Implementation

---

## Executive Summary

This plan details the implementation of a robust Modbus TCP infrastructure for connecting to PV devices, collecting device identification information, and exposing it via REST API. The system will:

- Manage persistent connections with queueing to minimize concurrent requests
- Read standard Modbus Device Identification (vendor, product, version)
- **Support SunSpec Modbus protocol for PV inverter data (models 1, 101-103, 111-113, 120-132)**
- Store device configurations in FirebirdSQL 5.0+
- Use H2 Database for testing
- Schedule automatic data collection (1 request/minute target)
- Provide REST API for device management and data access
- Include comprehensive monitoring and health checks
- Keep the service K8s compliant
- Support multi-device expansion from the start

**Timeline:** 12-18 days | **7 Stages** | **Target:** Single device working, multi-device ready

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      REST API Layer                         │
│  /api/devices, /api/devices/{id}/info, /api/modbus/...    │
│  /api/devices/{id}/sunspec/* (11 SunSpec endpoints)       │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│              Service Layer                                   │
│  DeviceInfoCollectorService (Scheduled)                     │
│  ModbusTcpService (Protocol)                                │
│  SunSpecService (SunSpec protocol + model registry)        │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│         Connection Infrastructure                            │
│  ModbusConnectionPool → ModbusConnection → Vert.x Socket   │
│  ModbusRequestQueue (Serialization)                         │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│              Data Layer                                      │
│  FirebirdSQL: modbus_device, modbus_device_info            │
│  In-Memory Cache: DeviceInfoCache, SunSpec Discovery Cache │
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Stack Additions

### Dependencies to Add (build.gradle):
```gradle
// Database ORM (Stage 3)
implementation 'io.quarkus:quarkus-hibernate-orm-panache'

// Database migrations (Stage 3)
implementation 'io.quarkus:quarkus-flyway'

// Testing (All Stages)
testImplementation 'io.quarkus:quarkus-test-vertx'
testImplementation 'org.mockito:mockito-core:5.10.0'
testImplementation 'org.testcontainers:testcontainers:1.19.0'
```

---

## Stage 1: Connection Pool & Request Queue

**Duration:** 2-3 days | **Priority:** CRITICAL | **Dependencies:** None

### Objectives
- Single persistent TCP connection to configured Modbus device
- FIFO request queue to serialize operations (1 request at a time)
- Auto-reconnect with exponential backoff
- Connection lifecycle management and monitoring

### Package Structure
```
at.or.reder.frodo.modbus/
├── connection/
│   ├── ModbusConnection.java           # Single connection lifecycle
│   ├── ModbusRequestQueue.java         # Request serialization
│   ├── ModbusConnectionPool.java       # Pool manager (single device)
│   ├── ConnectionState.java            # Enum: DISCONNECTED, CONNECTING, CONNECTED, FAILED
│   └── ConnectionStats.java            # Record: stats for monitoring
└── ModbusTcpService.java               # Refactored to use pool
```

### Implementation Details

#### 1.1 ModbusConnection.java
**Responsibilities:**
- Manage NetClient connection lifecycle
- Connection states: DISCONNECTED → CONNECTING → CONNECTED → FAILED
- Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, max 60s
- Connection timeout: configurable (default 30s)
- Track last successful request timestamp

**Key Methods:**
```java
@ApplicationScoped
public class ModbusConnection {
    private volatile ConnectionState state;
    private volatile NetSocket socket;
    private volatile Instant lastSuccessTime;
    private final AtomicInteger reconnectDelay;
    
    public Uni<Void> connect(String host, int port)
    public Uni<Void> disconnect()
    public Uni<byte[]> sendRequest(byte[] request, Duration timeout)
    public ConnectionState getState()
    public Instant getLastSuccessTime()
    public boolean isHealthy()
    
    private Uni<Void> reconnectWithBackoff()
}
```

**Connection Logic:**
- On failure: schedule reconnect with current delay
- On success: reset delay to 1s
- Max reconnect attempts: unlimited (keep trying)
- Emit connection state events for monitoring

#### 1.2 ModbusRequestQueue.java
**Responsibilities:**
- LinkedBlockingQueue for serializing requests
- Process one request at a time
- Timeout handling per request
- Queue capacity limit (reject when full)

**Key Methods:**
```java
@ApplicationScoped
public class ModbusRequestQueue {
    private final BlockingQueue<QueuedRequest<?>> queue;
    private volatile boolean running;
    
    public <T> Uni<T> enqueue(ModbusRequest<T> request, Duration timeout)
    public int getQueueSize()
    public void start()
    public void stop()
    
    private void processQueue() // Background worker
}

public record QueuedRequest<T>(
    ModbusRequest<T> request,
    UniEmitter<T> emitter,
    Instant enqueuedAt,
    Duration timeout
) {}
```

**Queue Processing:**
- Single background thread processes queue
- Dequeue → Execute → Complete/Fail → Next
- Timeout check before execution
- Log queue wait times for monitoring

#### 1.3 ModbusConnectionPool.java
**Responsibilities:**
- Integrate connection + queue
- Lifecycle management (@Startup, @PreDestroy)
- Load device config from properties (Stage 1) or database (Stage 3)
- Expose connection stats for metrics

**Key Methods:**
```java
@ApplicationScoped
@Startup
public class ModbusConnectionPool {
    @Inject ModbusConnection connection;
    @Inject ModbusRequestQueue queue;
    
    void onStart(@Observes StartupEvent event)
    void onStop(@Observes ShutdownEvent event)
    
    public Uni<byte[]> executeRequest(byte[] request)
    public ConnectionStats getStats()
}

public record ConnectionStats(
    ConnectionState state,
    int queueSize,
    Instant lastSuccessTime,
    long totalRequests,
    long failedRequests
) {}
```

#### 1.4 Configuration Properties
```properties
# Stage 1: Single Device Configuration
frodo.modbus.device.host=localhost
frodo.modbus.device.port=502
frodo.modbus.device.unit-id=1
frodo.modbus.device.name=Default PV Device

# Connection Pool Settings
frodo.modbus.connection.timeout-seconds=30
frodo.modbus.connection.reconnect-initial-delay-seconds=1
frodo.modbus.connection.reconnect-max-delay-seconds=60
frodo.modbus.connection.idle-timeout-seconds=300

# Request Queue Settings
frodo.modbus.request.queue-capacity=50
frodo.modbus.request.timeout-seconds=10
frodo.modbus.request.max-retries=3
frodo.modbus.request.retry-delay-seconds=2
```

#### 1.5 Refactor ModbusTcpService
**Changes:**
```java
@ApplicationScoped
public class ModbusTcpService {
    @Inject
    ModbusConnectionPool connectionPool; // NEW: instead of Vertx
    
    // REMOVE: Vertx injection, NetClient creation
    
    public Uni<int[]> readHoldingRegisters(int unitId, int startAddr, int count) {
        byte[] request = buildReadHoldingRegistersRequest(unitId, startAddr, count, getNextTransactionId());
        
        return connectionPool.executeRequest(request)
            .onItem().transform(response -> parseReadHoldingRegistersResponse(response, count))
            .onFailure().retry().withBackOff(Duration.ofSeconds(2)).atMost(3);
    }
    
    // NEW: Transaction ID management
    private final AtomicInteger transactionIdCounter = new AtomicInteger(1);
    private int getNextTransactionId() {
        return transactionIdCounter.getAndIncrement() & 0xFFFF;
    }
}
```

#### 1.6 Tests
**Unit Tests:**
- `ModbusConnectionTest.java` - Connection lifecycle, reconnect logic, state transitions
- `ModbusRequestQueueTest.java` - Queue ordering, timeout, capacity limits
- `ModbusConnectionPoolTest.java` - Integration of connection + queue
- `ModbusTcpServiceTest.java` - Update existing tests, verify retry logic

**Integration Tests:**
- `ModbusTcpServiceIntegrationTest.java` - Mock Modbus server with Vert.x

### Deliverables
- [ ] Persistent connection with auto-reconnect
- [ ] Request serialization queue
- [ ] Refactored ModbusTcpService using pool
- [ ] Unit tests (>80% coverage)
- [ ] Integration tests with mock server
- [ ] Updated configuration properties

---

## Stage 2: Modbus Device Identification (FC 0x2B/0x0E)

**Duration:** 2-3 days | **Priority:** HIGH | **Dependencies:** Stage 1

### Objectives
- Implement Modbus Read Device Identification protocol
- Support Basic Device Identification (vendor, product code, revision)
- Parse Modbus encapsulated response format
- Handle segmented responses (More Follows flag)

### Package Structure
```
at.or.reder.frodo.modbus/
├── model/
│   ├── DeviceIdentification.java       # Main domain model
│   ├── ReadDeviceIdCode.java           # Enum: BASIC, REGULAR, EXTENDED, SPECIFIC
│   └── ModbusObjectId.java             # Constants: VENDOR_NAME, PRODUCT_CODE, etc.
├── ModbusTcpService.java               # Add device ID methods
└── ModbusException.java                # Custom exception
```

### Implementation Details

#### 2.1 DeviceIdentification.java
```java
public record DeviceIdentification(
    String vendorName,           // Object ID 0x00 (required)
    String productCode,          // Object ID 0x01 (required)
    String majorMinorRevision,   // Object ID 0x02 (required)
    String vendorUrl,            // Object ID 0x03 (optional)
    String productName,          // Object ID 0x04 (optional)
    String modelName,            // Object ID 0x05 (optional)
    String userApplicationName,  // Object ID 0x06 (optional)
    Map<Integer, String> additionalObjects,
    Instant readTime
) {}
```

#### 2.2 ReadDeviceIdCode.java
```java
public enum ReadDeviceIdCode {
    BASIC(0x01),        // VendorName, ProductCode, Revision
    REGULAR(0x02),      // Basic + URL, ProductName, ModelName
    EXTENDED(0x03),     // Regular + UserApplicationName
    SPECIFIC(0x04);     // Read specific object
}
```

#### 2.3 ModbusTcpService additions
**New Methods:**
```java
public Uni<DeviceIdentification> readDeviceIdentification(int unitId, ReadDeviceIdCode readCode)
static byte[] buildReadDeviceIdentificationRequest(int unitId, ReadDeviceIdCode readCode, int objectId, int transactionId)
static DeviceIdentification parseReadDeviceIdentificationResponse(byte[] response)
```

**Frame Structure (FC 0x2B/0x0E):**
- MBAP Header (7 bytes)
- Function Code: 0x2B (1 byte)
- MEI Type: 0x0E (1 byte)
- Read Device ID Code: 0x01-0x04 (1 byte)
- Object ID: 0x00-0xFF (1 byte)

**Response Parsing:**
- Handle conformity level
- Parse object list (object ID + length + value)
- Handle "More Follows" flag (segmented responses)
- Handle exception responses (FC = 0xAB)

#### 2.4 Tests
**Unit Tests:**
- `ModbusDeviceIdentificationTest.java`
  - Frame building for FC 0x2B/0x0E
  - Response parsing with mock data (Basic ID)
  - Response parsing with extended objects
  - Handle malformed responses
  - Handle exception codes

**Integration Tests:**
- `ModbusDeviceIdentificationIT.java` - Mock server returns device identification

### Deliverables
- [ ] DeviceIdentification domain model
- [ ] Read Device Identification implementation (FC 0x2B/0x0E)
- [ ] Response parsing with validation
- [ ] Unit tests (>85% coverage)
- [ ] Integration tests with mock device
- [ ] Exception handling (Modbus exceptions)

---

## Stage 3: Database Schema & Device Configuration

**Duration:** 2-3 days | **Priority:** HIGH | **Dependencies:** Stage 1

### Objectives
- Design FirebirdSQL 5.0+ schema for device configurations
- Implement Hibernate ORM entities with Panache
- Database migrations using Flyway
- Seed initial device from configuration properties
- Multi-device support foundation

### Package Structure
```
at.or.reder.frodo.modbus/
├── entity/
│   ├── ModbusDeviceEntity.java         # Device configuration
│   └── ModbusDeviceInfoEntity.java     # Device identification cache
├── repository/
│   └── ModbusDeviceRepository.java     # Data access layer
├── config/
│   └── DeviceConfigInitializer.java    # Seed from properties
└── dto/
    └── ModbusDeviceConfig.java         # Configuration DTO
```

### Database Schema

#### V1.0.0__create_modbus_tables.sql
```sql
CREATE TABLE modbus_device (
    id INTEGER NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    unit_id INTEGER NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    description VARCHAR(1000),
    connection_timeout_seconds INTEGER DEFAULT 30,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_device_connection UNIQUE(host, port, unit_id),
    CONSTRAINT ck_port_range CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_unit_id_range CHECK (unit_id BETWEEN 1 AND 247)
);

CREATE SEQUENCE seq_modbus_device START WITH 1000;

CREATE TABLE modbus_device_info (
    id INTEGER NOT NULL PRIMARY KEY,
    device_id INTEGER NOT NULL,
    vendor_name VARCHAR(255),
    product_code VARCHAR(255),
    revision VARCHAR(255),
    vendor_url VARCHAR(500),
    product_name VARCHAR(255),
    model_name VARCHAR(255),
    user_app_name VARCHAR(255),
    conformity_level INTEGER,
    last_read_at TIMESTAMP,
    last_read_success BOOLEAN,
    last_error_message VARCHAR(1000),
    read_attempt_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_device_info_device FOREIGN KEY (device_id) 
        REFERENCES modbus_device(id) ON DELETE CASCADE,
    CONSTRAINT uk_device_info_device UNIQUE(device_id)
);

CREATE SEQUENCE seq_modbus_device_info START WITH 1000;

CREATE INDEX idx_device_enabled ON modbus_device(enabled);
CREATE INDEX idx_device_info_last_read ON modbus_device_info(last_read_at);
```

### Entity Classes

#### ModbusDeviceEntity.java
```java
@Entity
@Table(name = "modbus_device")
public class ModbusDeviceEntity extends PanacheEntity {
    @Column(nullable = false, length = 255)
    @NotBlank
    public String name;
    
    @Column(nullable = false, length = 255)
    @NotBlank
    public String host;
    
    @Column(nullable = false)
    @Min(1) @Max(65535)
    public int port;
    
    @Column(name = "unit_id", nullable = false)
    @Min(1) @Max(247)
    public int unitId;
    
    @Column(nullable = false)
    public boolean enabled = true;
    
    public String description;
    
    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL)
    public ModbusDeviceInfoEntity deviceInfo;
}
```

#### ModbusDeviceInfoEntity.java
```java
@Entity
@Table(name = "modbus_device_info")
public class ModbusDeviceInfoEntity extends PanacheEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    public ModbusDeviceEntity device;
    
    public String vendorName;
    public String productCode;
    public String revision;
    public String vendorUrl;
    public String productName;
    public String modelName;
    public String userAppName;
    public Instant lastReadAt;
    public Boolean lastReadSuccess;
    public String lastErrorMessage;
    
    public void updateFrom(DeviceIdentification identification, boolean success, String error);
    public DeviceIdentification toDeviceIdentification();
}
```

#### ModbusDeviceRepository.java
```java
@ApplicationScoped
public class ModbusDeviceRepository {
    public Uni<Optional<ModbusDeviceEntity>> findFirstEnabled()
    public Uni<Optional<ModbusDeviceEntity>> findByIdWithInfo(Long id)
    public Uni<List<ModbusDeviceEntity>> listAll()
    public Uni<List<ModbusDeviceEntity>> listAllEnabled()
    public Uni<ModbusDeviceEntity> save(ModbusDeviceEntity entity)
    public Uni<Boolean> deleteById(Long id)
    public Uni<ModbusDeviceInfoEntity> findOrCreateDeviceInfo(Long deviceId)
}
```

#### DeviceConfigInitializer.java
- Reads `frodo.modbus.device.*` properties
- Creates device entity if not exists in DB on startup
- Provides Stage 1 compatibility

### Configuration
```properties
# Flyway migrations
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration

# Hibernate ORM
quarkus.hibernate-orm.database.generation=none

# Device seeding
frodo.modbus.device.host=localhost
frodo.modbus.device.port=502
frodo.modbus.device.unit-id=1
frodo.modbus.device.name=Default PV Device
frodo.modbus.device.enabled=false
frodo.modbus.device.seed-from-config=true
```

### Deliverables
- [ ] FirebirdSQL 5.0+ schema with migrations
- [ ] Hibernate ORM entities with validation
- [ ] Repository layer with reactive API
- [ ] Configuration seeding from properties
- [ ] Connection pool updated to use database
- [ ] Unit & integration tests
- [ ] Multi-device foundation ready

---

## Stage 4: Scheduled Collection & Caching

**Duration:** 2 days | **Priority:** MEDIUM | **Dependencies:** Stage 2, 3

### Objectives
- Scheduled job to collect device identification (every 5 minutes)
- Store results in database (modbus_device_info table)
- In-memory cache for fast REST access
- Retry logic on failures (3 attempts)

### Package Structure
```
at.or.reder.frodo.modbus/
├── service/
│   ├── DeviceInfoCollectorService.java     # Scheduled collection
│   └── DeviceInfoCacheService.java         # Cache management
└── cache/
    └── CachedDeviceInfo.java               # Cache model
```

### Implementation Details

#### DeviceInfoCacheService.java
```java
@ApplicationScoped
public class DeviceInfoCacheService {
    private final ConcurrentHashMap<Long, CachedDeviceInfo> cache;
    
    public Optional<DeviceIdentification> get(Long deviceId)
    public void put(Long deviceId, DeviceIdentification identification)
    public void invalidate(Long deviceId)
    public void clear()
    
    @Scheduled(every = "5m")
    void cleanupExpired()
}
```

#### CachedDeviceInfo.java
```java
public record CachedDeviceInfo(
    DeviceIdentification identification,
    Instant cachedAt,
    Instant expiresAt
) {
    public boolean isExpired();
    public Duration age();
}
```

#### DeviceInfoCollectorService.java
```java
@ApplicationScoped
public class DeviceInfoCollectorService {
    @Scheduled(every = "${frodo.modbus.device-info.refresh-interval:5m}")
    void collectAllDeviceInfo()
    
    public Uni<Void> collectForDevice(ModbusDeviceEntity device)
    public Uni<DeviceIdentification> refreshDevice(Long deviceId)
    
    // Retry logic: 3 attempts with exponential backoff
}
```

### Configuration
```properties
frodo.modbus.device-info.refresh-interval=5m
frodo.modbus.device-info.cache-ttl-minutes=60
frodo.modbus.device-info.retry-attempts=3
frodo.modbus.device-info.retry-delay-seconds=5
```

### Deliverables
- [ ] In-memory cache with TTL expiration
- [ ] Scheduled device info collection (5 minutes)
- [ ] Retry logic (3 attempts with backoff)
- [ ] Database updates with timestamps
- [ ] Metrics for monitoring
- [ ] Unit tests for cache and collector

---

## Stage 5: REST API for Device Management

**Duration:** 2 days | **Priority:** HIGH | **Dependencies:** Stage 3, 4

### Objectives
- REST endpoints for device CRUD operations
- Expose device identification (cached and on-demand)
- Full OpenAPI documentation
- Error handling with structured responses

### Package Structure
```
at.or.reder.frodo/
├── api/
│   ├── DeviceResource.java                # Device REST endpoints
│   ├── dto/
│   │   ├── DeviceResponse.java            # Response DTOs
│   │   ├── DeviceRequest.java             # Request DTOs
│   │   └── ErrorResponse.java             # Error DTO
│   └── exception/
│       ├── DeviceNotFoundException.java
│       ├── DeviceConnectionException.java
│       └── GlobalExceptionMapper.java
```

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/devices | List all devices |
| GET | /api/devices/{id} | Get device details + identification |
| GET | /api/devices/{id}/info | Get device identification (cached) |
| GET | /api/devices/{id}/info?refresh=true | Force refresh |
| POST | /api/devices | Create new device |
| PUT | /api/devices/{id} | Update device |
| DELETE | /api/devices/{id} | Delete device |
| GET | /api/devices/{id}/test-connection | Test connectivity |

### Response DTOs
```java
public record DeviceListResponse(List<DeviceSummary> devices, int total) {}

public record DeviceSummary(
    Long id, String name, String host, int port, int unitId,
    boolean enabled, ConnectionStatus connectionStatus,
    Instant lastSuccessfulRead, boolean hasDeviceInfo
) {}

public record DeviceDetailResponse(
    Long id, String name, String host, int port, int unitId,
    boolean enabled, String description,
    DeviceIdentificationDto identification,
    Instant lastUpdated, boolean cached, ConnectionStatus connectionStatus
) {}

public record DeviceIdentificationDto(
    String vendorName, String productCode, String revision,
    String vendorUrl, String productName, String modelName,
    String userApplicationName, Instant readTime
) {}

public record ErrorResponse(
    int status, String error, String message,
    Instant timestamp, String path
) {}
```

### Error Handling
- 404: Device not found
- 503: Device unreachable
- 400: Validation errors
- 500: Unexpected errors

### Deliverables
- [ ] Full REST API for device management
- [ ] Device CRUD operations
- [ ] Device identification endpoints (cached/fresh)
- [ ] Connection test endpoint
- [ ] OpenAPI documentation
- [ ] Error handling with structured responses
- [ ] Integration tests with RestAssured

---

## Stage 6: Health Checks & Monitoring

**Duration:** 1-2 days | **Priority:** MEDIUM | **Dependencies:** Stage 1, 4, 5

### Objectives
- Modbus-specific health checks (basic protocol + SunSpec)
- Prometheus metrics for monitoring (including SunSpec discovery/reads)
- Dashboard-ready observability

### Implementation Details

#### ModbusHealthCheck.java
```java
@Readiness
@ApplicationScoped
public class ModbusHealthCheck implements HealthCheck {
    // Check: enabled devices exist
    // Check: connection pool status
    // Check: last successful read within threshold
    // Check: at least one device has info
}
```

**Health Criteria:**
- DOWN if no enabled devices
- DOWN if all devices failed last read
- DOWN if no successful read in last 15 minutes
- UP if at least one device readable

#### SunSpecHealthCheck.java (NEW)
```java
@Readiness
@ApplicationScoped
public class SunSpecHealthCheck implements HealthCheck {
    // Check: SunSpec discovery successful for at least one device
    // Check: Common model (ID 1) readable
    // Check: Inverter model data availability
    // Check: SunSpec cache status
}
```

**SunSpec Health Criteria:**
- DOWN if no device supports SunSpec
- DOWN if all SunSpec reads fail
- WARN if discovery cache expired
- UP if at least one device has valid SunSpec data

#### ModbusMetrics.java
```java
@ApplicationScoped
public class ModbusMetrics {
    // Gauges
    - frodo.modbus.connection.active (0 or 1)
    - frodo.modbus.queue.size
    - frodo.modbus.devices.total
    - frodo.modbus.devices.enabled
    - frodo.sunspec.discovery.cached{unit_id} (1 if cached, 0 otherwise)
    - frodo.sunspec.models.total{unit_id} (count of discovered models)
    
    // Counters
    - frodo.modbus.requests.total{status=success|failure}
    - frodo.modbus.device_info.reads.total{status=success|failure}
    - frodo.sunspec.discovery.total{status=success|failure,unit_id}
    - frodo.sunspec.model.reads.total{status=success|failure,model_id,unit_id}
    - frodo.sunspec.cache.invalidations.total{unit_id}
    
    // Timers
    - frodo.modbus.request.duration
    - frodo.modbus.device_info.read.duration
    - frodo.sunspec.discovery.duration{unit_id}
    - frodo.sunspec.model.read.duration{model_id,unit_id}
}
```

### Configuration
```properties
frodo.modbus.health.max-age-minutes=15
frodo.sunspec.health.discovery-required=false
frodo.sunspec.health.max-cache-age-hours=24
```

### Deliverables
- [ ] Modbus-specific health check
- [ ] SunSpec-specific health check (discovery, model reads)
- [ ] Prometheus metrics (basic Modbus + SunSpec)
- [ ] Health endpoint integration
- [ ] Metrics endpoint ready for scraping
- [ ] SunSpec discovery/read success/failure tracking
- [ ] Tests for health checks

---

## Stage 7: Documentation & Polish

**Duration:** 1-2 days | **Priority:** LOW | **Dependencies:** All

### Objectives
- Update README with complete setup guide (basic Modbus + SunSpec)
- Document REST API endpoints with examples (Device + SunSpec APIs)
- Testing tools documentation
- Database schema reference
- SunSpec model registry documentation

### Tasks
1. Update README.md with Modbus + SunSpec features
2. Create docs/TESTING.md (include SunSpec endpoint tests)
3. Update AGENTS.md (add SunSpec package structure)
4. Create example configurations (include SunSpec settings if any)
5. API usage examples (curl) for Device + SunSpec endpoints
6. Create docs/SUNSPEC_MODELS.md (NEW)
7. Document SunSpec data types and register decoding

### SunSpec-Specific Documentation

#### docs/SUNSPEC_MODELS.md
Document the SunSpec implementation:
- Supported models: Common (1), Inverter (101-103, 111-113), Nameplate (120), Settings (121, 122, 123), Status (124, 125, 126), Controls (127, 128, 129, 130, 131, 132)
- Model discovery process and caching strategy
- SunSpec data types: uint16, int16, uint32, int32, float32, acc32, acc64, string, enum, bitfield
- Register address mapping and offsets
- Scale factors and calculated values
- Example raw Modbus data → decoded model data

#### API Documentation Enhancements
- **GET /api/devices/{id}/sunspec/discovery** - Model chain discovery with caching
- **GET /api/devices/{id}/sunspec/common** - Common model (device ID)
- **GET /api/devices/{id}/sunspec/inverter** - Auto-detect inverter model
- **GET /api/devices/{id}/sunspec/nameplate** - Max ratings
- **GET /api/devices/{id}/sunspec/settings** - Settings (121)
- **GET /api/devices/{id}/sunspec/extended-settings** - Extended settings (122)
- **GET /api/devices/{id}/sunspec/voltage-power** - Volt/Watt settings (123)
- **GET /api/devices/{id}/sunspec/status** - Immediate status (124)
- **GET /api/devices/{id}/sunspec/controls** - Basic controls (127)
- **GET /api/devices/{id}/sunspec/models/{modelId}** - Generic model reader
- **GET /api/devices/{id}/sunspec/models/{modelId}/raw** - Raw register dump

Add curl examples for each endpoint with realistic responses.

#### AGENTS.md Updates
Add SunSpec package structure to project overview:
```
at.or.reder.frodo.modbus/
├── sunspec/
│   ├── SunSpecService.java              # Core SunSpec service
│   ├── SunSpecModelRegistry.java        # Model definitions (all supported models)
│   ├── SunSpecModelDataDecoder.java     # Model data decoder
│   ├── SunSpecRegisterDecoder.java      # Data type decoder (float32, acc32, etc.)
│   ├── SunSpecConstants.java            # Model IDs, base addresses
│   ├── SunSpecDataType.java             # Enum: all SunSpec data types
│   ├── SunSpecDiscoveryResult.java      # Discovery result cache model
│   ├── SunSpecModelBlock.java           # Record: model location (addr, length)
│   ├── SunSpecModelData.java            # Record: decoded model data
│   ├── SunSpecModelDefinition.java      # Record: model metadata
│   └── SunSpecFieldDefinition.java      # Record: field metadata
```

### Deliverables
- [ ] Complete README with Modbus + SunSpec examples
- [ ] Testing documentation (include SunSpec endpoint tests)
- [ ] Updated AGENTS.md (add SunSpec package structure)
- [ ] Example configurations
- [ ] API usage examples (curl) for Device + SunSpec endpoints
- [ ] docs/SUNSPEC_MODELS.md with model registry reference
- [ ] SunSpec data types and decoding documentation
- [ ] Raw Modbus → SunSpec model data examples

---

## Final Configuration Example

```properties
# Modbus Connection
frodo.modbus.enabled=true
frodo.modbus.connection.timeout-seconds=30
frodo.modbus.connection.reconnect-initial-delay-seconds=1
frodo.modbus.connection.reconnect-max-delay-seconds=60
frodo.modbus.connection.idle-timeout-seconds=300

# Request Queue
frodo.modbus.request.queue-capacity=50
frodo.modbus.request.timeout-seconds=10
frodo.modbus.request.max-retries=3
frodo.modbus.request.retry-delay-seconds=2

# Device Info Collection
frodo.modbus.device-info.refresh-interval=5m
frodo.modbus.device-info.cache-ttl-minutes=60
frodo.modbus.device-info.retry-attempts=3
frodo.modbus.device-info.retry-delay-seconds=5

# Device Seeding (Stage 1 compatibility)
frodo.modbus.device.host=192.168.1.100
frodo.modbus.device.port=502
frodo.modbus.device.unit-id=1
frodo.modbus.device.name=PV Inverter 1
frodo.modbus.device.enabled=true
frodo.modbus.device.seed-from-config=true

# Health
frodo.modbus.health.max-age-minutes=15

# Database
quarkus.datasource.active=true
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050/frodo.fdb
quarkus.datasource.username=sysdba
quarkus.datasource.password=masterkey

# Flyway
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

---

## Execution Timeline

| Stage | Duration | Dependencies | Deliverable |
|-------|----------|--------------|-------------|
| Stage 1 | 2-3 days | None | Connection pool + queue |
| Stage 2 | 2-3 days | Stage 1 | Device Identification impl |
| Stage 3 | 2-3 days | Stage 1 | Database schema + entities |
| Stage 4 | 2 days | Stage 2, 3 | Scheduled collection |
| Stage 5 | 2 days | Stage 3, 4 | REST API |
| Stage 6 | 1-2 days | Stage 1, 4, 5 | Health + Metrics (+ SunSpec) |
| Stage 7 | 1-2 days | All | Documentation (+ SunSpec) |

**Total Estimated Time:** 12-18 days

---

## Success Criteria Checklist

- [ ] **Stage 1**: Persistent connection with queue, auto-reconnect working
- [ ] **Stage 2**: Read Device Identification (FC 0x2B/0x0E) successfully
- [ ] **Stage 3**: Database schema deployed, device stored in FirebirdSQL
- [ ] **Stage 4**: Scheduled collection running every 5 minutes
- [ ] **Stage 5**: REST API: GET /api/devices/{id}/info returns device identification
- [ ] **Stage 6**: Health check shows Modbus status, metrics in Prometheus format
- [ ] **Stage 7**: Documentation complete, manual testing successful
- [ ] **Final**: Can configure device via database, collect info automatically, query via REST

---

## Testing Strategy

### Unit Tests
- Pure JUnit 5 + Mockito
- Test static utility methods (frame building/parsing)
- Test business logic in isolation

### Integration Tests
- @QuarkusTest with mock Modbus server (Vert.x-based)
- REST endpoint tests with RestAssured
- Database tests with H2 or Testcontainers

### Mock Modbus Server
- Custom Vert.x TCP server for testing
- Responds to FC 0x03 (Read Holding Registers)
- Responds to FC 0x2B (Read Device Identification)
- Simulates failures and timeouts

### Manual Testing
- Real Modbus device or simulator (pymodbus)
- Verify all endpoints in Swagger UI
- Test connection recovery scenarios

---

## Notes

1. **Protocol**: Using plain Modbus TCP (no encryption) on port 502
2. **Multi-Device**: Database schema supports multiple devices; connection pool handles single device initially
3. **Performance**: With 1 request/minute target, queue size (50) provides adequate buffering
4. **Firebird 5.0+**: Schema uses sequences and standard SQL syntax compatible with Firebird 5.x
5. **SunSpec Implementation**: Full SunSpec Modbus protocol support was implemented and merged with Stage 5 (PR #10, commit a097e61):
   - 11 SunSpec-related source files (SunSpecService, ModelRegistry, DataTypes, etc.)
   - 10 test files with >90% coverage
   - SunSpecResource with 11 REST endpoints
   - Support for models: Common (1), Inverter (101-103, 111-113), Nameplate (120), Settings (121-123), Status (124-126), Controls (127-132)
   - In-memory discovery cache with invalidation
   - Full register decoding for all SunSpec data types (float32, uint16, int16, uint32, acc32, acc64, string, enum, bitfield)
   - Auto-detection of inverter model variants (Float vs Int+SF)
   - **Stages 6 & 7 have been updated to include SunSpec-specific health checks, metrics, and documentation tasks**
