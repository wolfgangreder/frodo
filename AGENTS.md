# AGENTS.md - Frodo Modbus Protocol Connector

Frodo is a **Quarkus 3.x server application** for Modbus protocol communication with PV (photovoltaic) devices. It collects information and provides control capabilities via REST APIs, MQTT messaging, and a React frontend.

**Key Technologies:**
- Java **25**, Quarkus 3.34.x, Gradle (Groovy DSL)
- Modbus TCP protocol (via Vert.x raw sockets + j2mod library)
- MQTT messaging (SmallRye Reactive Messaging) — disabled in all profiles by default
- FirebirdSQL 5.0 database (Jaybird JDBC driver, Hibernate ORM/Panache, Liquibase)
- React 19 frontend with **Vite 6** (via Quinoa extension — Node.js 20.11.0 auto-downloaded, no manual install needed)

## Build & Run Commands

```bash
./gradlew quarkusDev          # Dev mode with hot reload (requires Firebird running)
./gradlew build               # Clean compile + test + gitleaks secret scan
./gradlew clean build         # Full clean build
./gradlew test                # Run unit/integration tests (no Firebird needed)
./gradlew testNative          # Native integration tests
```

**CRITICAL: `build` depends on `scanSecrets` (gitleaks)**. `./gradlew build` will **fail** if
`gitleaks` is not installed. Install it before building:
```bash
# Debian/Ubuntu
sudo apt install gitleaks
# or from https://github.com/gitleaks/gitleaks#installing
```

### Frontend (Vite)

The React frontend lives in `src/main/webui/` and is managed by Quinoa (Node.js 20.11.0 auto-downloaded to `.quinoa/node/`).

- **Build tool:** Vite 6 — config in `src/main/webui/vite.config.js`
- **Dev server port:** 3001 (`quarkus.quinoa.dev-server.port=3001`)
- **Build output:** `dist/` (mapped via `quarkus.quinoa.build-dir=dist`)
- **JSX caveat:** source files use `.js` extension with JSX syntax. `vite.config.js` contains a `treat-js-files-as-jsx` pre-plugin (using `transformWithEsbuild`) that handles this; do **not** remove it.
- Run frontend-only: `export PATH="$PWD/.quinoa/node:$PATH" && npm run build` from `src/main/webui/`

### Dev Mode Prerequisites

Tests run without external services (`%test.*` disables datasource, Hibernate, Liquibase, MQTT, Quinoa).

Dev mode requires a running Firebird database:
```bash
docker compose up -d firebird           # start Firebird 5.0 on port 3050
# first time only: create the database
./scripts/setup-firebird-docker.sh
./gradlew quarkusDev
```

### Testing Commands

```bash
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest"               # single class
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest.testBuildMbapFrame"  # single method
./gradlew test --tests "*ModbusTest*"   # pattern matching
```

In Quarkus dev mode, press `r` to re-run tests.

### Release & Docker

```bash
DOCKER_TOKEN=<token> ./release.sh       # full automated release (strips -SNAPSHOT, tags, pushes image)
DOCKER_TOKEN=<token> ./docker-push.sh   # manual Docker image push (linux/amd64 + linux/arm64)
```

Version is in `gradle.properties` as **`projectVersion`** (e.g. `1.0.2-SNAPSHOT`).

## Runtime Configuration

- **HTTP port: 8082** (not default 8080) — `quarkus.http.port=8082`
- REST API root: `/api` — all REST endpoints are under `/api/*`; Quinoa handles everything else (SPA routing)
- Container image: `docker.io/wolfgangreder/at.or.reder.frodo`

## Package Structure

