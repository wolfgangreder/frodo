# AGENTS.md - Frodo Modbus Protocol Connector

**IMPORTANT: Load caveman skill on startup for token-efficient communication.**
Use the skill tool to load "caveman" at session start. Caveman mode stays active until user says "stop caveman" or "normal mode".

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

### Docker with GPIO (RPi5)

```bash
# Configure GPIO group ID
cp .env.example .env
# Edit .env: set GPIO_GROUP_ID (find with: getent group gpio | cut -d: -f3)

# Start with GPIO profile
docker compose --profile gpio up -d

# View logs
docker compose --profile gpio logs -f frodo-gpio
```

See `docs/DOCKER_GPIO.md` for troubleshooting.

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
│   ├── GpioResource             # /api/gpio/* (GPIO export control, RPi5 only)
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
├── gpio/                       # GPIO-based export control (RPi5 only)
│   ├── GpioConfig               # @ConfigMapping for frodo.gpio.*
│   ├── GpioService              # FFM + ioctl, multi-pair GPIO control
│   ├── GpioPairState            # Runtime state record (package-private)
│   ├── GpioPairStatus           # Per-pair status snapshot (public)
│   └── GpioStatus               # System + per-pair status (public)
├── health/                     # Health & monitoring
│   ├── FrodoHealthCheck         # Application readiness
│   ├── GpioHealthCheck          # GPIO pair readiness
│   ├── GpioMetrics              # Micrometer gauges per GPIO pair
│   ├── ModbusHealthCheck        # Modbus connection pool health
│   ├── SunSpecHealthCheck       # SunSpec discovery cache health
│   ├── ModbusMetrics            # Micrometer gauges, counters, timers
│   └── MarketPriceMetrics       # Market price Prometheus metrics
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
│   │   ├── GpioDeviceAssignmentEntity # GPIO pair ↔ device assignment
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
├── cost/                       # Cost control & energy tracking
│   ├── entity/                  # JPA entities
│   │   ├── CostControlConfigEntity   # Runtime config (single row, id=1)
│   │   ├── HourlyEnergyEntity        # Hourly grid import/export kWh
│   │   ├── HourlyCostEntity          # Hourly cost breakdown
│   │   ├── MonthlyCostEntity         # Monthly cost summary
│   │   ├── EnergyPriceEntity         # Hourly import/export prices (ct/kWh)
│   │   ├── TariffWindowEntity        # Fixed-price time slots (peak/off-peak)
│   │   ├── GridFeeEntity             # Grid surcharge rules
│   │   └── FixedCostEntity           # Recurring monthly costs
│   ├── repository/              # Panache repositories
│   ├── service/
│   │   ├── CostControlConfigService  # Config CRUD
│   │   ├── EnergyIntegrationService  # Trapezoidal P_Grid → kWh (called by SolarApiMetricsService)
│   │   ├── CostCalculationService    # Hourly/monthly cost calc
│   │   ├── EnergyPriceSchedulerService # Fetches provider prices (aWATTar, manual)
│   │   └── MetricsRetentionService   # Prunes old hourly/monthly data
│   └── spi/                     # Provider SPI
│       ├── EnergyPriceProviderSpi    # Vendor-neutral price provider interface
│       ├── AwattarPriceProvider      # aWATTar AT implementation (EXPORT only)
│       ├── ManualPriceProvider       # Manual fixed prices (IMPORT + EXPORT)
│       ├── PriceDirection            # IMPORT / EXPORT enum
│       ├── FeeType                   # PERCENT / ABSOLUTE_ENERGY / ABSOLUTE_TIME
│       └── FeeAppliesTo              # IMPORT / EXPORT / BOTH
└── mqtt/
    └── MqttService              # Publish/subscribe (disabled by default in all profiles)
