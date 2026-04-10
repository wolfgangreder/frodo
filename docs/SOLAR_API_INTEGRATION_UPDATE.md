# Solar API Integration Update

**Date**: 2026-04-10  
**Status**: Plan Updated

---

## Overview

Based on user input about the Fronius Solar API endpoint `/solar_api/v1/GetPowerFlowRealtimeData.fcgi`, the implementation plan has been updated to include **Solar API integration** as the primary method for Ohmpilot device discovery and metrics collection.

---

## Key Discovery: Live PowerFlowRealtimeData

### Endpoint
```
GET http://{inverter-ip}/solar_api/v1/GetPowerFlowRealtimeData.fcgi
```

### Real-World Data Structure (from http://192.168.1.160)

```json
{
  "Body": {
    "Data": {
      "Inverters": {
        "1": {
          "Battery_Mode": "nearly depleted",
          "DT": 1,
          "E_Total": 352448.24611111113,
          "P": 300.64181518554688,
          "SOC": 8.6
        }
      },
      "Site": {
        "BackupMode": false,
        "BatteryStandby": false,
        "Meter_Location": "grid",
        "Mode": "bidirectional",
        "P_Akku": 3.34,
        "P_Grid": 421.5,
        "P_Load": -719.48,
        "P_PV": 326.41,
        "rel_Autonomy": 41.42,
        "rel_SelfConsumption": 100.0
      },
      "Smartloads": {
        "Ohmpilots": {
          "0": {
            "P_AC_Total": 0.0,
            "State": "normal",
            "Temperature": 52.9
          }
        },
        "OhmpilotEcos": {}
      },
      "SecondaryMeters": {},
      "Version": "13"
    }
  }
}
```

### Key Findings

1. **Ohmpilot Present**: ComponentId "0" discovered in Smartloads.Ohmpilots
2. **Available Metrics**:
   - `P_AC_Total`: 0.0 W (current power consumption)
   - `State`: "normal" (operating state)
   - `Temperature`: 52.9°C (storage temperature)
3. **Version 13**: Supports both Ohmpilots and OhmpilotEcos
4. **Integration Point**: Single endpoint provides all devices (inverter, battery, meters, Ohmpilots)

---

## Plan Updates

### Added: Phase 2.5 - Solar API Integration

**Goal**: Integrate Fronius Solar API for device discovery and metrics collection

**Components**:

#### 2.5.1 Solar API Client
- HTTP client for Solar API endpoints
- Methods: `getPowerFlowRealtimeData()`, `getActiveDeviceInfo()`, `getOhmpilotRealtimeData()`
- Configuration: base path, timeout, port (default 80)

#### 2.5.2 Data Models
- `PowerFlowRealtimeData` - Main response wrapper
- `SmartloadsData` - Ohmpilot data container
- `OhmpilotData` - Individual Ohmpilot metrics
- `SolarApiResponse<T>` - Generic wrapper

#### 2.5.3 Discovery Integration
- Query Solar API first to discover Ohmpilot devices
- Extract ComponentId, metrics from Smartloads.Ohmpilots
- Fallback to Modbus-based discovery if Solar API unavailable
- Combine results from both sources

#### 2.5.4 Health Check
- Monitor Solar API availability
- Report connectivity status
- Alert on API failures

**Estimated Time**: 4-6 hours

---

## Updated Architecture

### Multi-Source Discovery Strategy

```
┌─────────────────────────────────────────────────────────┐
│           Device Discovery Service                      │
└─────────────────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
┌─────────────────┐    ┌─────────────────┐
│  Solar API      │    │  Modbus TCP     │
│  Discovery      │    │  Discovery      │
└─────────────────┘    └─────────────────┘
        │                       │
        │                       │
┌───────┴─────────┐    ┌────────┴────────┐
│ Ohmpilots       │    │ Smart Meters    │
│ (ComponentId)   │    │ (Unit IDs       │
│                 │    │  200-203)       │
└─────────────────┘    └─────────────────┘
                       │ Inverters       │
                       │ (Unit ID 1)     │
                       └─────────────────┘
```

### Data Flow

