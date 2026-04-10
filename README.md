# Frodo

Frodo is a Quarkus 3.x server application for Modbus TCP communication with PV (photovoltaic) inverters. It supports device management, SunSpec model discovery, scheduled data collection, and monitoring -- all accessible via REST APIs, MQTT messaging, and a React frontend.

## Features

| Feature | Technology |
|---------|-----------|
| REST API | Quarkus REST + Jackson |
| API Documentation | SmallRye OpenAPI / Swagger UI |
| Modbus TCP | java.net.Socket with connection pooling & fair-lock request queue |
| SunSpec Protocol | Model chain discovery, typed register decoding (Float & Int+SF) |
| Device Management | CRUD API, database-backed config, scheduled info collection |
| Device Discovery | Multi-source discovery (SunSpec + Solar API), device hierarchy |
| Solar API | Fronius Solar API client for Ohmpilot and power flow data |
| Monitoring | Micrometer Prometheus metrics (JVM, Modbus, SunSpec, Discovery) |
| Health Checks | MicroProfile Health (Modbus, SunSpec, Solar API, device hierarchy) |
| Messaging | MQTT via SmallRye Reactive Messaging |
| Database | FirebirdSQL via Jaybird 6 JDBC + Liquibase migrations |
| Containerization | Docker (Quarkus Docker extension) |
| Frontend UI | React 18 (via Quinoa extension) |

## Prerequisites

- Java 21+
- Docker (for container builds and Firebird database)
- Node.js 20+ (for the React UI; installed automatically by Quinoa)
- FirebirdSQL 5.0+ (for production; Docker recommended for development)

## Database Setup

Frodo uses FirebirdSQL as the production database. The database must be created with **UTF-8 character set** and **32K page size**.

### Quick Start with Docker (Recommended)

```bash
# Option 1: Automated setup script
./scripts/setup-firebird-docker.sh

# Option 2: Manual Docker Compose
docker-compose up -d firebird
docker-compose exec firebird isql -user sysdba -password masterkey << EOF
CREATE DATABASE '/firebird/data/frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
EOF
```

### Manual Setup (Native Installation)

```bash
# Create database with isql
isql -user sysdba -password masterkey -input src/main/resources/db/create-database.sql
```

**Detailed Instructions**: See **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)** for:
- Docker setup with docker-compose
- Native Firebird installation
- Connection configuration
- Backup/restore procedures
- Troubleshooting

**Database Migrations**: Liquibase migrations run automatically on server startup in both development and production modes. Ensure the database is created before starting the application.

**Test Mode**: Unit tests do not require a database (Hibernate and datasource are disabled in test profile).

## Running in Development Mode

### With Database (Recommended)

1. **Start Firebird database**:
   ```bash
   ./scripts/setup-firebird-docker.sh
   # Or use docker-compose
   docker-compose up -d firebird
   ```

2. **Start Quarkus in dev mode**:
   ```bash
   ./gradlew quarkusDev
   ```

   Database migrations will run automatically on startup.

### Without Database (Legacy)

To run without database (disables scheduled collection and device management):

```bash
./gradlew quarkusDev -Dquarkus.datasource.active=false
```

The application starts on <http://localhost:8080>.

## REST API Endpoints

### Infrastructure

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/info` | Application info |
| GET | `/q/health` | Health checks (Modbus, SunSpec, application) |
| GET | `/q/metrics` | Prometheus metrics |
| GET | `/swagger-ui` | Interactive API documentation |
| GET | `/q/openapi` | OpenAPI 3.0 specification |

### Device Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/devices` | List devices (supports `?deviceType=` and `?parentId=` filters) |
| POST | `/api/devices` | Create a new device |
| GET | `/api/devices/{id}` | Get device details |
| PUT | `/api/devices/{id}` | Update a device |
| DELETE | `/api/devices/{id}` | Delete a device (fails 409 if sub-devices exist) |
| GET | `/api/devices/{id}/info` | Get cached device identification (FC 0x2B) |
| POST | `/api/devices/{id}/info/refresh` | Force refresh device identification |