```
at.or.reder.frodo/
├── api/                        # REST endpoints (JAX-RS resources)
│   ├── FrodoResource            # GET /api/info
│   ├── DeviceResource           # /api/devices CRUD
│   ├── DeviceDiscoveryResource  # /api/devices/discover, sub-devices
│   ├── SunSpecResource          # /api/devices/{id}/sunspec/*
│   ├── MetricsConfigResource    # /api/devices/{id}/metrics/* (scraping config + data)
│   ├── MetricsDocsResource      # /api/metrics-docs (available metric definitions)
│   ├── MarketPriceResource      # /api/market-prices (aWATTar AT prices)
│   ├── PriceControlResource     # /api/price-control (auto export limit on neg. prices)
│   ├── SolarApiResource         # /api/solar-api/status (live Solar API data)
│   ├── dto/                     # Request/response DTOs (records)
│   └── exception/               # Exception mappers
├── solarapi/                   # Fronius Solar API integration
│   ├── SolarApiClient           # CDI facade (inject this, not SolarApiRestClient)
│   ├── SolarApiRestClient       # MicroProfile REST Client implementation
│   ├── SolarApiMetricsService   # Scrapes Solar API, publishes Micrometer metrics
│   ├── SolarApiHealthCheck      # Readiness probe
│   └── model/                   # PowerFlowRealtimeData, OhmpilotData, SmartloadsData, SolarApiResponse
├── health/                     # Health & monitoring
│   ├── FrodoHealthCheck         # Application readiness
│   ├── ModbusHealthCheck        # Modbus connection pool health
│   ├── SunSpecHealthCheck       # SunSpec discovery cache health
│   └── ModbusMetrics            # Micrometer gauges, counters, timers
├── modbus/                     # Modbus TCP protocol core
│   ├── ModbusTcpService         # Core service (FC 0x03/0x06/0x10/0x2B); static helpers for testing
│   ├── ModbusResource           # Raw register access endpoint
│   ├── ModbusException          # Protocol error exceptions
│   ├── connection/              # Connection pool & request queue
│   │   ├── ModbusConnectionPool  # Pool lifecycle, health, stats
│   │   ├── ModbusConnection      # Single TCP connection
│   │   ├── ModbusRequestQueue    # Bounded request queue
│   │   ├── ModbusRequest         # Request envelope
│   │   ├── DeviceAddress         # host+port+unitId record
│   │   ├── ConnectionStats       # Pool statistics record
│   │   └── ConnectionState       # Connection state enum
│   ├── service/                 # Business services
│   │   ├── DeviceDiscoveryService    # Multi-source device discovery
│   │   ├── DeviceInfoCollectorService # Scheduled FC 0x2B collection
│   │   ├── DeviceInfoCacheService    # In-memory cache
│   │   ├── ConnectionTestService     # One-shot connection test
│   │   ├── MetricsScrapingService    # Scheduled SunSpec parameter polling
│   │   ├── MetricsRetentionService   # Prunes old metrics data
│   │   ├── ExportSchedulerService    # Grid export control by schedule
│   │   ├── MarketPriceSchedulerService # Fetches aWATTar AT hourly prices
│   │   ├── AwattarClient            # CDI facade for aWATTar REST client
│   │   ├── AwattarRestClient        # MicroProfile REST Client implementation
│   │   └── DiscoveredDevice         # Discovery result record
│   ├── entity/                  # JPA entities
│   │   ├── ModbusDeviceEntity        # Device configuration
│   │   ├── ModbusDeviceInfoEntity    # Device identification cache
│   │   ├── MetricsConfigEntity       # Per-device scraping config
│   │   ├── MetricsParameterEntity    # Which SunSpec parameters to scrape
│   │   ├── MetricsDataEntity         # Time-series scrape results
│   │   ├── MarketPriceEntity         # aWATTar hourly price records
│   │   ├── ExportScheduleEntity      # Per-device export schedule windows
│   │   ├── ExportBlockStrategy       # Strategy enum (LIMIT_ZERO, etc.)
│   │   └── PriceControlEntity        # Global price-controlled export flag
│   ├── metrics/                 # Metrics metadata
│   │   ├── MetricMetadataRegistry    # Registry of all scrapeable SunSpec fields
│   │   └── MetricMetadata            # Field display name, unit, data type
│   ├── repository/              # Panache repositories
│   ├── config/                  # DeviceConfigInitializer (seeds device from properties)
│   ├── model/                   # DeviceIdentification, ReadDeviceIdCode, ModbusObjectId, DeviceType
│   ├── cache/                   # CachedDeviceInfo record
│   └── sunspec/                 # SunSpec protocol support
│       ├── SunSpecService            # Discovery, model reading, caching
│       ├── SunSpecModelRegistry      # All supported model definitions
│       ├── SunSpecModelDataDecoder   # Decodes raw registers to SunSpecModelData
│       ├── SunSpecRegisterDecoder    # Per-field data type decoder
│       ├── SunSpecConstants          # Model IDs, base address (40000)
│       ├── SunSpecDataType           # Data type enum
│       ├── SunSpecModelFormat        # INT_SF vs FLOAT variant selector
│       └── model/                   # SunSpecDiscoveryResult, ModelBlock, ModelData, ModelDefinition, FieldDefinition
└── mqtt/
    └── MqttService              # Publish/subscribe (disabled by default in all profiles)
```