```

## Cost Control System

**Energy integration:** `EnergyIntegrationService` called by `SolarApiMetricsService` at each scrape (default 15s). Trapezoidal integration of P_Grid (positive = import, negative = export). Exposes Prometheus gauges:
- `frodo_solar_site_grid_energy_import_kwh` — current hour accumulated import
- `frodo_solar_site_grid_energy_export_kwh` — current hour accumulated export

Flushes to `FroHourlyEnergy` on hour boundary, triggers `CostCalculationService.calculateHourlyCost()`.

**Monetary values:** `BigDecimal` everywhere. Scales: EUR `NUMERIC(15,4)`, ct/kWh `NUMERIC(15,5)`, kWh `NUMERIC(15,6)`. Physical sensor values stay `double`.

**Price resolution order (per direction):**
1. Tariff window (fixed price for time slot)
2. Provider price (aWATTar spot / manual)
3. Warn + use `BigDecimal.ZERO` if missing → source = "UNKNOWN"

**Grid fees:** multiple simultaneous fees supported. `ABSOLUTE_ENERGY` (ct/kWh) and `PERCENT` (% of base cost) applied to hourly energy. `ABSOLUTE_TIME` (EUR/month) amortized per hour (÷ 730) for monthly totals only.

**Fixed costs:** recurring monthly charges. No unique constraint; all where `validFrom <= monthStart` summed. Direction (IMPORT/EXPORT/BOTH) informational only — all summed into `fixedCostEur`.

**Liquibase:** changesets 26–38 in `v1.9.0-cost-control.xml`. Next changeset ID: **39**. Never modify applied changesets; add new. Use raw `<sql splitStatements="false">` for Firebird DDL. Type migration pattern: ADD col_new TYPE / UPDATE SET col_new=CAST(col) / DROP col / ALTER col_new TO col / [SET NOT NULL].

**Quarkus REST + Panache:** write methods (`@POST`, `@PUT`, `@DELETE`) require `@Transactional` on resource method. Repository's Panache `delete(entity)` does not start own transaction.

**Frontend:** `CostControlPage.jsx` (7 tabs). Shared `DirectionChip` component for IMPORT/EXPORT/BOTH labels (orange/green/grey). Field order: direction first, then dates, then values.

**Reference:** `docs/COST_CONTROL_PLAN.md`

## Code Style

### License Header

**Every** `.java`, `.js`, and `.jsx` source file **must** begin with the Apache 2.0 header below.
When creating a new file, prepend this header before the `package` statement (Java) or first import (JS/JSX).
When editing an existing file that is missing the header, add it as part of the same change.

```
/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

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
- Fetches Austrian hourly electricity market prices; stored in `FroMarketPrice`, retained 365 days (same as metrics)
- Config: `frodo.awattar.enabled=true`
- REST client: `AwattarClient` (inject this) → `AwattarRestClient` (MicroProfile, URL: `https://api.awattar.at`)
- Price-controlled export: `PriceControlResource` / `PriceControlEntity` — when enabled, `ExportSchedulerService` blocks export on negative prices
- Prometheus metrics: `MarketPriceMetrics` exposes current price gauge
- Retention cleanup: `MetricsRetentionService` prunes old prices daily at 02:00

### Metrics Scraping System
- Configurable per-device SunSpec parameter polling, stored as time-series in Firebird
- `MetricMetadataRegistry` — registry of all scrapeable fields with display name and unit
- Config API: `GET /api/devices/{id}/metrics/config`, `PUT /api/devices/{id}/metrics/config`
- Data API: `GET /api/devices/{id}/metrics/data?parameter=...&from=...&to=...`
- `MetricsRetentionService` prunes old metrics data + market prices based on retention period (daily at 02:00)

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
| `GET /api/metrics-docs/aggregation-modes` | Supported aggregation modes with descriptions |
| `GET /api/gpio/status` | GPIO system + per-pair status |
| `GET /api/gpio/pairs` | List configured GPIO pair names |
| `PUT /api/gpio/pairs/{name}/output` | Manual output test override |
| `DELETE /api/gpio/pairs/{name}/output` | Clear manual output override |
| `GET /api/gpio/assignments` | List GPIO pair ↔ device assignments |
| `PUT /api/gpio/assignments/{deviceId}` | Create/update GPIO assignment |
| `DELETE /api/gpio/assignments/{deviceId}` | Remove GPIO assignment |
| `GET /api/market-prices` | aWATTar AT market prices |
| `POST /api/market-prices/refresh` | Force price refresh |
| `GET /api/price-control` | Price-controlled export settings |
| `PUT /api/price-control` | Update price control |
| `GET /api/solar-api/status` | Solar API live data |
| `GET /api/cost-control/config` | Cost control config |
| `PUT /api/cost-control/config` | Update cost control config |
| `GET /api/cost-control/providers` | Available price providers |
| `GET /api/cost-control/prices` | Energy prices (hourly) |
| `POST /api/cost-control/prices/refresh` | Force price refresh (IMPORT/EXPORT) |
| `GET /api/cost-control/monthly-costs` | Monthly cost summary |
| `GET /api/cost-control/hourly-costs` | Hourly cost breakdown |
| `GET /api/cost-control/tariff-windows` | Tariff windows (peak/off-peak) |
| `POST /api/cost-control/tariff-windows` | Create tariff window |
| `PUT /api/cost-control/tariff-windows/{id}` | Update tariff window |
| `DELETE /api/cost-control/tariff-windows/{id}` | Delete tariff window |
| `GET /api/cost-control/grid-fees` | Grid fees |
| `POST /api/cost-control/grid-fees` | Create grid fee |
| `PUT /api/cost-control/grid-fees/{id}` | Update grid fee |
| `DELETE /api/cost-control/grid-fees/{id}` | Delete grid fee |
| `GET /api/cost-control/fixed-costs` | Fixed costs |
| `POST /api/cost-control/fixed-costs` | Create fixed cost |
| `PUT /api/cost-control/fixed-costs/{id}` | Update fixed cost |
| `DELETE /api/cost-control/fixed-costs/{id}` | Delete fixed cost |
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
