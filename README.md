# Frodo

Frodo is a Quarkus 3.34.x server application for Modbus TCP communication with PV (photovoltaic) inverters and related devices. It supports device management, SunSpec model discovery, scheduled data collection, grid export control, energy cost tracking, and monitoring — all accessible via REST APIs, MQTT messaging, and a React frontend.

## Features

| Feature | Technology |
|---------|-----------|
| REST API | Quarkus REST + Jackson |
| API Documentation | SmallRye OpenAPI / Swagger UI |
| Modbus TCP | java.net.Socket with connection pooling & fair-lock request queue |
| SunSpec Protocol | Model chain discovery, typed register decoding (Float & Int+SF) |
| Device Management | CRUD API, database-backed config, scheduled info collection |
| Device Discovery | Multi-source discovery (SunSpec + Solar API), device hierarchy |
| Metrics Scraping | Configurable per-device SunSpec parameter polling, time-series storage |
| Solar API | Fronius Solar API client for Ohmpilot and power flow data |
| Market Prices | aWATTar AT hourly electricity market price integration |
| Cost Control | Energy cost calculation, tariff windows, grid fees, fixed costs |
| GPIO Export Control | RPi5 GPIO relay control for grid export limiting |
| Monitoring | Micrometer Prometheus metrics (JVM, Modbus, SunSpec, Discovery, GPIO) |
| Health Checks | MicroProfile Health (Modbus, SunSpec, Solar API, cost control) |
| Messaging | MQTT via SmallRye Reactive Messaging |
| Database | FirebirdSQL 5.0 via Jaybird JDBC + Hibernate ORM/Panache + Liquibase |
| Containerization | Docker (multi-arch: linux/amd64 + linux/arm64) |
| Frontend UI | React 19 + PatternFly 6 (via Quinoa / Vite 6) |

## Prerequisites

- Java 25+
- Docker (for container builds and Firebird database)
- Node.js 20+ (auto-downloaded by Quinoa to `.quinoa/node/` — no manual install needed)
- FirebirdSQL 5.0+ (Docker recommended for development)

## Quick Start

### 1. Start Firebird

```bash
docker compose up -d firebird

# First time only — create the database
./scripts/setup-firebird-docker.sh
```

### 2. Start in Dev Mode

```bash
./gradlew quarkusDev
```

The application starts at **<http://localhost:8082/frodo/>**.

Quarkus dev UI is at <http://localhost:8082/frodo/q/dev/>.

## URL Structure

All resources share the `/frodo` base path:

| Resource | Base URL |
|----------|----------|
| Frontend SPA | `http://host:8082/frodo/` |
| REST API | `http://host:8082/frodo/api/` |
| Health checks | `http://host:8082/frodo/q/health` |
| Prometheus metrics | `http://host:8082/frodo/q/metrics` |
| Swagger UI | `http://host:8082/frodo/swagger-ui` |

**Redirects** (built in, no proxy config needed):

| Request | Result |
|---------|--------|
| `http://host:8082/frodo` | 301 → `/frodo/` |
| `http://host:8082/` | 301 → `/frodo/` (production) |

### Reverse Proxy (nginx)

```nginx
location /frodo/ {
    proxy_pass http://localhost:8082/frodo/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

## Database Setup

Frodo uses FirebirdSQL as the production database. The database must be created with **UTF-8 character set** and **32K page size**.

### Quick Start with Docker (Recommended)

```bash
# Option 1: Automated setup script
./scripts/setup-firebird-docker.sh

# Option 2: Manual Docker Compose
docker compose up -d firebird
docker compose exec firebird isql -user sysdba -password masterkey << EOF
CREATE DATABASE '/firebird/data/frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
EOF
```

**Detailed Instructions**: See **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)**.

**Database Migrations**: Liquibase migrations run automatically on server startup.

**Test Mode**: Unit tests do not require a database (datasource and Hibernate are disabled in the test profile).

## Running in Docker

### Standard Deployment (No GPIO)

Uncomment the `frodo` service in `docker-compose.yml`, then:

```bash
docker compose up -d
```

### Raspberry Pi 5 with GPIO Support

For GPIO-based export control on RPi5:

1. **Configure GPIO group ID:**
   ```bash
   cp .env.example .env
   # Edit .env — set GPIO_GROUP_ID (find with: getent group gpio | cut -d: -f3)
   ```

2. **Configure GPIO pairs** in `docker-compose.yml` (uncomment and set pin numbers)

3. **Start with GPIO profile:**
   ```bash
   docker compose --profile gpio up -d
   ```

See [docs/DOCKER_GPIO.md](docs/DOCKER_GPIO.md) for detailed instructions and troubleshooting.

## REST API Endpoints

All endpoints are under the `/frodo/api` prefix.

### Infrastructure

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/info` | Application info |
| GET | `/frodo/q/health` | Health checks |
| GET | `/frodo/q/metrics` | Prometheus metrics |
| GET | `/frodo/swagger-ui` | Interactive API documentation |
| GET | `/frodo/q/openapi` | OpenAPI 3.0 specification |

