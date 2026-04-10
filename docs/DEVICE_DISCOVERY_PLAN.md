# Device Discovery Implementation Plan

## Overview

This document outlines the implementation plan for adding multi-device discovery support to Frodo, enabling detection and management of Smart Meter and Ohmpilot devices connected via Modbus TCP on the same connection as the inverter.

**Date**: 2026-04-10  
**Status**: Planning

---

## Background

### Current Limitations
- Frodo only scans the configured Unit ID (typically 1 for inverters)
- No support for discovering Smart Meters (Unit IDs 200-203) or Ohmpilot devices
- No SunSpec model definitions for meter devices
- Single device per TCP connection

### Architecture Insight (from Documentation)
Based on `refdoc/modbus.pdf` and `refdoc/solar_api.pdf`:

1. **Fronius Gateway Architecture**:
   - Inverter acts as Modbus TCP gateway
   - Multiple RTU devices connected via inverter
   - Each device has unique Unit ID (slave address)

2. **Default Unit ID Mapping**:
   - **Inverter**: Unit ID 1 (default)
   - **Smart Meter 1**: Unit ID 200 (default, configurable)
   - **Smart Meter 2**: Unit ID 201
   - **Smart Meter 3**: Unit ID 202
   - **Smart Meter 4**: Unit ID 203
   - **Ohmpilot**: Unknown (to be discovered)

3. **SunSpec per Device**:
   - Each device implements its own SunSpec model chain
   - SunSpec signature ("SunS" 0x53756e53) at register 40000 for each Unit ID
   - Device type determined by which models are present

4. **Smart Meter Models** (SunSpec):
   - Model 201: Single Phase (A-N) Meter (Int+SF)
   - Model 202: Split Single Phase (A-B-N) Meter (Int+SF)
   - Model 203: Three Phase (WYE) Meter (Int+SF)
   - Model 204: Three Phase (Delta) Meter (Int+SF)
   - Model 211: Single Phase (A-N) Meter (Float)
   - Model 212: Split Single Phase (A-B-N) Meter (Float)
   - Model 213: Three Phase (WYE) Meter (Float)
   - Model 214: Three Phase (Delta) Meter (Float)

5. **Ohmpilot Support** (Smartload DeviceClass):
   - **Solar API DeviceClass**: "Ohmpilot" (also referenced as "smartload")
   - **No standard SunSpec model** - confirmed via official repository search
   - **Solar API Endpoints**:
     - `GET /solar_api/v1/GetPowerFlowRealtimeData.fcgi` - **Unified power flow data** (includes all devices)
     - `GET /solar_api/v1/GetOhmPilotRealtimeData.cgi` - Ohmpilot-specific real-time data
     - `GET /solar_api/v1/GetActiveDeviceInfo.cgi?DeviceClass=Ohmpilot` - Device discovery
   - **PowerFlowRealtimeData Structure** (Version 10+):
     - **Smartloads.Ohmpilots**: Object keyed by ComponentId (device identifier)
       - `P_AC_Total` - Current power consumption [W]
       - `State` - Operating state ("normal", "min-temperature", "legionella-protection", "fault", "warning", "boost")
       - `Temperature` - Storage/tank temperature [°C]
     - **Smartloads.OhmpilotEcos** (Version 13+, GEN24/Tauro/Verto only):
       - `P_AC_Total` - Current power consumption [W]
       - `State_HR1`, `State_HR2` - Heating rod states
       - `Temperature_1`, `Temperature_2` - Heating rod temperatures [°C]
   - **Detection Strategy**:
     - **Primary**: Query `GetPowerFlowRealtimeData.fcgi` to discover Ohmpilot ComponentIds
     - **Fallback**: Unit ID scanning (configurable ranges) via Modbus FC 0x2B
     - Map ComponentId to Modbus Unit ID (mapping unknown, requires testing)
   - **Data Sources**:
     - **Solar API**: Real-time power, state, temperature (no Modbus knowledge needed)
     - **Modbus**: Direct register access (requires proprietary register map)
   - **Implementation Notes**:
     - Solar API provides easier access to Ohmpilot data without Modbus complexity
     - Modbus registers unknown - rely on Solar API for Ohmpilot metrics
     - ComponentId in Solar API may differ from Modbus Unit ID

### Phase 1: Data Model Extensions ✓
**Goal**: Extend database schema to support device types and hierarchies

#### 1.1 Create DeviceType Enum
**File**: `src/main/java/at/or/reder/frodo/modbus/model/DeviceType.java`

```java
public enum DeviceType {
  INVERTER("Inverter", "Solar inverter"),
  STORAGE("Storage", "Battery storage system"),
  SMART_METER("Smart Meter", "Energy meter"),
  OHMPILOT("Ohmpilot", "Smartload excess energy controller"),
  UNKNOWN("Unknown", "Unknown device type");
  
  // Display name, description, helper methods
}
```