1. **Solar API Query** (Primary for Ohmpilot)
   - Query `GetPowerFlowRealtimeData.fcgi`
   - Parse `Smartloads.Ohmpilots` object
   - Extract ComponentIds and metrics
   - Create `DiscoveredDevice` records (type=OHMPILOT)

2. **Modbus Discovery** (Primary for Meters/Inverters)
   - Scan Unit ID ranges (1, 200-203)
   - Check for SunSpec signature at register 40000
   - Read Common model (1) for device info
   - Determine device type from model chain
   - Create `DiscoveredDevice` records

3. **Result Combination**
   - Merge devices from both sources
   - Deduplicate if needed
   - Save to database with appropriate parent/child relationships

---

## Benefits of Solar API Integration

### Advantages
1. **No Modbus Unit ID needed** - ComponentId sufficient for initial discovery
2. **Unified endpoint** - Single API call returns all devices
3. **Real-time metrics** - Power, state, temperature immediately available
4. **No register map required** - Works without knowing Modbus registers
5. **Simpler implementation** - HTTP client vs. Modbus protocol complexity

### Limitations
1. **ComponentId ≠ Unit ID** - Mapping unknown (requires testing)
2. **Read-only** - Cannot control Ohmpilot via Solar API (Modbus needed for control)
3. **Dependency on Solar API** - Requires HTTP port 80 accessible
4. **Limited to Fronius** - Not a standard protocol

### Strategy
- **Use Solar API** for discovery and monitoring (power, temp, state)
- **Use Modbus** for direct control when needed (future enhancement)
- **Fallback to Modbus** if Solar API unavailable

---

## Implementation Impact

### Updated Estimates

| Phase | Original | Updated | Change |
|-------|----------|---------|--------|
| Phase 1: Data Model | 6-8h | 6-8h | - |
| Phase 2: SunSpec Models | 8-12h | 8-12h | - |
| **Phase 2.5: Solar API** | **-** | **4-6h** | **+4-6h** |
| Phase 3: Discovery Service | 6-8h | 6-8h | - |
| Phase 4: REST API | 4-6h | 4-6h | - |
| Phase 5: Health & Monitoring | 2-3h | 2-3h | - |
| Phase 6: Documentation | 4-6h | 4-6h | - |
| **Total** | **30-42h** | **34-48h** | **+4-6h** |

### Sprint Breakdown (Updated)

**Sprint 2**: SunSpec Meter Models + Solar API Integration (12-18h)
- Tasks 1-4: Parse SunSpec JSON models (8-12h)
- **Task 5: Implement Solar API client** (2-3h)
- **Task 6: Add Solar API data models** (1-2h)
- **Task 7: Create Solar API health check** (1h)

---

## Documentation Updates

### Files Modified

1. **`docs/DEVICE_DISCOVERY_PLAN.md`** (1055 lines)
   - Added Phase 2.5: Solar API Integration
   - Updated Ohmpilot background section with PowerFlowRealtimeData structure
   - Updated discovery logic to use Solar API as primary source
   - Updated time estimates
   - Added changelog entry

2. **`AGENTS.md`** (316 lines)
   - Added `solarapi/` package to Package Structure
   - Added new "Fronius Solar API" section with:
     - Base URL and key endpoints
     - PowerFlowRealtimeData structure
     - Use cases and integration strategy
   - Updated SunSpec meter models (204/214)

3. **`docs/SOLAR_API_INTEGRATION_UPDATE.md`** (NEW)
   - This summary document

---

## Next Steps

### Ready to Implement

The plan is fully updated and ready for implementation approval. The Solar API integration provides:

✅ **Immediate Ohmpilot discovery** without Modbus complexity  
✅ **Real-time metrics** (power, state, temperature)  
✅ **Unified data source** for all devices  
✅ **Graceful fallback** to Modbus-only discovery  

### Awaiting User Approval

Please review the updated plan and confirm:
1. ✅ Phase 2.5 (Solar API integration) looks correct
2. ✅ Discovery strategy (Solar API primary, Modbus fallback) is acceptable
3. ✅ Time estimates (34-48h total) are acceptable
4. ✅ Ready to proceed with Phase 1 implementation

---

**Status**: ⏸️ Plan updated, awaiting approval to begin implementation