### Device Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/devices` | List devices (`?deviceType=`, `?parentId=`) |
| POST | `/frodo/api/devices` | Create a device |
| GET | `/frodo/api/devices/{id}` | Get device details |
| PUT | `/frodo/api/devices/{id}` | Update a device |
| DELETE | `/frodo/api/devices/{id}` | Delete a device (409 if sub-devices exist) |
| GET | `/frodo/api/devices/{id}/info` | Cached device identification (FC 0x2B) |
| POST | `/frodo/api/devices/{id}/info/refresh` | Force refresh device identification |

### Device Discovery

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/frodo/api/devices/discover` | Discover devices on host:port |
| POST | `/frodo/api/devices/{id}/discover-sub-devices` | Discover sub-devices |
| GET | `/frodo/api/devices/{id}/sub-devices` | List sub-devices |

### SunSpec Protocol

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/devices/{id}/sunspec/discovery` | Discover SunSpec model chain |
| GET | `/frodo/api/devices/{id}/sunspec/inverter` | Auto-detect inverter model (101-103 / 111-113) |
| GET | `/frodo/api/devices/{id}/sunspec/meter` | Auto-detect meter model (201-204 / 211-214) |
| GET | `/frodo/api/devices/{id}/sunspec/model/{modelId}` | Read any model by ID |
| GET | `/frodo/api/devices/{id}/sunspec/models` | List available models |

### Metrics Scraping

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/devices/{id}/metrics/config` | Scraping configuration |
| PUT | `/frodo/api/devices/{id}/metrics/config` | Update scraping configuration |
| GET | `/frodo/api/devices/{id}/metrics/data` | Time-series data (`?parameter=`, `?from=`, `?to=`) |
| GET | `/frodo/api/devices/{id}/metrics/latest` | Latest scraped values |
| GET | `/frodo/api/devices/{id}/metrics/status` | Scraping status |
| GET | `/frodo/api/metrics-docs` | Available metric field definitions |

### Market Prices

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/market-prices` | aWATTar AT hourly market prices |
| POST | `/frodo/api/market-prices/refresh` | Force price refresh |
| GET | `/frodo/api/price-control` | Price-controlled export settings |
| PUT | `/frodo/api/price-control` | Update price control settings |

### Cost Control

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/cost-control/config` | Cost control configuration |
| PUT | `/frodo/api/cost-control/config` | Update configuration |
| GET | `/frodo/api/cost-control/hourly-costs` | Hourly cost breakdown |
| GET | `/frodo/api/cost-control/monthly-costs` | Monthly cost summary |
| GET | `/frodo/api/cost-control/tariff-windows` | Tariff windows |
| POST | `/frodo/api/cost-control/tariff-windows` | Create tariff window |
| PUT | `/frodo/api/cost-control/tariff-windows/{id}` | Update tariff window |
| DELETE | `/frodo/api/cost-control/tariff-windows/{id}` | Delete tariff window |
| GET | `/frodo/api/cost-control/grid-fees` | Grid fees |
| POST | `/frodo/api/cost-control/grid-fees` | Create grid fee |
| PUT | `/frodo/api/cost-control/grid-fees/{id}` | Update grid fee |
| DELETE | `/frodo/api/cost-control/grid-fees/{id}` | Delete grid fee |
| GET | `/frodo/api/cost-control/fixed-costs` | Fixed monthly costs |
| POST | `/frodo/api/cost-control/fixed-costs` | Create fixed cost |
| PUT | `/frodo/api/cost-control/fixed-costs/{id}` | Update fixed cost |
| DELETE | `/frodo/api/cost-control/fixed-costs/{id}` | Delete fixed cost |

### GPIO Export Control

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/gpio/status` | GPIO system + per-pair status |
| GET | `/frodo/api/gpio/pairs` | List configured GPIO pair names |
| PUT | `/frodo/api/gpio/pairs/{name}/output` | Manual output override |
| DELETE | `/frodo/api/gpio/pairs/{name}/output` | Clear manual output override |
| GET | `/frodo/api/gpio/assignments` | GPIO pair ↔ device assignments |
| PUT | `/frodo/api/gpio/assignments/{deviceId}` | Create/update GPIO assignment |
| DELETE | `/frodo/api/gpio/assignments/{deviceId}` | Remove GPIO assignment |