#### 1.2 Extend ModbusDeviceEntity
**File**: `src/main/java/at/or/reder/frodo/modbus/entity/ModbusDeviceEntity.java`

Add fields:
- `deviceType` (String/Enum, nullable, auto-detected from SunSpec models)
- `parentDeviceId` (Long, nullable, FK to parent device)
- `autoDiscovered` (boolean, default false, indicates auto-discovered vs manual)

**Changes**:
```java
@Column(name = "device_type", length = 50)
@Enumerated(EnumType.STRING)
public DeviceType deviceType;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_device_id")
public ModbusDeviceEntity parentDevice;

@Column(name = "auto_discovered", nullable = false)
public boolean autoDiscovered = false;
```

#### 1.3 Database Migration
**File**: `src/main/resources/db/changelog/v1.1.0-add-device-discovery.xml`

Liquibase changeset:
```xml
<changeSet id="1.1.0-add-device-discovery" author="frodo">
  <addColumn tableName="FroModbusDevice">
    <column name="device_type" type="VARCHAR(50)"/>
    <column name="parent_device_id" type="BIGINT"/>
    <column name="auto_discovered" type="BOOLEAN" defaultValueBoolean="false"/>
  </addColumn>
  
  <addForeignKeyConstraint
    baseTableName="FroModbusDevice"
    baseColumnNames="parent_device_id"
    constraintName="fk_FroDevice_parent"
    referencedTableName="FroModbusDevice"
    referencedColumnNames="id"
    onDelete="CASCADE"/>
</changeSet>
```

Include in master changelog.

#### 1.4 Update DTOs
**Files**: `src/main/java/at/or/reder/frodo/api/dto/`

Update:
- `DeviceRequest` - add optional `deviceType`
- `DeviceResponse` - add `deviceType`, `parentDeviceId`, `autoDiscovered`
- Create `DeviceDiscoveryRequest` record
- Create `DeviceDiscoveryResponse` record with discovered devices list

---

### Phase 2: Smart Meter SunSpec Models ✓
**Goal**: Parse Smart Meter register maps and add to SunSpec registry

#### 2.1 Use Official SunSpec Model Definitions
**Source**: GitHub repository https://github.com/sunspec/models

**Reference Files**:
- **Official JSON models**: https://github.com/sunspec/models/tree/master/json
  - `model_201.json` through `model_214.json` (meter models)
- **Official SMDX XML**: https://github.com/sunspec/models/tree/master/smdx
  - `smdx_00201.xml` through `smdx_00214.xml`
- **Excel reference**: `refdoc/sunspec/SunSpec_Information_Model_Reference_20240701_-1.xlsx`
- **PDF specifications**: 
  - `refdoc/sunspec/SunSpec-Device-Information-Model-Specificiation-V1-4.pdf`
  - `refdoc/sunspec/SunSpec-DER-Information-Model-Specification-V1-2.pdf`

**Alternative/Verification**: Fronius-specific register maps
- `refdoc/gen24-modbus-api-external-docs/Smart_Meter_Register_Map_Float.xlsx`
- `refdoc/gen24-modbus-api-external-docs/Smart_Meter_Register_Map_Int&SF.xlsx`

**Action**: 
1. Download or reference official SunSpec JSON model definitions from GitHub
2. Parse JSON model definitions for models 201-204 (Int+SF) and 211-214 (Float)
3. Extract field names, offsets, types, units, scale factors
4. Create `SunSpecModelDefinition` for each meter model
5. Verify field compatibility against Fronius Excel files
6. Handle any Fronius-specific extensions or deviations

**Model Availability**:
```bash
# Official SunSpec models found:
# - 201: Single Phase (AN) Meter
# - 202: Split Phase (ABN) Meter  
# - 203: Three Phase WYE Meter
# - 204: Three Phase Delta Meter
# - 211-214: Float variants of above
```


#### 2.2 Add Meter Constants
**File**: `src/main/java/at/or/reder/frodo/modbus/sunspec/SunSpecConstants.java`

Add constants:
```java
// Meter Models (Int+SF)
public static final int MODEL_METER_SINGLE_PHASE = 201;
public static final int MODEL_METER_SPLIT_PHASE = 202;
public static final int MODEL_METER_THREE_PHASE = 203;
public static final int MODEL_METER_THREE_PHASE_DELTA = 204;

// Meter Models (Float)
public static final int MODEL_METER_SINGLE_PHASE_FLOAT = 211;
public static final int MODEL_METER_SPLIT_PHASE_FLOAT = 212;
public static final int MODEL_METER_THREE_PHASE_WYE_FLOAT = 213;
public static final int MODEL_METER_THREE_PHASE_DELTA_FLOAT = 214;
public static final int MODEL_METER_THREE_PHASE_FLOAT = 213;
```

Add helper methods:
```java
public static boolean isMeterModel(int modelId)
public static boolean isFloatMeterModel(int modelId)
public static boolean isIntSfMeterModel(int modelId)
```