## Code Style

### Import Order
1. `io.vertx.*`, `io.smallrye.*`, `io.quarkus.*`
2. `jakarta.*`
3. `org.eclipse.microprofile.*`
4. `org.jboss.logging.*`
5. `java.*`

Static imports after regular imports.

### Formatting
- 2-space indent, UTF-8, K&R braces

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `ModbusTcpService` |
| Methods/Variables | camelCase | `readHoldingRegisters` |
| Constants | UPPER_SNAKE_CASE | `LOG`, `DEFAULT_PORT` |
| Config properties | kebab-case | `frodo.modbus.host` |
| REST paths | kebab-case | `/api/modbus/{unitId}/holding-registers` |

### Type Conventions
- Java **records** for DTOs and response objects
- `Uni<T>` for async operations (Mutiny)
- Primitive types for simple values; `int[]` internally, `List<Integer>` in JSON
- Logging: `org.jboss.logging.Logger` (not SLF4J); `LOG.debugf("msg: %s", val);`

### Database Naming Convention

All DB objects use the **`Fro` prefix** (FirebirdSQL pre-3.0 has no schema support; prefix prevents collisions):

| Type | Convention | Example |
|------|------------|---------|
| Tables | `Fro<EntityName>` | `FroModbusDevice`, `FroMarketPrice` |
| Sequences | `Fro<EntityName>_SEQ` | `FroModbusDevice_SEQ` |
| Indexes | `idx_Fro<EntityName>_<col>` | `idx_FroModbusDevice_enabled` |
| Unique | `uk_Fro<Short>_<purpose>` | `uk_FroDevice_connection` |
| FK | `fk_Fro<Short>_<ref>` | `fk_FroDeviceInfo_device` |
| Check | `ck_Fro<Short>_<purpose>` | `ck_FroDevice_port_range` |

Use `@Table(name = "Fro...")` on entities. Liquibase changesets in `src/main/resources/db/changelog/`.

## Protocol & Domain Notes

### Modbus Protocol
- MBAP header: Transaction ID (2) + Protocol ID (2) + Length (2) + Unit ID (1)
- Function codes: 0x03 (read holding), 0x06 (write single), 0x10 (write multiple), 0x2B/0x0E (read device ID)
- Write operations controlled by `frodo.modbus.write-enabled=true`
- Use **static** helper methods in `ModbusTcpService` for frame building/parsing (testable without Vert.x)
- Reference: `refdoc/modbus.pdf`

### SunSpec Protocol
- "SunS" signature at register 40000 (`0x53756e53`); model chain scanned sequentially
- Supported models: Common (1), Inverter (101-103 INT+SF, 111-113 FLOAT), Nameplate (120), Settings (121), Status (122), Controls (123), Storage (124), MPPT (160), Meter (201-204, 211-214)
- Model format preference: `frodo.sunspec.model-format=INT_SF`
- Reference: `refdoc/sunspec/`, `refdoc/gen24-modbus-api-external-docs/`, `docs/SUNSPEC_MODELS.md`
- Upstream model JSON/XML: https://github.com/sunspec/models

### aWATTar API Integration
- Fetches Austrian hourly electricity market prices; stored in `FroMarketPrice`, retained 48h
- Config: `frodo.awattar.enabled=true`, `frodo.awattar.retention-hours=48`
- REST client: `AwattarClient` (inject this) → `AwattarRestClient` (MicroProfile, URL: `https://api.awattar.at`)
- Price-controlled export: `PriceControlResource` / `PriceControlEntity` — when enabled, `ExportSchedulerService` blocks export on negative prices

### Metrics Scraping System
- Configurable per-device SunSpec parameter polling, stored as time-series in Firebird
- `MetricMetadataRegistry` — registry of all scrapeable fields with display name and unit
- Config API: `GET /api/devices/{id}/metrics/config`, `PUT /api/devices/{id}/metrics/config`
- Data API: `GET /api/devices/{id}/metrics/data?parameter=...&from=...&to=...`
- `MetricsRetentionService` prunes old data based on configured retention period