### Solar API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/solar-api/status` | Live Solar API power flow data |

### Modbus Raw Access

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/frodo/api/modbus/{unitId}/holding-registers` | Read holding registers (FC 0x03) |

### API Usage Examples

```bash
BASE=http://localhost:8082/frodo

# List all devices
curl -s $BASE/api/devices | jq .

# Create a device
curl -s -X POST $BASE/api/devices \
  -H "Content-Type: application/json" \
  -d '{
    "name": "PV Inverter",
    "host": "192.168.1.100",
    "port": 502,
    "unitId": 1,
    "enabled": true
  }' | jq .

# Discover SunSpec models on a device
curl -s $BASE/api/devices/1/sunspec/discovery | jq .

# Read inverter data (auto-detects Float vs Int+SF)
curl -s $BASE/api/devices/1/sunspec/inverter | jq .

# Discover devices on a Modbus TCP gateway
curl -s -X POST $BASE/api/devices/discover \
  -H "Content-Type: application/json" \
  -d '{"host": "192.168.1.160", "port": 502, "autoSave": true}' | jq .

# Check health
curl -s $BASE/q/health | jq .

# Prometheus metrics
curl -s $BASE/q/metrics

# Current market prices
curl -s $BASE/api/market-prices | jq .

# Latest scraped metrics for a device
curl -s $BASE/api/devices/1/metrics/latest | jq .
```

## Building

```bash
# Build (requires gitleaks installed)
./gradlew build

# Run tests only (no Firebird needed)
./gradlew test
```

**Note**: `./gradlew build` runs a gitleaks secret scan. Install gitleaks before building:
```bash
sudo apt install gitleaks   # Debian/Ubuntu
```

## Configuration Reference

All application-specific properties in `src/main/resources/application.properties`:

### HTTP / Routing

| Property | Value | Description |
|----------|-------|-------------|
| `quarkus.http.port` | `8082` | HTTP listen port |
| `quarkus.http.root-path` | `/frodo` | Base path for all resources |
| `quarkus.rest.path` | `/api` | JAX-RS base path (relative to root-path) |

### Modbus Connection

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.enabled` | `true` | Enable Modbus TCP communication |
| `frodo.modbus.write-enabled` | `true` | Enable write operations (FC 0x06, 0x10) |
| `frodo.modbus.connection.timeout-seconds` | `30` | TCP connection timeout |
| `frodo.modbus.connection.reconnect-initial-delay-seconds` | `1` | Initial reconnect delay |
| `frodo.modbus.connection.reconnect-max-delay-seconds` | `60` | Maximum reconnect delay |
| `frodo.modbus.request.timeout-seconds` | `10` | Request timeout |
| `frodo.modbus.request.max-retries` | `3` | Retry attempts on failure |

### Device Info Collection

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.device-info.refresh-interval` | `5m` | Scheduled collection interval |
| `frodo.modbus.device-info.cache-ttl-minutes` | `60` | Cache TTL for device identification |

### Device Seeding

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.device.host` | `localhost` | Seed device host |
| `frodo.modbus.device.port` | `502` | Seed device port |
| `frodo.modbus.device.unit-id` | `1` | Seed device Modbus unit ID |
| `frodo.modbus.device.name` | `Default PV Device` | Seed device name |
| `frodo.modbus.device.seed-from-config` | `true` | Auto-create device on startup |

### Device Discovery

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.discovery.enabled` | `true` | Enable device discovery |
| `frodo.discovery.unit-id-ranges` | `1,200-203` | Unit ID ranges to scan |
| `frodo.discovery.timeout-seconds` | `3` | Timeout per device probe |
| `frodo.discovery.max-concurrent-scans` | `5` | Maximum concurrent scans |

### Solar API

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.solar-api.enabled` | `true` | Enable Fronius Solar API integration |
| `frodo.solar-api.host` | `192.168.1.160` | Solar API host (inverter IP) |
| `frodo.solar-api.port` | `80` | Solar API port |
| `frodo.solar-api.timeout-seconds` | `5` | HTTP request timeout |
| `frodo.solar-api.scrape-interval-seconds` | `5` | Scrape interval |

