# Device Discovery

Frodo supports automatic discovery of devices connected to a Fronius Modbus TCP gateway. A single Fronius Gen24 inverter acts as a gateway, providing access to sub-devices (smart meters, Ohmpilot heaters) via different Modbus Unit IDs.

## Supported Device Types

| Type | Detection Method | Default Unit IDs |
|------|-----------------|------------------|
| Inverter | SunSpec models 101-103, 111-113 | 1 |
| Smart Meter | SunSpec models 201-204, 211-214 | 200-203 |
| Storage | SunSpec model 124 | varies |
| Ohmpilot | Solar API (GetPowerFlowRealtimeData) | N/A (no Modbus Unit ID) |

## Discovery Strategies

### 1. SunSpec Discovery (Primary)

Scans each configured Unit ID for the SunSpec "SunS" signature at register 40000. If found, reads the complete model chain to determine device type and capabilities.

- Reads the Common model (1) for manufacturer, model, serial number, firmware version
- Determines device type from the models present (inverter, meter, storage)
- Returns all SunSpec model IDs available on the device

### 2. Modbus FC 0x2B Fallback

If a Unit ID responds but has no SunSpec support, Frodo attempts a Read Device Identification (FC 0x2B) request. This returns vendor name, product code, and firmware version. Device type is inferred from product strings (e.g., "ohmpilot", "meter").

### 3. Solar API Discovery (Ohmpilot)

When the Solar API is enabled (`frodo.solar-api.enabled=true`), Frodo queries the Fronius Solar API `GetPowerFlowRealtimeData` endpoint to discover Ohmpilot devices. These devices are identified by ComponentId and include power consumption, temperature, and operating state data.

Ohmpilot devices discovered via Solar API have `unitId=-1` since the ComponentId does not map to a Modbus Unit ID.

## REST API

### Discover Devices on a Gateway

```bash
# Scan with default unit ID ranges (configured in application.properties)
curl -s -X POST http://localhost:8080/api/devices/discover \
  -H "Content-Type: application/json" \
  -d '{"host": "192.168.1.160", "port": 502}' | jq .

# Scan with custom unit ID ranges
curl -s -X POST http://localhost:8080/api/devices/discover \
  -H "Content-Type: application/json" \
  -d '{"host": "192.168.1.160", "port": 502, "unitIdRanges": "1,200-205"}' | jq .

# Scan and auto-save discovered devices to the database
curl -s -X POST http://localhost:8080/api/devices/discover \
  -H "Content-Type: application/json" \
  -d '{"host": "192.168.1.160", "port": 502, "autoSave": true}' | jq .
```

**Response:**
```json
{
  "host": "192.168.1.160",
  "port": 502,
  "devicesFound": 2,
  "devices": [
    {
      "host": "192.168.1.160",
      "port": 502,
      "unitId": 1,
      "deviceType": "INVERTER",
      "manufacturer": "Fronius",
      "model": "Symo 10.0-3-M",
      "serialNumber": "12345678",
      "version": "1.2.7",
      "modelIds": [1, 113, 120, 121, 122, 123, 124, 160],
      "source": "sunspec",
      "suggestedName": "Fronius Symo 10.0-3-M",
      "hasSunSpec": true
    },
    {
      "host": "192.168.1.160",
      "port": 502,
      "unitId": 200,
      "deviceType": "SMART_METER",
      "manufacturer": "Fronius",
      "model": "Smart Meter TS 65A-3",
      "serialNumber": "87654321",
      "modelIds": [1, 213],
      "source": "sunspec",
      "suggestedName": "Fronius Smart Meter TS 65A-3",
      "hasSunSpec": true
    }
  ],
  "savedDeviceIds": []
}
```

### Discover Sub-Devices for an Existing Device

```bash
# Discover sub-devices using the parent device's host:port
curl -s -X POST http://localhost:8080/api/devices/1/discover-sub-devices | jq .
```

Discovered devices are automatically saved as children of the parent device.

### List Sub-Devices

```bash
curl -s http://localhost:8080/api/devices/1/sub-devices | jq .
```

### Filter Devices by Type or Parent

```bash
# List only smart meters
curl -s "http://localhost:8080/api/devices?deviceType=SMART_METER" | jq .

# List devices belonging to a parent
curl -s "http://localhost:8080/api/devices?parentId=1" | jq .
```

## Configuration

All discovery configuration properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `frodo.discovery.enabled` | `true` | Enable/disable discovery feature |
| `frodo.discovery.unit-id-ranges` | `1,200-203` | Default unit IDs to scan |
| `frodo.discovery.timeout-seconds` | `3` | Per-unit-ID scan timeout |
| `frodo.discovery.max-concurrent-scans` | `5` | Max concurrent unit ID scans |
| `frodo.solar-api.enabled` | `false` | Enable Solar API for Ohmpilot discovery |
| `frodo.solar-api.host` | `localhost` | Solar API host |
| `frodo.solar-api.port` | `80` | Solar API port |
| `frodo.solar-api.timeout-seconds` | `10` | Solar API request timeout |

### Unit ID Ranges Format

The `unit-id-ranges` property supports comma-separated values and dash-separated ranges:
- `1` -- single unit ID
- `1,200-203` -- unit ID 1 plus range 200 through 203
- `1-3,10,200-202` -- ranges and individual values combined

Valid unit IDs: 1-247 (per Modbus specification).

## Device Hierarchy

Discovered devices form a parent-child hierarchy:

```
Inverter (Unit 1)            -- parent device
├── Smart Meter 1 (Unit 200)  -- auto-discovered child
├── Smart Meter 2 (Unit 201)  -- auto-discovered child
└── Ohmpilot (Solar API)      -- auto-discovered child (no Modbus Unit ID)
```

- Parent devices cannot be deleted while they have sub-devices (returns HTTP 409)
- Auto-discovered devices are flagged with `autoDiscovered=true`
- The `parentDeviceId` field links children to their parent

## Monitoring

### Health Checks

The Modbus health check (`/q/health`) includes device hierarchy information:
- Per-device connection status and last read age
- Device counts by type (inverter, smart_meter, ohmpilot, etc.)
- Enabled vs auto-discovered device counts

### Metrics

Discovery operations are tracked via Prometheus metrics at `/q/metrics`:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `frodo.devices.total` | gauge | `type` | Device count per type |
| `frodo.discovery.scans.total` | counter | `result` | Discovery scans (success/failure) |
| `frodo.discovery.devices.found.total` | counter | -- | Total devices found |

## Source Files

| File | Description |
|------|-------------|
| `DeviceDiscoveryService.java` | Discovery orchestration (SunSpec + FC 0x2B + Solar API) |
| `DeviceDiscoveryResource.java` | REST API endpoints for discovery |
| `DiscoveredDevice.java` | Discovery result record |
| `DeviceDiscoveryRequest.java` | Request DTO (host, port, unitIdRanges, autoSave) |
| `DeviceDiscoveryResponse.java` | Response DTO with discovered devices |
| `SolarApiClient.java` | Fronius Solar API HTTP client |
| `ModbusDeviceEntity.java` | JPA entity with deviceType, parentDevice, autoDiscovered |
| `ModbusHealthCheck.java` | Health check with device hierarchy reporting |
| `ModbusMetrics.java` | Discovery metrics (scans, devices found) |