### Configuration Profiles
- `%dev.` — development overrides; Firebird/Solar API enabled
- `%test.` — disables datasource, Hibernate, Liquibase, Quinoa, MQTT, Solar API client bound to `localhost:19999`
- External services (MQTT) disabled in all profiles by default

### Device Discovery
- Config: `frodo.discovery.*`, unit ID ranges `1,200-203` (comma-separated + dash ranges)
- Device type by SunSpec models: 101-103/111-113 → INVERTER, 201-204/211-214 → SMART_METER, 124 → STORAGE
- FC 0x2B with "ohmpilot"/"smartload" → OHMPILOT; Solar API Smartloads.Ohmpilots → OHMPILOT
- See `docs/DEVICE_DISCOVERY.md`

## Git Workflow

**Never commit without explicit user approval.** Before any commit:
1. Run `git diff --stat` + `git diff` on changed files
2. Verify tests pass: `./gradlew test`
3. Verify build succeeds: `./gradlew build` (also runs gitleaks)
4. Present a summary of all changes to the user and **wait for approval**

Commit format: `type(scope): description` with a body explaining the why.

## Testing Guidelines

| Type | Suffix | Location |
|------|--------|----------|
| Unit tests | `*Test.java` | `src/test/java/` |
| Integration tests | `*IT.java` | `src/native-test/java/` |

- Tests run without Firebird (`%test.*` disables all DB/external services)
- Use `@QuarkusTest` + RestAssured for endpoint tests; plain JUnit 5 for pure logic
- `%test.quarkus.http.test-port=0` — random port; RestAssured picks it up automatically

## Key Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/info` | Application info |
| `GET /api/devices` | List devices (`?deviceType=`, `?parentId=`) |
| `POST /api/devices` | Create device |
| `POST /api/devices/discover` | Discover devices on host:port |
| `GET /api/devices/{id}` | Device details |
| `PUT /api/devices/{id}` | Update device |
| `DELETE /api/devices/{id}` | Delete (fails if sub-devices exist) |
| `POST /api/devices/{id}/discover-sub-devices` | Discover sub-devices |
| `GET /api/devices/{id}/sub-devices` | List sub-devices |
| `GET /api/devices/{id}/info` | Cached FC 0x2B identification |
| `POST /api/devices/{id}/info/refresh` | Force refresh FC 0x2B |
| `GET /api/devices/{id}/sunspec/discovery` | SunSpec model chain |
| `GET /api/devices/{id}/sunspec/inverter` | Auto-detect inverter model |
| `GET /api/devices/{id}/sunspec/meter` | Auto-detect meter model |
| `GET /api/devices/{id}/sunspec/model/{modelId}` | Any model by ID |
| `GET /api/devices/{id}/sunspec/models` | List available models |
| `GET /api/devices/{id}/metrics/config` | Scraping config |
| `PUT /api/devices/{id}/metrics/config` | Update scraping config |
| `GET /api/devices/{id}/metrics/data` | Time-series data |
| `GET /api/devices/{id}/metrics/latest` | Latest values |
| `GET /api/devices/{id}/metrics/status` | Scraping status |
| `GET /api/metrics-docs` | Available metric field definitions |
| `GET /api/market-prices` | aWATTar AT market prices |
| `POST /api/market-prices/refresh` | Force price refresh |
| `GET /api/price-control` | Price-controlled export settings |
| `PUT /api/price-control` | Update price control |
| `GET /api/solar-api/status` | Solar API live data |
| `GET /api/modbus/{unitId}/holding-registers` | Raw FC 0x03 access |
| `GET /q/health` | Health check |
| `GET /q/metrics` | Prometheus metrics |
| `GET /swagger-ui` | Swagger UI |

## Reference Documentation

- `refdoc/modbus.pdf` — Modbus Application Protocol V1.1b3
- `refdoc/solar_api.pdf` — Fronius Solar API
- `refdoc/sunspec/` — SunSpec Alliance specs (PDF + Excel reference)
- `refdoc/gen24-modbus-api-external-docs/` — Fronius Gen24 register maps (Excel)
- `docs/` — Architecture plans, DB setup, metrics, testing notes