### Market Prices

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.awattar.enabled` | `true` | Enable aWATTar AT price fetching |

### Health & Monitoring

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.modbus.health.max-age-minutes` | `15` | Max age of last successful read before DOWN |
| `frodo.sunspec.health.discovery-required` | `false` | Require SunSpec discovery for health UP |
| `frodo.sunspec.health.max-cache-age-hours` | `24` | Max SunSpec cache age before health WARN |

### GPIO Export Control

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.gpio.enabled` | `false` | Enable GPIO-based export control |
| `frodo.gpio.chip-device` | `/dev/gpiochip0` | Linux GPIO character device |
| `frodo.gpio.force-platform` | `false` | Force RPi platform detection (use in Docker) |
| `frodo.gpio.pairs.<name>.output-pin` | — | Output relay GPIO pin number |
| `frodo.gpio.pairs.<name>.input-pin` | — | Input switch GPIO pin number |

## Building a Docker Image

```bash
./gradlew build -Dquarkus.container-image.build=true

# Push multi-arch image (linux/amd64 + linux/arm64)
DOCKER_TOKEN=<token> ./docker-push.sh
```

## Release

```bash
DOCKER_TOKEN=<token> ./release.sh
```

Strips `-SNAPSHOT`, tags the release, builds and pushes the Docker image. Version is set in `gradle.properties` as `projectVersion`.

## Project Structure

```
src/main/java/at/or/reder/frodo/
├── api/                         -- REST endpoints (/frodo/api/*)
│   ├── FrodoResource            GET /api/info
│   ├── DeviceResource           /api/devices CRUD
│   ├── DeviceDiscoveryResource  /api/devices/discover, sub-devices
│   ├── SunSpecResource          /api/devices/{id}/sunspec/*
│   ├── MetricsConfigResource    /api/devices/{id}/metrics/*
│   ├── MetricsDocsResource      /api/metrics-docs
│   ├── MarketPriceResource      /api/market-prices
│   ├── GpioResource             /api/gpio/*
│   ├── PriceControlResource     /api/price-control
│   ├── SolarApiResource         /api/solar-api/status
│   ├── RootRedirectHandler      301 redirects: / and /frodo → /frodo/
│   ├── dto/                     Request/response DTOs (records)
│   └── exception/               Exception mappers
├── solarapi/                    -- Fronius Solar API integration
├── gpio/                        -- GPIO export control (RPi5)
├── health/                      -- Health checks & Micrometer metrics
├── modbus/                      -- Modbus TCP protocol core
│   ├── ModbusTcpService         FC 0x03/0x06/0x10/0x2B
│   ├── connection/              Connection pool & request queue
│   ├── service/                 Discovery, info collection, metrics scraping
│   ├── entity/                  JPA entities (devices, metrics, schedules)
│   ├── metrics/                 MetricMetadataRegistry
│   ├── repository/              Panache repositories
│   └── sunspec/                 SunSpec protocol (discovery, decoding, registry)
├── cost/                        -- Energy cost tracking
│   ├── entity/                  JPA entities (costs, prices, tariffs, fees)
│   ├── repository/              Panache repositories
│   ├── service/                 Cost calc, energy integration, price fetching
│   └── spi/                     EnergyPriceProviderSpi (aWATTar, manual)
└── mqtt/                        -- MQTT messaging (disabled by default)
src/main/webui/                  -- React 19 + PatternFly 6 frontend (Vite 6)
```

## Storybook

```bash
# Dev server on port 6006
npm run storybook          # from src/main/webui/

# Static build
npm run build-storybook
```

## Further Documentation

- **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)** — Firebird database setup
- **[docs/DEVICE_DISCOVERY.md](docs/DEVICE_DISCOVERY.md)** — Multi-source device discovery
- **[docs/SUNSPEC_MODELS.md](docs/SUNSPEC_MODELS.md)** — SunSpec model registry reference
- **[docs/DOCKER_GPIO.md](docs/DOCKER_GPIO.md)** — GPIO Docker setup (RPi5)
- **[docs/COST_CONTROL_PLAN.md](docs/COST_CONTROL_PLAN.md)** — Cost control system design

## Protocol References

- **Modbus Application Protocol V1.1b3**: `refdoc/modbus.pdf`
- **Fronius Gen24 Register Maps**: `refdoc/gen24-modbus-api-external-docs/`
- **SunSpec Alliance Specs**: `refdoc/sunspec/`
- **Fronius Solar API**: `refdoc/solar_api.pdf`