### Device Discovery

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/devices/discover` | Discover devices on a host:port (Modbus + Solar API) |
| POST | `/api/devices/{id}/discover-sub-devices` | Discover sub-devices for an existing parent device |
| GET | `/api/devices/{id}/sub-devices` | List sub-devices of a parent device |

### Modbus Raw Access

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/modbus/{unitId}/holding-registers?start=0&count=10` | Read holding registers (FC 0x03) |

### SunSpec Protocol

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/devices/{id}/sunspec/discovery` | Discover SunSpec model chain |
| GET | `/api/devices/{id}/sunspec/common` | Common model (1) -- device identification |
| GET | `/api/devices/{id}/sunspec/inverter` | Auto-detect inverter model (101-103 / 111-113) |
| GET | `/api/devices/{id}/sunspec/meter` | Auto-detect meter model (201-204 / 211-214) |
| GET | `/api/devices/{id}/sunspec/nameplate` | Nameplate ratings (120) |
| GET | `/api/devices/{id}/sunspec/settings` | Basic settings (121) |
| GET | `/api/devices/{id}/sunspec/status` | Extended measurements & status (122) |
| GET | `/api/devices/{id}/sunspec/controls` | Immediate controls (123) |
| GET | `/api/devices/{id}/sunspec/storage` | Basic storage controls (124) |
| GET | `/api/devices/{id}/sunspec/mppt` | Multiple MPPT extension (160) |
| GET | `/api/devices/{id}/sunspec/model/{modelId}` | Read any model by ID |
| GET | `/api/devices/{id}/sunspec/models` | List all available models |

### API Usage Examples

```bash
# List all devices
curl -s http://localhost:8080/api/devices | jq .

# Create a device
curl -s -X POST http://localhost:8080/api/devices \
  -H "Content-Type: application/json" \
  -d '{
    "name": "PV Inverter",
    "host": "192.168.1.100",
    "port": 502,
    "unitId": 1,
    "enabled": true
  }' | jq .

# Get device identification (Modbus FC 0x2B)
curl -s http://localhost:8080/api/devices/1/info | jq .

# Discover SunSpec models on a device
curl -s http://localhost:8080/api/devices/1/sunspec/discovery | jq .

# Read inverter data (auto-detects Float vs Int+SF)
curl -s http://localhost:8080/api/devices/1/sunspec/inverter | jq .

# Read nameplate ratings
curl -s http://localhost:8080/api/devices/1/sunspec/nameplate | jq .

# Read a specific SunSpec model by ID
curl -s http://localhost:8080/api/devices/1/sunspec/model/120 | jq .

# List all available models on the device
curl -s http://localhost:8080/api/devices/1/sunspec/models | jq .

# Discover devices on a Modbus TCP gateway
curl -s -X POST http://localhost:8080/api/devices/discover \
  -H "Content-Type: application/json" \
  -d '{
    "host": "192.168.1.160",
    "port": 502,
    "autoSave": true
  }' | jq .

# Discover sub-devices for an existing parent device
curl -s -X POST http://localhost:8080/api/devices/1/discover-sub-devices | jq .

# List sub-devices of a parent device
curl -s http://localhost:8080/api/devices/1/sub-devices | jq .

# Filter devices by type
curl -s "http://localhost:8080/api/devices?deviceType=SMART_METER" | jq .

# Filter devices by parent
curl -s "http://localhost:8080/api/devices?parentId=1" | jq .

# Read meter data (auto-detects Float vs Int+SF)
curl -s http://localhost:8080/api/devices/2/sunspec/meter | jq .

# Read raw Modbus holding registers
curl -s "http://localhost:8080/api/modbus/1/holding-registers?start=40000&count=10" | jq .

# Check health status
curl -s http://localhost:8080/q/health | jq .

# Prometheus metrics
curl -s http://localhost:8080/q/metrics
```

## Building

```bash
./gradlew build
```

## Building a Docker Image

```bash
./gradlew build -Dquarkus.container-image.build=true
```

## Configuration Reference

All `frodo.*` configuration properties in `src/main/resources/application.properties`:

### Modbus Connection

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.host` | `localhost` | Modbus TCP host (legacy, use device DB) |
| `frodo.modbus.port` | `502` | Modbus TCP port (legacy, use device DB) |
| `frodo.modbus.enabled` | `false` | Enable Modbus TCP communication |
| `frodo.modbus.write-enabled` | `false` | Enable write operations (FC 0x06, 0x10) |

