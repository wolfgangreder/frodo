# Device Discovery Plan Update Summary

**Date**: 2026-04-10  
**Status**: Plan updated with new SunSpec reference documentation and Ohmpilot research findings

## Changes Made

### 1. Terminology Correction
- ✅ Changed "OHMpilot" → "Ohmpilot" throughout all documentation
- Files updated:
  - `docs/DEVICE_DISCOVERY_PLAN.md`
  - `AGENTS.md`

### 2. SunSpec Model Reference Updates

#### Added Official SunSpec Repository Reference
- **Repository**: https://github.com/sunspec/models
- **JSON models**: models 201-204, 211-214 (meter models)
- **SMDX XML**: smdx_00201.xml through smdx_00214.xml

#### Added Model 204 and 214 (Three Phase Delta Meter)
Previous coverage:
- 201-203 (Int+SF): Single, Split, Three Phase WYE
- 211-213 (Float): Single, Split, Three Phase WYE

**New coverage**:
- **204** (Int+SF): Three Phase **Delta** Meter
- **214** (Float): Three Phase **Delta** Meter

Updated constants in plan:
```java
// Added:
public static final int MODEL_METER_THREE_PHASE_DELTA = 204;
public static final int MODEL_METER_THREE_PHASE_DELTA_FLOAT = 214;

// Renamed for clarity:
MODEL_METER_THREE_PHASE → MODEL_METER_THREE_PHASE_WYE
MODEL_METER_THREE_PHASE_FLOAT → MODEL_METER_THREE_PHASE_WYE_FLOAT
```

### 3. Updated Implementation Approach

#### Phase 2: Smart Meter Models
**Old approach**: Parse Fronius Excel files manually  
**New approach**: Use official SunSpec JSON model definitions from GitHub

**Benefits**:
- Standard, validated model definitions
- Complete field metadata (names, types, units, scale factors)
- Maintained by SunSpec Alliance
- Easy to verify against Fronius Excel files

**Process**:
1. Clone/reference official SunSpec models repository
2. Parse JSON model definitions (201-204, 211-214)
3. Create `SunSpecModelDefinition` for each model
4. Verify compatibility with Fronius Excel files
5. Handle any Fronius-specific extensions

### 4. Ohmpilot Research Findings

#### Official SunSpec Model Search ✅
**Searched**: https://github.com/sunspec/models  
**Result**: No dedicated Ohmpilot model exists

**Evidence**:
- Checked all JSON and SMDX files in repository
- Searched for keywords: "ohm", "pilot", "heater", "thermal", "water heater"
- Model 122 (Status) has "Ris" field (isolation resistance) - NOT Ohmpilot-related

**Conclusion**: Ohmpilot uses proprietary Fronius Modbus registers, not standard SunSpec

#### Updated Implementation Strategy

**Phase 1: Basic Discovery** (Current - with Smart Meter)
- Detect Ohmpilot via unit ID scanning
- Use FC 0x2B (Read Device Identification) for manufacturer/model/serial
- Identify by name matching: "Fronius" + "Ohmpilot"
- Store as `DeviceType.OHMPILOT`
- **No register reading** until register map is obtained

**Phase 2: Full Support** (Future - after obtaining register map)
1. Research Fronius documentation/support for register map
2. Analyze Solar API `GetOhmPilotRealtimeData` for field hints
3. Implement `OhmpilotService` (non-SunSpec)
4. Create dedicated REST endpoint
5. Add decoder for proprietary data format

**Detection Logic** (updated):
```java
DeviceType determineDeviceType(SunSpecDiscoveryResult discovery, String manufacturer, String model) {
  if (discovery.hasAnyModel(101, 102, 103, 111, 112, 113)) {
    return DeviceType.INVERTER;
  }
  
  // NEW: Added models 204 and 214
  if (discovery.hasAnyModel(201, 202, 203, 204, 211, 212, 213, 214)) {
    return DeviceType.SMART_METER;
  }
  
  // NEW: Name-based detection (no SunSpec model)
  if (manufacturer.toLowerCase().contains("fronius") && 
      model.toLowerCase().contains("ohmpilot")) {
    return DeviceType.OHMPILOT;
  }
  
  return DeviceType.UNKNOWN;
}
```

### 5. Documentation Updates

#### AGENTS.md
Updated reference documentation section:
```markdown
**Reference Documentation:**
- `refdoc/modbus.pdf` - Fronius Modbus TCP protocol specification
- `refdoc/solar_api.pdf` - Fronius Solar API documentation
- `refdoc/sunspec/` - SunSpec Alliance specifications:
  - `SunSpec_Information_Model_Reference_20240701_-1.xlsx`
  - `SunSpec-Device-Information-Model-Specificiation-V1-4.pdf`
  - `SunSpec-DER-Information-Model-Specification-V1-2.pdf`
  - `SunSpec-Modbus-FactSheet-RevA-2019-07-web.pdf`
- `refdoc/gen24-modbus-api-external-docs/` - Fronius Gen24 register maps
- GitHub Repository: https://github.com/sunspec/models
```

Updated SunSpec Protocol section:
- Added meter models (201-203, 211-213) reference
- Added GitHub repository link
- Added cross-reference to `DEVICE_DISCOVERY_PLAN.md`

#### DEVICE_DISCOVERY_PLAN.md
- Section 2.1: Replaced "Parse Excel Files" with "Use Official SunSpec Models"
- Ohmpilot section: Added comprehensive research findings
- References section: Added GitHub repository links
- Changelog: Added update entry

### 6. Key Insights

#### Smart Meter Support
- ✅ **8 official SunSpec models** available (201-204, 211-214)
- ✅ **JSON definitions** ready to parse
- ✅ **Complete field metadata** in repository
- ✅ **Fronius Excel files** available for verification

#### Ohmpilot Support
- ❌ **No standard SunSpec model** exists
- ⚠️ **Proprietary registers** (Fronius-specific)
- ⏳ **Register map needed** before full implementation
- ✅ **Basic discovery** possible via name matching
- 📚 **Resources**: Solar API docs (pages 61-64), Fronius support portal

### 7. Next Steps

#### Ready to Implement (Smart Meter)
1. ✅ Plan approved and updated
2. 🔄 Parse SunSpec JSON models from GitHub
3. 🔄 Implement meter model definitions
4. 🔄 Add meter constants to SunSpecConstants
5. 🔄 Create discovery service
6. 🔄 Add REST endpoints
7. 🔄 Test with live Smart Meter device

#### Future Research (Ohmpilot)
1. ⏳ Search Fronius support portal for Modbus documentation
2. ⏳ Analyze Solar API response structure
3. ⏳ Check community forums for register maps
4. ⏳ Test with live Ohmpilot device (if available)
5. ⏳ Contact Fronius technical support

## Files Modified

| File | Changes |
|------|---------|
| `AGENTS.md` | Updated reference documentation, SunSpec protocol section |
| `docs/DEVICE_DISCOVERY_PLAN.md` | Updated terminology, SunSpec references, Ohmpilot research, added models 204/214 |
| `docs/PLAN_UPDATE_SUMMARY.md` | **New** - This summary document |

## References Added

- https://github.com/sunspec/models - Official SunSpec model definitions
- `refdoc/sunspec/` - Local SunSpec specifications (PDFs, Excel)
- `refdoc/solar_api.pdf` - Fronius Solar API (Ohmpilot endpoints)

---

**Status**: ✅ Plan updated and ready for review  
**Next**: Await user approval to begin implementation