Update `modelName(int modelId)` switch statement.

#### 2.3 Define Meter Models in Registry
**File**: `src/main/java/at/or/reder/frodo/modbus/sunspec/SunSpecModelRegistry.java`

For each meter model (201-204, 211-214):
- Create `SunSpecModelDefinition` with all fields
- Add to registry map
- Include common meter fields:
  - `A` (Current)
  - `AphA`, `AphB`, `AphC` (Phase currents)
  - `PhV` (Voltage)
  - `PhVphA`, `PhVphB`, `PhVphC` (Phase voltages)
  - `W` (Active Power)
  - `VA` (Apparent Power)
  - `VAR` (Reactive Power)
  - `PF` (Power Factor)
  - `Hz` (Frequency)
  - `TotWhExp` (Total Energy Exported)
  - `TotWhImp` (Total Energy Imported)
  - Scale factors (for Int+SF models)

#### 2.4 Test Meter Model Decoding
**File**: `src/test/java/at/or/reder/frodo/modbus/sunspec/SunSpecMeterModelTest.java`

Unit tests:
- Parse meter model definitions
- Decode sample meter register data
- Verify field extraction (voltage, current, power, energy)
- Test both Int+SF and Float variants

---

### Phase 2.5: Solar API Integration ✓
**Goal**: Integrate Fronius Solar API for device discovery and metrics collection

#### 2.5.1 Create Solar API Client
**File**: `src/main/java/at/or/reder/frodo/solarapi/SolarApiClient.java` (new)

**Methods**:
```java
@ApplicationScoped
public class SolarApiClient {
  
  @Inject
  @RestClient
  WebClient webClient;
  
  // Get power flow data including all devices
  Uni<PowerFlowRealtimeData> getPowerFlowRealtimeData(String host, int port);
  
  // Get active device information
  Uni<ActiveDeviceInfo> getActiveDeviceInfo(String host, int port, String deviceClass);
  
  // Get Ohmpilot-specific real-time data
  Uni<OhmpilotRealtimeData> getOhmpilotRealtimeData(String host, int port);
}
```

**Configuration**:
```properties
# Solar API (HTTP/HTTPS - typically port 80/443)
frodo.solar-api.enabled=true
frodo.solar-api.timeout-seconds=5
frodo.solar-api.base-path=/solar_api/v1
frodo.solar-api.port=80
```

#### 2.5.2 Define Solar API Data Models
**File**: `src/main/java/at/or/reder/frodo/solarapi/model/` (new package)

**PowerFlowRealtimeData.java**:
```java
public record PowerFlowRealtimeData(
  String version,
  Map<String, InverterData> inverters,
  SiteData site,
  SmartloadsData smartloads,
  Map<String, MeterData> secondaryMeters
) {}

public record SmartloadsData(
  Map<String, OhmpilotData> ohmpilots,
  Map<String, OhmpilotEcoData> ohmpilotEcos
) {}

public record OhmpilotData(
  String componentId,  // Key from JSON object
  Double pAcTotal,     // P_AC_Total
  String state,        // State
  Double temperature   // Temperature
) {}
```

**Response wrapper**:
```java
public record SolarApiResponse<T>(
  SolarApiHead head,
  SolarApiBody<T> body
) {}
```

#### 2.5.3 Integrate Solar API into Discovery
**File**: `src/main/java/at/or/reder/frodo/modbus/service/DeviceDiscoveryService.java`

**Enhanced discovery logic**:
```java
Uni<List<DiscoveredDevice>> discoverDevices(String host, int port) {
  return solarApiClient.getPowerFlowRealtimeData(host, port)
    .onItem().transform(powerFlow -> {
      List<DiscoveredDevice> devices = new ArrayList<>();
      
      // Discover Ohmpilots from Solar API
      if (powerFlow.smartloads() != null) {
        powerFlow.smartloads().ohmpilots().forEach((componentId, data) -> {
          devices.add(new DiscoveredDevice(
            host, port, 
            -1,  // Unit ID unknown, to be discovered
            DeviceType.OHMPILOT,
            "Fronius", "Ohmpilot", componentId, null, List.of()
          ));
        });
      }
      
      return devices;
    })
    .onFailure().recoverWithItem(List.of())  // Fallback if Solar API unavailable
    .chain(solarApiDevices -> 
      // Continue with Modbus-based discovery for meters/inverters
      discoverViaModbus(host, port)
        .onItem().transform(modbusDevices -> {
          List<DiscoveredDevice> combined = new ArrayList<>(solarApiDevices);
          combined.addAll(modbusDevices);
          return combined;
        })
    );
}
```

#### 2.5.4 Add Solar API Health Check
**File**: `src/main/java/at/or/reder/frodo/health/SolarApiHealthCheck.java` (new)