### Connection Pool

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.connection.timeout-seconds` | `30` | TCP connection timeout |
| `frodo.modbus.connection.reconnect-initial-delay-seconds` | `1` | Initial reconnect delay |
| `frodo.modbus.connection.reconnect-max-delay-seconds` | `60` | Maximum reconnect delay (exponential backoff) |
| `frodo.modbus.connection.idle-timeout-seconds` | `300` | Close idle connections after this period |

### Request Queue

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.request.queue-capacity` | `50` | Maximum queued requests |
| `frodo.modbus.request.timeout-seconds` | `10` | Request timeout |
| `frodo.modbus.request.max-retries` | `3` | Retry attempts on failure |
| `frodo.modbus.request.retry-delay-seconds` | `2` | Delay between retries |

### Device Info Collection

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.device-info.refresh-interval` | `5m` | Scheduled collection interval |
| `frodo.modbus.device-info.cache-ttl-minutes` | `60` | Cache TTL for device identification |
| `frodo.modbus.device-info.retry-attempts` | `3` | Retries per collection cycle |
| `frodo.modbus.device-info.retry-delay-seconds` | `5` | Delay between retries |

### Device Seeding

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.device.host` | `localhost` | Seed device host |
| `frodo.modbus.device.port` | `502` | Seed device port |
| `frodo.modbus.device.unit-id` | `1` | Seed device Modbus unit ID |
| `frodo.modbus.device.name` | `Default PV Device` | Seed device name |
| `frodo.modbus.device.seed-from-config` | `true` | Auto-create device from config on startup |

### Health & Monitoring

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.health.max-age-minutes` | `15` | Max age of last successful read before DOWN |
| `frodo.sunspec.health.discovery-required` | `false` | Require SunSpec discovery for health UP |
| `frodo.sunspec.health.max-cache-age-hours` | `24` | Max SunSpec cache age before WARN |

### Device Discovery

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.discovery.enabled` | `true` | Enable device discovery functionality |
| `frodo.discovery.unit-id-ranges` | `1,200-203` | Unit ID ranges to scan (comma-separated values/ranges) |
| `frodo.discovery.timeout-seconds` | `5` | Timeout per device probe during discovery |
| `frodo.discovery.max-concurrent-scans` | `1` | Maximum concurrent discovery scans |

### Solar API

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.solar-api.enabled` | `false` | Enable Fronius Solar API integration |
| `frodo.solar-api.host` | `localhost` | Solar API host (usually the inverter IP) |
| `frodo.solar-api.port` | `80` | Solar API port |
| `frodo.solar-api.timeout-seconds` | `10` | HTTP request timeout |

### Example Production Configuration

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

# Device Seeding
frodo.modbus.device.host=192.168.1.100
frodo.modbus.device.port=502
frodo.modbus.device.unit-id=1
frodo.modbus.device.name=PV Inverter 1
frodo.modbus.device.seed-from-config=true

# Health
frodo.modbus.health.max-age-minutes=15
frodo.sunspec.health.max-cache-age-hours=24

# Device Discovery
frodo.discovery.enabled=true
frodo.discovery.unit-id-ranges=1,200-203

# Solar API (for Ohmpilot discovery)
frodo.solar-api.enabled=true
frodo.solar-api.host=192.168.1.100

# Database
quarkus.datasource.active=true
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050/frodo.fdb
quarkus.datasource.username=sysdba
quarkus.datasource.password=masterkey

# Liquibase
quarkus.liquibase.migrate-at-start=true
```

**Note**: Use double slash `//` for absolute paths in Docker (`//firebird/data/`), single slash for native installations.

See **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)** for database creation and configuration details.

## Project Structure