```java
@ApplicationScoped
@Liveness
public class SolarApiHealthCheck implements HealthCheck {
  
  @Inject
  SolarApiClient solarApiClient;
  
  @Override
  public HealthCheckResponse call() {
    // Check if Solar API is reachable for configured devices
  }
}
```

**Estimated Time**: 4-6 hours

---

### Phase 3: Device Discovery Service ✓
**Goal**: Implement service to scan multiple Unit IDs and discover devices

#### 3.1 Create Discovery Configuration
**File**: `src/main/resources/application.properties`

Add properties:
```properties
# Device Discovery
frodo.discovery.enabled=true
frodo.discovery.unit-id-ranges=1,200-203
frodo.discovery.timeout-seconds=3
frodo.discovery.max-concurrent-scans=5
```

#### 3.2 Create DeviceDiscoveryService
**File**: `src/main/java/at/or/reder/frodo/modbus/service/DeviceDiscoveryService.java`

**Methods**:
```java
@ApplicationScoped
public class DeviceDiscoveryService {
  
  // Discover devices on a host:port connection
  Uni<List<DiscoveredDevice>> discoverDevices(String host, int port);
  
  // Scan specific Unit ID
  Uni<Optional<DiscoveredDevice>> scanUnitId(String host, int port, int unitId);
  
  // Determine device type from SunSpec models
  DeviceType determineDeviceType(SunSpecDiscoveryResult discovery);
  
  // Parse unit ID ranges ("1,200-203" -> [1,200,201,202,203])
  List<Integer> parseUnitIdRanges(String ranges);
}
```

**DiscoveredDevice record**:
```java
public record DiscoveredDevice(
  String host,
  int port,
  int unitId,
  DeviceType deviceType,
  String manufacturer,
  String model,
  String serialNumber,
  String version,
  List<Integer> modelIds
) {}
```

**Logic**:
1. Parse configured unit ID ranges
2. For each unit ID:
   - Create `DeviceAddress`
   - Attempt SunSpec discovery (catch exceptions for non-responding IDs)
   - If SunSpec signature found:
     - Read Common model (1) to get manufacturer/model/serial
     - Determine device type from models present:
       - Has inverter models (101-103, 111-113) → `INVERTER`
       - Has storage model (124) → `STORAGE`
       - Has meter models (201-204, 211-214) → `SMART_METER`
       - Has model 160 (MPPT) → likely `INVERTER`
       - Otherwise → `UNKNOWN`
   - If NO SunSpec signature found:
     - Attempt Modbus FC 0x2B (Read Device Identification)
     - Check manufacturer/model strings for "Fronius" + "Ohmpilot"/"smartload"
     - If matched → `OHMPILOT`
     - Otherwise skip (non-responding or unsupported device)
   - Return `DiscoveredDevice`
3. Filter out non-responding unit IDs
4. Return list of discovered devices

#### 3.3 Integration with Device Management
**File**: `src/main/java/at/or/reder/frodo/modbus/service/DeviceManagementService.java` (new)

**Methods**:
```java
// Create device entities from discovery results
Uni<List<ModbusDeviceEntity>> saveDiscoveredDevices(
  Long parentDeviceId, 
  List<DiscoveredDevice> discovered
);

// Update existing device type if auto-discovered
void updateDeviceType(ModbusDeviceEntity device, DeviceType type);
```

**Logic**:
- Check if device already exists (host+port+unitId unique constraint)
- If exists: update device type if null
- If new: create entity with `autoDiscovered=true`, set parent device
- Return saved entities

---

### Phase 4: REST API Extensions ✓
**Goal**: Expose discovery via REST endpoints

#### 4.1 Device Discovery Endpoints
**File**: `src/main/java/at/or/reder/frodo/api/DeviceDiscoveryResource.java` (new)

**Endpoints**:
```java
@Path("/api/devices")
public class DeviceDiscoveryResource {
  
  // Trigger discovery on a host:port
  @POST
  @Path("/discover")
  Uni<DeviceDiscoveryResponse> discoverDevices(DeviceDiscoveryRequest request);
  
  // Scan for sub-devices on an existing device connection
  @POST
  @Path("/{id}/discover-sub-devices")
  Uni<DeviceDiscoveryResponse> discoverSubDevices(@PathParam("id") Long id);
  
  // Get sub-devices for a parent device
  @GET
  @Path("/{id}/sub-devices")
  Uni<List<DeviceResponse>> getSubDevices(@PathParam("id") Long id);
}
```

**DeviceDiscoveryRequest**:
```java
public record DeviceDiscoveryRequest(
  String host,
  int port,
  String unitIdRanges,  // e.g., "1,200-203"
  boolean autoSave      // automatically save discovered devices
) {}
```

**DeviceDiscoveryResponse**:
```java
public record DeviceDiscoveryResponse(
  String host,
  int port,
  int devicesFound,
  List<DiscoveredDeviceDto> devices,
  List<Long> savedDeviceIds  // if autoSave=true
) {}
```

#### 4.2 Update DeviceResource
**File**: `src/main/java/at/or/reder/frodo/api/DeviceResource.java`

Add query parameter to list devices:
```java
@GET
Uni<List<DeviceResponse>> listDevices(
  @QueryParam("deviceType") DeviceType deviceType,
  @QueryParam("parentId") Long parentId
);
```

Add validation to prevent parent deletion if sub-devices exist.

#### 4.3 SunSpec Meter Endpoints
**File**: `src/main/java/at/or/reder/frodo/api/SunSpecResource.java`

Add endpoints:
```java
// Auto-detect and read meter model (prefers Float based on config)
@GET
@Path("/{id}/sunspec/meter")
Uni<SunSpecModelDataDto> readMeterModel(@PathParam("id") Long id);

// Read specific meter model
@GET
@Path("/{id}/sunspec/meter/{modelId}")
Uni<SunSpecModelDataDto> readMeterModelById(
  @PathParam("id") Long id,
  @PathParam("modelId") int modelId
);
```

Similar to `readInverterModel()` logic.

---

### Phase 5: Health Checks & Monitoring ✓
**Goal**: Monitor discovered devices

#### 5.1 Update ModbusHealthCheck
**File**: `src/main/java/at/or/reder/frodo/health/ModbusHealthCheck.java`

Enhance to:
- Group devices by parent/sub-device hierarchy
- Show device type in health output
- Report sub-device connection status

#### 5.2 Update ModbusMetrics
**File**: `src/main/java/at/or/reder/frodo/health/ModbusMetrics.java`

Add metrics:
- `frodo_devices_total{type="inverter|smart_meter|ohmpilot|unknown"}`
- `frodo_discovery_scans_total{result="success|failure"}`
- `frodo_discovery_devices_found_total`

---

### Phase 6: Documentation & Testing ✓
**Goal**: Comprehensive testing and documentation

#### 6.1 Unit Tests
**Files**:
- `src/test/java/at/or/reder/frodo/modbus/service/DeviceDiscoveryServiceTest.java`
  - Test unit ID range parsing
  - Test device type determination
  - Mock SunSpec discovery responses
- `src/test/java/at/or/reder/frodo/modbus/sunspec/SunSpecMeterModelTest.java`
  - Test meter model definitions
  - Test meter data decoding
- `src/test/java/at/or/reder/frodo/api/DeviceDiscoveryResourceTest.java`
  - Test discovery REST endpoints
  - Test sub-device listing

#### 6.2 Integration Tests
**File**: `src/native-test/java/at/or/reder/frodo/DeviceDiscoveryIT.java`

- Full discovery flow with mock Modbus responses
- Database persistence of discovered devices
- Parent-child relationship integrity

#### 6.3 Documentation
**Files**:
- `docs/DEVICE_DISCOVERY.md` - User guide for device discovery
- `docs/SUNSPEC_MODELS.md` - Update with meter models
- `AGENTS.md` - Update with new patterns and endpoints
- `README.md` - Add discovery features to feature list

---

## Implementation Priority (Smart Meter First)

### Sprint 1: Foundation (Estimated: 4-6 hours)
1. ✅ Create DeviceType enum
2. ✅ Extend ModbusDeviceEntity with new fields
3. ✅ Create Liquibase migration
4. ✅ Update DTOs (DeviceRequest, DeviceResponse)
5. ✅ Build and test database migration

### Sprint 2: Smart Meter Models (Estimated: 6-8 hours)
1. ✅ Parse Smart_Meter_Register_Map Excel files
2. ✅ Add meter constants to SunSpecConstants
3. ✅ Define meter models in SunSpecModelRegistry
4. ✅ Add meter model decoding support
5. ✅ Write unit tests for meter models

### Sprint 3: Discovery Service (Estimated: 8-10 hours)
1. ✅ Create DeviceDiscoveryService
2. ✅ Implement unit ID scanning logic
3. ✅ Implement device type detection
4. ✅ Add discovery configuration properties
5. ✅ Write unit tests for discovery service

### Sprint 4: REST API (Estimated: 4-6 hours)
1. ✅ Create DeviceDiscoveryResource
2. ✅ Add discovery endpoints
3. ✅ Update DeviceResource for filtering
4. ✅ Add meter endpoints to SunSpecResource
5. ✅ Write endpoint tests

### Sprint 5: Frontend Integration (Estimated: 4-6 hours)
1. ✅ Add "Discover Devices" button to device form
2. ✅ Display device type in device list
3. ✅ Show parent-child relationships
4. ✅ Add sub-device management UI

### Sprint 6: Polish & Documentation (Estimated: 4-6 hours)
1. ✅ Update health checks
2. ✅ Add metrics
3. ✅ Write documentation
4. ✅ Integration testing
5. ✅ Code review and cleanup

**Total Estimated Time**: 34-48 hours (updated with Solar API integration)

---

## Ohmpilot Future Work

### Research Results (2026-04-10)