```
src/main/java/at/or/reder/frodo/
├── api/                         -- REST endpoints
│   ├── FrodoResource.java           Application info (/api/info)
│   ├── DeviceResource.java          Device management CRUD (/api/devices)
│   ├── DeviceDiscoveryResource.java Discovery endpoints (discover, sub-devices)
│   ├── SunSpecResource.java         SunSpec protocol endpoints
│   ├── dto/                         Request/response DTOs (records)
│   └── exception/                   REST exception mappers
├── health/                      -- Health & monitoring
│   ├── FrodoHealthCheck.java        Application readiness check
│   ├── ModbusHealthCheck.java       Modbus + device hierarchy health check
│   ├── SunSpecHealthCheck.java      SunSpec discovery cache health check
│   ├── SolarApiHealthCheck.java     Solar API availability health check
│   └── ModbusMetrics.java           Micrometer gauges, counters, timers
├── modbus/                      -- Modbus TCP protocol
│   ├── ModbusTcpService.java        Core Modbus TCP service (FC 0x03, 0x06, 0x10, 0x2B)
│   ├── ModbusResource.java          Raw register access endpoint
│   ├── ModbusException.java         Modbus protocol exceptions
│   ├── connection/                  Connection pool & request queue
│   ├── service/                     Device info collection, caching & discovery
│   │   ├── DeviceDiscoveryService.java  Multi-source device discovery
│   │   ├── DiscoveredDevice.java        Discovery result record
│   │   ├── DeviceInfoCollectorService.java  Scheduled collection
│   │   └── DeviceInfoCacheService.java      In-memory cache
│   ├── entity/                      JPA entities (device, device info)
│   ├── repository/                  Panache repositories
│   ├── config/                      Device config initializer
│   ├── model/                       Domain models (DeviceIdentification, DeviceType)
│   ├── cache/                       In-memory cache models
│   └── sunspec/                     SunSpec protocol support
│       ├── SunSpecService.java          Discovery, model reading, caching
│       ├── SunSpecModelRegistry.java    Model definitions (inverter, meter, storage, ...)
│       ├── SunSpecModelDataDecoder.java Model data decoder
│       ├── SunSpecRegisterDecoder.java  Data type decoder (float32, acc32, ...)
│       ├── SunSpecConstants.java        Model IDs, base addresses
│       ├── SunSpecDataType.java         Enum: all SunSpec data types
│       ├── SunSpecDiscoveryResult.java  Discovery result record
│       ├── SunSpecModelBlock.java       Model location record
│       ├── SunSpecModelData.java        Decoded model data record
│       ├── SunSpecModelDefinition.java  Model metadata record
│       └── SunSpecFieldDefinition.java  Field metadata record
├── solarapi/                    -- Fronius Solar API integration
│   ├── SolarApiClient.java          HTTP client for Solar API
│   ├── SolarApiClientProducer.java  CDI producer for JAX-RS Client
│   └── model/                       Solar API data models
│       ├── SolarApiResponse.java        Generic response wrapper
│       ├── PowerFlowRealtimeData.java   Power flow response
│       ├── SmartloadsData.java          Ohmpilot/smartload data
│       └── OhmpilotData.java           Ohmpilot device data
└── mqtt/                        -- MQTT messaging
    └── MqttService.java             Publish/subscribe service
```

## Further Documentation

- **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)** -- Firebird database setup guide
- **[docs/DEVICE_DISCOVERY.md](docs/DEVICE_DISCOVERY.md)** -- Device discovery guide (multi-device, Solar API)
- **[docs/SUNSPEC_MODELS.md](docs/SUNSPEC_MODELS.md)** -- SunSpec model registry reference
- **[docs/TESTING.md](docs/TESTING.md)** -- Testing guide
- **[docs/SECURITY.md](docs/SECURITY.md)** -- Security guidelines and secret scanning
- **[docs/MODBUS_INFRASTRUCTURE_PLAN.md](docs/MODBUS_INFRASTRUCTURE_PLAN.md)** -- Implementation plan

## Protocol References

- **Modbus Application Protocol V1.1b3**: `refdoc/modbus.pdf`
- **Fronius Gen24 Register Maps**: `refdoc/gen24-modbus-api-external-docs/`
  - Float models: `Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx`
  - Int+SF models: `Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_ROW.xlsx`
  - Storage Float: `Gen24_Primo_Symo_Storage_Register_Map_Float_ROW.xlsx`
  - Storage Int+SF: `Gen24_Primo_Symo_Storage_Register_Map_Int&SF_ROW.xlsx`