#### Solar API DeviceClass Discovery ✅
**Searched**: Fronius Solar API documentation (`refdoc/solar_api.pdf`)
- **Result**: Ohmpilot is a recognized DeviceClass in Solar API
- **DeviceClass**: "Ohmpilot" (also referred to as "smartload" in API)
- **Dedicated Endpoint**: `GetOhmPilotRealtimeData` (pages 61-64)
- **Device Discovery**: `GetActiveDeviceInfo?DeviceClass=Ohmpilot`
- **Power Flow**: Included in `GetPowerFlowRealtimeData` as "Smartloads" node (API v12+)

#### SunSpec Model Search ✅
**Searched**: Official SunSpec repository (https://github.com/sunspec/models)
- **Result**: No dedicated Ohmpilot/smartload model found
- **Models checked**: All 114 JSON and SMDX files in repository
- **Keywords searched**: "ohm", "pilot", "heater", "thermal", "water heater", "smartload", "load control"
- **Conclusion**: Ohmpilot does not have a standard SunSpec model

#### Key Findings
- **Solar API Support**: Full REST API support via GetOhmPilotRealtimeData
- **DeviceClass Taxonomy**: Ohmpilot listed alongside Inverter, Storage, Meter, SensorCard, StringControl
- **No SunSpec Model**: No thermal management or smartload models in official SunSpec specifications
- **Data Fields Available** (from Solar API):
  - `CodeOfError` - Error/status code
  - `PowerReal_PAC_Sum` - Total power consumption [W]
  - `Temperature_Channel_1` - Temperature sensor reading [°C]
  - Additional hardware/firmware-dependent fields
- **Modbus Access**: Proprietary Fronius registers (non-SunSpec), register map unknown
- **Reference**: Model 122 (Status) contains "Ris" field (isolation resistance) - NOT Ohmpilot-related

### Implementation Strategy

**Phase 1: Solar API Discovery** (Included in Current Plan)
- **Primary**: Query `GET /solar_api/v1/GetPowerFlowRealtimeData.fcgi` to discover Ohmpilot devices
- **Data available**: ComponentId, P_AC_Total, State, Temperature
- **Advantage**: No Modbus Unit ID needed, works immediately
- **Limitation**: ComponentId may not match Modbus Unit ID

**Phase 2: Modbus Unit ID Mapping** (Future Enhancement)
- Test correlation between Solar API ComponentId and Modbus Unit ID
- Implement FC 0x2B scanning if needed to confirm Unit ID
- Create mapping table: ComponentId → Modbus Unit ID

**Phase 3: Direct Modbus Access** (Optional - Requires Register Map)
- Store as `DeviceType.OHMPILOT` in database
- No detailed register reading until register map is obtained

**Phase 2: Full Support** (Future Work)
1. **Obtain Register Map**:
   - Check Fronius support portal / developer documentation
   - Analyze Solar API `GetOhmPilotRealtimeData` response for field hints
   - Reverse engineer from live device if documentation unavailable
   - Community research: Fronius forums, photovoltaikforum.com

2. **Implement Custom Support**:
   - Create `OhmpilotRegisterMap` (non-SunSpec definitions)
   - Implement `OhmpilotService` similar to `SunSpecService`
   - Add decoder for Ohmpilot-specific data types
   - Create dedicated REST endpoint: `GET /api/devices/{id}/ohmpilot/status`

3. **Expected Data Points** (based on Solar API):
   - Temperature readings
   - Power consumption
   - Operating state/mode
   - Energy statistics
   - Control settings

### Detection Logic
```java
// In DeviceDiscoveryService.determineDeviceType()
DeviceType determineDeviceType(SunSpecDiscoveryResult discovery, String manufacturer, String model) {
  // Check for inverter models
  if (discovery.hasAnyModel(101, 102, 103, 111, 112, 113)) {
    return DeviceType.INVERTER;
  }
  
  // Check for storage/battery models
  if (discovery.hasAnyModel(124, 125, 126)) {
    return DeviceType.STORAGE;
  }
  
  // Check for meter models
  if (discovery.hasAnyModel(201, 202, 203, 204, 211, 212, 213, 214)) {
    return DeviceType.SMART_METER;
  }
  
  // Check for Ohmpilot by name (no specific SunSpec model)
  // Solar API DeviceClass: "Ohmpilot" (smartload)
  if (manufacturer.toLowerCase().contains("fronius") && 
      (model.toLowerCase().contains("ohmpilot") || model.toLowerCase().contains("smartload"))) {
    return DeviceType.OHMPILOT;
  }
  
  return DeviceType.UNKNOWN;
}
```

### Research Tasks (Next Steps)
- [ ] Search Fronius support portal for Ohmpilot Modbus documentation
- [ ] Analyze `GetOhmPilotRealtimeData` JSON response structure
- [ ] Check community forums for Ohmpilot register maps
- [ ] Attempt live discovery on physical Ohmpilot device to identify:
  - Default Unit ID
  - Available registers
  - Data format and structure
- [ ] Contact Fronius technical support for official Modbus specification

### Resources
- **Solar API**: `refdoc/solar_api.pdf` (pages 61-64)
- **SunSpec Repository**: https://github.com/sunspec/models (verified - no Ohmpilot model)
- **Community Forums**: 
  - https://www.photovoltaikforum.com/
  - Fronius Solar Web community
- **Fronius Support**: https://www.fronius.com/en/solar-energy/installers-partners/service-support

### Estimated Effort (Phase 2)
- Research & documentation: 4-8 hours
- Register map implementation: 8-12 hours
- REST API & service layer: 4-6 hours
- Testing & validation: 4-6 hours
- **Total**: 20-32 hours (after register map is obtained)


---

---

## Technical Decisions

### 1. Unit ID Scanning Strategy ✓
**Decision**: Configurable ranges (default: "1,200-203")

**Rationale**:
- Fast (scans 5 IDs in ~1-2 seconds vs 247 IDs in ~2-5 minutes)
- Covers Fronius defaults (inverter + 4 meters)
- User can extend ranges if needed (e.g., "1,200-203,50-60")

**Configuration**:
```properties
frodo.discovery.unit-id-ranges=1,200-203
```

### 2. Discovery Trigger ✓
**Decision**: Manual trigger only (REST API + frontend button)

**Rationale**:
- User controls when discovery runs
- Avoids startup delays
- Prevents unexpected database changes
- Can be added to scheduled jobs later if needed

### 3. Parent-Child Relationship ✓
**Decision**: Self-referential FK in ModbusDeviceEntity

**Rationale**:
- Simple schema (no additional tables)
- Clear hierarchy (parentDevice field)
- Supports cascade delete
- Query optimization via JPA

**Alternative Considered**: Separate entities per device type
- Rejected: Over-engineering, devices share 95% of fields

### 4. Device Type Detection ✓
**Decision**: Infer from SunSpec model IDs

**Logic**:
```java
if (hasInverterModels) return INVERTER;
if (hasMeterModels) return SMART_METER;
if (hasOhmpilotModels) return OHMPILOT;  // future
return UNKNOWN;
```

**Rationale**:
- Automatic, no user input needed
- Reliable (SunSpec models are device-specific)
- Can be overridden manually if needed

### 5. Excel Parsing Approach ✓
**Decision**: Parse Excel files programmatically (Apache POI or Python)

**Rationale**:
- Accurate field definitions
- Preserves all metadata (units, scale factors, descriptions)
- Repeatable process for future updates

**Alternative Considered**: Manual extraction
- Rejected: Error-prone, time-consuming, not maintainable

---

## Risk Mitigation

### Risk 1: Excel Parsing Complexity
**Mitigation**: 
- Use Apache POI library (already in Quarkus ecosystem)
- Create one-time parser utility class
- Manually verify first model, then automate rest

### Risk 2: Unknown Ohmpilot Unit ID
**Mitigation**:
- Default scan includes 1,200-203 (covers most devices)
- Users can manually specify extended ranges
- Discovery scans all configured ranges
- Document how to find Ohmpilot Unit ID

### Risk 3: Performance (Large Unit ID Ranges)
**Mitigation**:
- Parallel scanning (configurable concurrency)
- Timeout per unit ID (default 3 seconds)
- User-controlled ranges (don't scan all 247 by default)
- Progress reporting in API response

### Risk 4: Database Migration Failures
**Mitigation**:
- Test migration on H2 and Firebird
- Make new fields nullable (backward compatible)
- Provide rollback changeset
- Document manual migration steps

---

## Testing Strategy

### Unit Tests (Coverage Target: 80%)
- DeviceType enum methods
- Unit ID range parsing
- Device type determination logic
- Meter model definitions
- Discovery service methods
- DTO validation

### Integration Tests (Coverage Target: Key Flows)
- Full discovery flow (mock Modbus)
- Database persistence
- Parent-child relationships
- API endpoints
- Error handling (timeouts, invalid responses)

### Manual Testing Checklist
- [ ] Discover devices on live Fronius system
- [ ] Verify Smart Meter detection
- [ ] Verify inverter detection
- [ ] Test multiple meters (if available)
- [ ] Test parent-child deletion cascade
- [ ] Test device type filtering
- [ ] Verify frontend button triggers discovery
- [ ] Check health checks show all devices
- [ ] Verify metrics updated

---

## OpenAPI/Swagger Updates

Add new endpoints to Swagger UI:
- Tag: "Device Discovery"
- Endpoints: `/api/devices/discover`, `/api/devices/{id}/discover-sub-devices`, etc.
- Example requests/responses
- Error codes (404, 400, 500)

---

## Configuration Reference

```properties
# Device Discovery Settings
frodo.discovery.enabled=true
frodo.discovery.unit-id-ranges=1,200-203
frodo.discovery.timeout-seconds=3
frodo.discovery.max-concurrent-scans=5

# SunSpec Model Format (affects meter model preference)
frodo.sunspec.model-format=FLOAT  # or INT_SF
```

---

## Database Schema Changes

```sql
-- New columns in FroModbusDevice
ALTER TABLE FroModbusDevice ADD COLUMN device_type VARCHAR(50);
ALTER TABLE FroModbusDevice ADD COLUMN parent_device_id BIGINT;
ALTER TABLE FroModbusDevice ADD COLUMN auto_discovered BOOLEAN DEFAULT FALSE;

-- Foreign key constraint
ALTER TABLE FroModbusDevice 
  ADD CONSTRAINT fk_FroDevice_parent 
  FOREIGN KEY (parent_device_id) 
  REFERENCES FroModbusDevice(id) 
  ON DELETE CASCADE;

-- Index for queries
CREATE INDEX idx_FroDevice_parent ON FroModbusDevice(parent_device_id);
CREATE INDEX idx_FroDevice_type ON FroModbusDevice(device_type);
```

---

## API Endpoint Summary

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/devices/discover` | Discover devices on host:port |
| POST | `/api/devices/{id}/discover-sub-devices` | Discover sub-devices for existing device |
| GET | `/api/devices/{id}/sub-devices` | List sub-devices |
| GET | `/api/devices/{id}/sunspec/meter` | Read meter model (auto-detect) |
| GET | `/api/devices/{id}/sunspec/meter/{modelId}` | Read specific meter model |

### Updated Endpoints
| Method | Path | Changes |
|--------|------|---------|
| GET | `/api/devices` | Add `deviceType` and `parentId` query params |
| GET | `/api/devices/{id}` | Include `deviceType`, `parentDeviceId`, `autoDiscovered` in response |
| DELETE | `/api/devices/{id}` | Prevent deletion if sub-devices exist (or cascade) |

---

## Success Criteria

### Phase 1 (Smart Meter Support)
- ✅ Smart Meter devices can be discovered via REST API
- ✅ Smart Meter SunSpec models (201-203, 211-213) are defined and decodable
- ✅ Device type is auto-detected and stored
- ✅ Parent-child relationships work correctly
- ✅ Frontend shows "Discover Devices" button
- ✅ Health checks include all devices
- ✅ Unit tests pass (>80% coverage)
- ✅ Documentation complete

### Phase 2 (Ohmpilot Support - Future)
- ✅ Ohmpilot default Unit ID identified
- ✅ Ohmpilot SunSpec models identified and implemented
- ✅ Ohmpilot devices auto-discovered
- ✅ Custom Ohmpilot endpoint (if needed)

---

## References

### Documentation
- `refdoc/modbus.pdf` - Fronius Modbus TCP documentation
- `refdoc/solar_api.pdf` - Fronius Solar API documentation
- `refdoc/gen24-modbus-api-external-docs/Smart_Meter_Register_Map_*.xlsx` - Meter register maps
- `docs/SUNSPEC_MODELS.md` - SunSpec model documentation
- `AGENTS.md` - Project coding guidelines

### SunSpec Resources
- SunSpec Alliance: https://sunspec.org/
- SunSpec Information Models: https://sunspec.org/sunspec-information-models/
- **Official SunSpec Models Repository**: https://github.com/sunspec/models
  - JSON model definitions: https://github.com/sunspec/models/tree/master/json
  - SMDX XML definitions: https://github.com/sunspec/models/tree/master/smdx
- Local SunSpec specifications: `refdoc/sunspec/` directory
  - Device Information Model (V1.4)
  - DER Information Model (V1.2)
  - Complete model reference (Excel)

### External Libraries
- Apache POI: Excel file parsing (if needed)
- Quarkus Reactive: Uni/Multi for async operations
- Panache: JPA repository operations

---

## Change Log

| Date | Author | Changes |
|------|--------|---------|
| 2026-04-10 | AI Agent | Initial plan created based on user requirements |
| 2026-04-10 | AI Agent | Updated with official SunSpec models reference, Ohmpilot research findings, added models 204/214 |
| 2026-04-10 | AI Agent | Updated discovery logic to detect Ohmpilot via FC 0x2B when no SunSpec signature found |
| 2026-04-10 | AI Agent | Added Phase 2.5: Solar API integration for Ohmpilot discovery via GetPowerFlowRealtimeData endpoint |

---

## Next Steps

1. ✅ Review and approve this plan
2. ✅ Parse Smart Meter Excel files (Sprint 2, Task 1)
3. ✅ Begin Phase 1 implementation (DeviceType enum, database migration)
4. ✅ Proceed with Smart Meter model definitions
5. 🔄 Implement discovery service
6. 🔄 Add REST endpoints
7. 🔄 Frontend integration
8. 🔄 Testing and documentation

---

**Status**: ⏸️ Awaiting approval to proceed with implementation

