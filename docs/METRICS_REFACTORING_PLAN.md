# Metrics Refactoring Plan

## Executive Summary

Refactor the metrics system to adopt best practices for Prometheus naming conventions, improve observability with proper tag-based dimensions, auto-generate comprehensive documentation from SunSpec register maps, and integrate documentation into both the repository and the web UI.

## Goals

1. **Report model ID as a tag attribute** instead of embedding it in metric names
2. **Report multi-channel metrics** (e.g., MPPT DC inputs) with channel as both a tag and optional name suffix
3. **Generate comprehensive metrics documentation** from the Fronius register map JSON files in `refdoc/`
4. **Display documentation** both as a standalone markdown file and in the web UI
5. **Clean migration** - replace old naming schema entirely

## Current State Analysis

### Current Metrics Implementation

**Location**: `MetricsScrapingService.java` (lines 208-253)

**Current naming pattern**:
```
frodo_sunspec_{modelId}_{fieldName}
```

**Example**:
```
frodo_sunspec_113_w{device_id="1", device_name="Inverter01", model_id="113", field="W"}
```

**Problems**:
- Model ID appears in BOTH the metric name AND as a tag (redundant)
- Multi-channel fields (e.g., model 160 `module/1/DCA`) don't have channel dimension
- No semantic meaning in metric names (what does "113" mean?)
- No descriptions or units metadata accessible to users

### SunSpec Models with Multi-Channel Data

**Model 160: Multiple MPPT Extension**
- Has repeating module blocks (typically 2 MPPTs)
- Field naming: `module/1/DCA`, `module/2/DCA`, etc.
- Each module has: `ID`, `IDStr`, `DCA`, `DCV`, `DCW`, `DCWH`, `Tms`, `Tmp`, `DCSt`, `DCEvt`

**Model 103/113: Inverter (Three Phase)**
- Phase-specific fields: `AphA`, `AphB`, `AphC` (currents)
- Phase voltage fields: `PhVphA`, `PhVphB`, `PhVphC`
- These could benefit from a `phase` tag

## Proposed Solution

### 1. New Metric Naming Schema

#### Naming Convention

**Base format**: `frodo_sunspec_{semantic_name}`

**Tags**:
- `device_id` - Modbus device ID (existing)
- `device_name` - Device friendly name (existing)
- `model_id` - SunSpec model ID (moved from name to tag)
- `field` - SunSpec field name (existing)
- `channel` - Channel/module number (NEW - for multi-channel metrics)
- `phase` - AC phase identifier (NEW - for three-phase metrics)

#### ISO Base Units in Metric Names

Following Prometheus best practices and the SI/ISO 80000 standard, all metric names
include the **base unit as a suffix**. This removes ambiguity and enables correct
unit handling in Grafana, Prometheus recording rules, and alerting.

| Physical Quantity | ISO Base Unit | Metric Suffix | Notes |
|-------------------|---------------|---------------|-------|
| Electric current | ampere (A) | `_amperes` | SI base unit |
| Electric potential | volt (V) | `_volts` | SI derived (kg·m²·s⁻³·A⁻¹) |
| Power | watt (W) | `_watts` | SI derived (kg·m²·s⁻³) |
| Apparent power | volt-ampere (VA) | `_voltamperes` | Same dimension as W |
| Reactive power | var | `_vars` | Same dimension as W |
| Energy | watt-hour (Wh) | `_watt_hours` | Industry standard (1 Wh = 3600 J) |
| Frequency | hertz (Hz) | `_hertz` | SI derived (s⁻¹) |
| Temperature | degree Celsius (°C) | `_celsius` | SI derived (K − 273.15) |
| Ratio / percentage | ratio (0–1) | `_ratio` | Dimensionless; PF, SoC |
| Resistance | ohm (Ω) | `_ohms` | SI derived (kg·m²·s⁻³·A⁻²) |
| Capacity | ampere-hour (Ah) | `_ampere_hours` | Industry standard |
| Time | second (s) | `_seconds` | SI base unit |

> **Note**: Watt-hours are preferred over joules for energy metrics because the
> solar/energy industry universally uses Wh/kWh/MWh. Prometheus best practice
> is to use base units, but Wh is the de-facto base unit in this domain.

#### Example Transformations

**Before**:
```
frodo_sunspec_113_w{device_id="1", device_name="Inverter01", model_id="113", field="W"}
frodo_sunspec_113_apha{device_id="1", device_name="Inverter01", model_id="113", field="AphA"}
frodo_sunspec_160_module_1_dca{device_id="1", device_name="Inverter01", model_id="160", field="module/1/DCA"}
```

**After**:
```
frodo_sunspec_ac_power_watts{device_id="1", device_name="Inverter01", model_id="113", field="W"}
frodo_sunspec_ac_current_amperes{device_id="1", device_name="Inverter01", model_id="113", field="AphA", phase="A"}
frodo_sunspec_dc_current_amperes{device_id="1", device_name="Inverter01", model_id="160", field="DCA", channel="1"}
```

#### Semantic Name Mapping

Create a mapping from (modelId, fieldName) -> semantic metric name:

**Model 1 (Common)**:
- All fields -> `frodo_sunspec_device_info` (string gauge, no unit suffix)

**Models 101-103, 111-113 (Inverter)**:
| Field | Semantic Name | Unit Suffix | Additional Tags |
|-------|---------------|-------------|-----------------|
| `W` | `ac_power` | `_watts` | - |
| `A` | `ac_current` | `_amperes` | - |
| `AphA/B/C` | `ac_current` | `_amperes` | `phase=A/B/C` |
| `PhVphA/B/C` | `ac_voltage_phase` | `_volts` | `phase=A/B/C` |
| `PPVphAB/BC/CA` | `ac_voltage_line` | `_volts` | `line=AB/BC/CA` |
| `Hz` | `ac_frequency` | `_hertz` | - |
| `VA` | `ac_apparent_power` | `_voltamperes` | - |
| `VAr` | `ac_reactive_power` | `_vars` | - |
| `PF` | `ac_power_factor` | `_ratio` | - |
| `WH` | `ac_energy_total` | `_watt_hours` | - |
| `DCA` | `dc_current` | `_amperes` | - |
| `DCV` | `dc_voltage` | `_volts` | - |
| `DCW` | `dc_power` | `_watts` | - |
| `TmpCab/Snk/Trns/Ot` | `temperature` | `_celsius` | `location=cabinet/heatsink/transformer/other` |
| `St` | `operating_state` | (none) | - |
| `StVnd` | `vendor_state` | (none) | - |

**Model 120 (Nameplate)**:
| Field | Semantic Name | Unit Suffix |
|-------|---------------|-------------|
| `WRtg` | `rating_power` | `_watts` |
| `VARtg` | `rating_apparent_power` | `_voltamperes` |
| `ARtg` | `rating_current` | `_amperes` |
| `WHRtg` | `rating_energy` | `_watt_hours` |
| `MaxChaRte` | `rating_max_charge_rate` | `_watts` |
| `MaxDisChaRte` | `rating_max_discharge_rate` | `_watts` |

**Model 121 (Settings)**:
| Field | Semantic Name | Unit Suffix |
|-------|---------------|-------------|
| `WMax` | `setting_max_power` | `_watts` |
| `VRef` | `setting_voltage_reference` | `_volts` |
| `VAMax` | `setting_max_apparent_power` | `_voltamperes` |
| `ECPNomHz` | `setting_nominal_frequency` | `_hertz` |

**Model 122 (Extended Status)**:
| Field | Semantic Name | Unit Suffix |
|-------|---------------|-------------|
| `ActWh` | `lifetime_energy_active` | `_watt_hours` |
| `ActVAh` | `lifetime_energy_apparent` | `_voltampere_hours` |
| `WAval` | `available_power` | `_watts` |
| `VArAval` | `available_reactive_power` | `_vars` |
| `Ris` | `isolation_resistance` | `_ohms` |

**Model 123 (Controls)**:
| Field | Semantic Name | Unit Suffix |
|-------|---------------|-------------|
| `WMaxLimPct` | `control_power_limit` | `_ratio` |
| `OutPFSet` | `control_power_factor` | `_ratio` |
| `Conn` | `control_connection_state` | (none) |

**Model 124 (Storage)**:
| Field | Semantic Name | Unit Suffix |
|-------|---------------|-------------|
| `ChaState` | `battery_charge_state` | `_ratio` |
| `InBatV` | `battery_voltage` | `_volts` |
| `ChaSt` | `battery_charge_status` | (none) |
| `StorAval` | `battery_available_capacity` | `_ampere_hours` |
| `WChaMax` | `battery_max_charge_power` | `_watts` |

**Model 160 (MPPT)**:
| Field Pattern | Semantic Name | Unit Suffix | Additional Tags |
|---------------|---------------|-------------|-----------------|
| `module/{n}/DCA` | `dc_current` | `_amperes` | `channel={n}` |
| `module/{n}/DCV` | `dc_voltage` | `_volts` | `channel={n}` |
| `module/{n}/DCW` | `dc_power` | `_watts` | `channel={n}` |
| `module/{n}/DCWH` | `dc_energy_total` | `_watt_hours` | `channel={n}` |
| `module/{n}/Tmp` | `dc_temperature` | `_celsius` | `channel={n}` |
| `module/{n}/IDStr` | `mppt_id` | (none) | `channel={n}` |

### 2. Metadata Generation System

#### Metadata Schema (JSON)

Create `src/main/resources/metrics-metadata.json`:

```json
{
  "metrics": [
    {
      "metricName": "frodo_sunspec_ac_power_watts",
      "description": "AC Power output from the inverter",
      "unit": "W",
      "baseUnit": "watts",
      "type": "gauge",
      "models": [
        {"modelId": 101, "modelName": "Inverter (Single Phase, Int+SF)", "field": "W"},
        {"modelId": 111, "modelName": "Inverter (Single Phase, Float)", "field": "W"},
        {"modelId": 113, "modelName": "Inverter (Three Phase, Float)", "field": "W"}
      ],
      "tags": ["device_id", "device_name", "model_id", "field"],
      "typicalRange": {"min": -100000, "max": 100000},
      "registerInfo": {
        "address": "40092",
        "size": 2,
        "dataType": "float32",
        "rw": "R",
        "functionCode": "0x03"
      }
    },
    {
      "metricName": "frodo_sunspec_dc_current_amperes",
      "description": "DC current from PV strings (per MPPT channel)",
      "unit": "A",
      "baseUnit": "amperes",
      "type": "gauge",
      "models": [
        {"modelId": 160, "modelName": "Multiple MPPT Extension", "field": "DCA"}
      ],
      "tags": ["device_id", "device_name", "model_id", "field", "channel"],
      "multiChannel": true,
      "channelCount": 2,
      "typicalRange": {"min": 0, "max": 50}
    }
  ]
}
```

#### Metadata Generator Script

Create `scripts/generate-metrics-metadata.js` (Node.js):

**Input**: 
- `refdoc/gen24-modbus-api-external-docs/Gen24_Primo_Symo_Float_PARSED.json`
- `src/main/resources/metrics-semantic-mapping.json` (manual mapping file)

**Process**:
1. Parse Fronius JSON register map
2. Load semantic name mapping
3. For each mapped metric:
   - Extract description, unit, register info from JSON
   - Identify if multi-channel (field name contains `module/`)
   - Generate metadata entry
4. Output `src/main/resources/metrics-metadata.json`

**Run**: As part of Gradle build process (custom task)

### 3. Code Changes

#### A. Create Metadata Model Classes

**File**: `src/main/java/at/or/reder/frodo/modbus/metrics/MetricMetadata.java`

```java
public record MetricMetadata(
  String metricName,
  String description,
  String unit,
  String type,
  List<ModelMapping> models,
  List<String> tags,
  boolean multiChannel,
  Integer channelCount,
  Range typicalRange,
  RegisterInfo registerInfo
) {
  public record ModelMapping(int modelId, String modelName, String field) {}
  public record Range(Double min, Double max) {}
  public record RegisterInfo(String address, int size, String dataType, String rw, String functionCode) {}
}
```

**File**: `src/main/java/at/or/reder/frodo/modbus/metrics/MetricMetadataRegistry.java`

```java
@ApplicationScoped
public class MetricMetadataRegistry {
  private final Map<String, MetricMetadata> metadataByName;
  private final Map<String, MetricMetadata> metadataByField; // (modelId_fieldName) -> metadata
  
  @PostConstruct
  void loadMetadata() {
    // Load from classpath: /metrics-metadata.json
  }
  
  public Optional<MetricMetadata> getByMetricName(String metricName);
  public Optional<MetricMetadata> getByField(int modelId, String fieldName);
  public List<MetricMetadata> getAll();
}
```

#### B. Update MetricsScrapingService

**Changes**:

1. Inject `MetricMetadataRegistry`
2. Update `buildMetricName()` method:
   - Look up semantic name from metadata registry
   - Parse multi-channel field names (e.g., `module/1/DCA` -> field=`DCA`, channel=`1`)
   - Fall back to legacy naming if no metadata found
3. Update `registerGauges()`:
   - Add `channel` tag for multi-channel metrics
   - Add `phase` tag for phase-specific metrics (AphA -> phase=A)
   - Use metadata description in gauge registration
   - Include unit in description

**Example**:
```java
private String buildMetricName(int modelId, String fieldName) {
  Optional<MetricMetadata> metadata = metadataRegistry.getByField(modelId, fieldName);
  if (metadata.isPresent()) {
    return metadata.get().metricName();
  }
  // Fallback for unmapped fields
  return String.format("frodo_sunspec_model_%d_%s", modelId, 
    fieldName.toLowerCase().replace("/", "_"));
}

private Map<String, String> buildTags(MetricsParameterEntity param, String fieldName) {
  Map<String, String> tags = new HashMap<>();
  tags.put("device_id", String.valueOf(param.config.device.id));
  tags.put("device_name", param.config.device.name);
  tags.put("model_id", String.valueOf(param.sunspecModelId));
  tags.put("field", extractBaseFieldName(fieldName));
  
  // Extract channel from field name (e.g., "module/1/DCA" -> channel="1")
  extractChannel(fieldName).ifPresent(ch -> tags.put("channel", ch));
  
  // Extract phase from field name (e.g., "AphA" -> phase="A")
  extractPhase(fieldName).ifPresent(ph -> tags.put("phase", ph));
  
  return tags;
}
```

#### C. Update MetricsParameterEntity

**Add fields**:
- `channel` (Integer, nullable) - for multi-channel metrics
- `phase` (String, nullable) - for phase-specific metrics

**Database migration**: Add columns to `FroMetricsParameter` table

#### D. Create Metrics Documentation API Endpoint

**File**: `src/main/java/at/or/reder/frodo/api/MetricsDocsResource.java`

```java
@Path("/api/metrics-docs")
@Tag(name = "Metrics Documentation")
public class MetricsDocsResource {
  
  @Inject MetricMetadataRegistry registry;
  
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<MetricMetadata> getAllMetrics() {
    return registry.getAll();
  }
  
  @GET
  @Path("/{metricName}")
  public MetricMetadata getMetric(@PathParam("metricName") String metricName) {
    return registry.getByMetricName(metricName)
      .orElseThrow(() -> new NotFoundException("Metric not found: " + metricName));
  }
  
  @GET
  @Path("/by-model/{modelId}")
  public List<MetricMetadata> getByModel(@PathParam("modelId") int modelId) {
    return registry.getAll().stream()
      .filter(m -> m.models().stream().anyMatch(mm -> mm.modelId() == modelId))
      .toList();
  }
}
```

### 4. Documentation Generation

#### A. Markdown Documentation

**Generate**: `docs/METRICS.md`

**Sections**:
1. **Overview** - metrics system architecture, naming conventions
2. **Metric Naming Schema** - explanation of tags, channel handling
3. **Available Metrics** - comprehensive table:
   - Metric name
   - Description
   - Unit
   - Type (gauge/counter)
   - Source models
   - Tags
   - Typical range
4. **Metrics by Category**:
   - AC Power & Energy
   - DC Power & Energy (per channel)
   - Voltage & Current
   - Temperature
   - Status & Control
   - Battery/Storage
5. **PromQL Query Examples**:
   - Total AC power across devices
   - Per-MPPT DC current
   - Energy production per day
   - Device availability
6. **Grafana Dashboard Snippets** - JSON panel examples

**Generator Script**: `scripts/generate-metrics-docs.js`

```javascript
// Reads metrics-metadata.json
// Generates docs/METRICS.md using template
// Includes:
// - Sortable tables
// - Category grouping
// - PromQL examples
// - Register reference
```

**Gradle Task**:
```groovy
task generateMetricsDocs(type: Exec) {
  commandLine 'node', 'scripts/generate-metrics-docs.js'
}

task generateMetricsMetadata(type: Exec) {
  commandLine 'node', 'scripts/generate-metrics-metadata.js'
}

processResources.dependsOn generateMetricsMetadata
build.dependsOn generateMetricsDocs
```

#### B. Web UI Integration

**New Page**: `/metrics-docs` route

**File**: `src/main/webui/src/pages/MetricsDocsPage.jsx`

**Features**:
1. **Search/Filter** - by metric name, model, category
2. **Metrics Table** - sortable, expandable rows
3. **Metric Detail Panel**:
   - Full description
   - Available on models (with links to SunSpec docs)
   - Tags and dimensions
   - Register information
   - Sample PromQL queries
   - Copy metric name button
4. **Category Tabs**: Power, Energy, Status, Control, Battery
5. **Export Button**: Download as CSV/JSON

**Components**:
- `MetricsDocsPage.jsx` - main page
- `MetricsTable.jsx` - searchable table
- `MetricDetailDrawer.jsx` - side panel for details
- `MetricCard.jsx` - compact metric card view
- `PromQLExample.jsx` - syntax-highlighted query examples

**API Hook**:
```javascript
// src/main/webui/src/hooks/useMetricsDocs.js
export function useMetricsDocs() {
  return useQuery({
    queryKey: ['metrics-docs'],
    queryFn: () => fetch('/api/metrics-docs').then(r => r.json())
  });
}
```

**Navigation**: Add "Metrics Documentation" link to main nav menu

### 5. Migration Strategy

#### Phase 1: Preparation (no breaking changes)
1. Generate metadata from refdoc JSON
2. Create metadata registry
3. Implement new metric naming in parallel (disabled by default)
4. Add documentation endpoint
5. Build UI documentation page

#### Phase 2: Migration (breaking change)
1. Enable new metric naming schema
2. Remove old metric name generation code
3. Update default Grafana dashboards
4. Document migration in CHANGELOG
5. Provide migration guide with regex patterns for dashboard updates

#### Migration Guide for Grafana Dashboards

**Before**:
```promql
frodo_sunspec_113_w{device_id="1"}
```

**After**:
```promql
frodo_sunspec_ac_power_watts{device_id="1", model_id="113"}
```

**Bulk Update Pattern**:
```
Find: frodo_sunspec_(\d+)_([a-z_]+)
Replace: frodo_sunspec_$2{model_id="$1"}
```

### 6. Testing Strategy

#### Unit Tests

**File**: `MetricMetadataRegistryTest.java`
- Test loading metadata from JSON
- Test lookup by metric name
- Test lookup by model ID + field name
- Test multi-channel field parsing

**File**: `MetricsScrapingServiceTest.java` (updated)
- Test new metric name generation
- Test channel extraction from field names
- Test phase extraction
- Test tag building
- Test backward compatibility fallback

#### Integration Tests

**File**: `MetricsDocsResourceTest.java`
- Test GET /api/metrics-docs
- Test GET /api/metrics-docs/{name}
- Test GET /api/metrics-docs/by-model/{id}

**File**: `MetricsPrometheusTest.java` (new)
- Scrape /q/metrics endpoint
- Verify new metric naming
- Verify tags present
- Verify multi-channel metrics separated

## Implementation Checklist

### Backend Tasks

- [ ] **Create metadata schema and model classes**
  - [ ] `MetricMetadata.java` record
  - [ ] `MetricMetadataRegistry.java` service
  - [ ] Unit tests for registry

- [ ] **Generate metadata from refdoc**
  - [ ] Create `scripts/generate-metrics-metadata.js`
  - [ ] Create `src/main/resources/metrics-semantic-mapping.json`
  - [ ] Gradle task `generateMetricsMetadata`
  - [ ] Verify metadata output

- [ ] **Update metrics scraping service**
  - [ ] Inject `MetricMetadataRegistry`
  - [ ] Implement `buildMetricName()` with semantic mapping
  - [ ] Implement `extractChannel()` helper
  - [ ] Implement `extractPhase()` helper
  - [ ] Update `registerGauges()` to use new tags
  - [ ] Update gauge descriptions with metadata
  - [ ] Unit tests for new naming logic

- [ ] **Database schema updates**
  - [ ] Add `channel` column to `FroMetricsParameter`
  - [ ] Add `phase` column to `FroMetricsParameter`
  - [ ] Liquibase changelog
  - [ ] Update entity class

- [ ] **Create documentation API endpoint**
  - [ ] `MetricsDocsResource.java`
  - [ ] DTOs for response (if needed)
  - [ ] OpenAPI annotations
  - [ ] Integration tests

### Documentation Tasks

- [ ] **Generate markdown documentation**
  - [ ] Create `scripts/generate-metrics-docs.js`
  - [ ] Create doc template
  - [ ] Generate `docs/METRICS.md`
  - [ ] Gradle task `generateMetricsDocs`
  - [ ] Include PromQL examples
  - [ ] Include Grafana snippets

- [ ] **Create migration guide**
  - [ ] Document breaking changes
  - [ ] Provide regex patterns for dashboard updates
  - [ ] Example before/after queries
  - [ ] Add to `docs/METRICS.md`

### Frontend Tasks

- [ ] **Create metrics documentation page**
  - [ ] `MetricsDocsPage.jsx`
  - [ ] `MetricsTable.jsx` (searchable, sortable)
  - [ ] `MetricDetailDrawer.jsx` (detail panel)
  - [ ] `MetricCard.jsx` (card view)
  - [ ] `PromQLExample.jsx` (syntax highlighting)
  - [ ] CSS/styling

- [ ] **API integration**
  - [ ] `useMetricsDocs.js` hook
  - [ ] Error handling
  - [ ] Loading states

- [ ] **Navigation updates**
  - [ ] Add "Metrics Documentation" to nav menu
  - [ ] Route configuration

### Testing Tasks

- [ ] **Unit tests**
  - [ ] `MetricMetadataRegistryTest.java`
  - [ ] `MetricsScrapingServiceTest.java` (updated)
  - [ ] Metadata generation script tests

- [ ] **Integration tests**
  - [ ] `MetricsDocsResourceTest.java`
  - [ ] `MetricsPrometheusTest.java`
  - [ ] Frontend component tests

### Deployment Tasks

- [ ] **Update build process**
  - [ ] Verify Gradle tasks run correctly
  - [ ] Update CI/CD pipeline if needed
  - [ ] Build and test Docker image

- [ ] **Update example dashboards**
  - [ ] Update Grafana dashboard JSON files
  - [ ] Test with new metric names
  - [ ] Provide in `docs/grafana/`

- [ ] **Documentation updates**
  - [ ] Update `README.md` with metrics info
  - [ ] Update `AGENTS.md` with new endpoints
  - [ ] CHANGELOG entry

## Estimated Effort

| Task Category | Estimated Time |
|---------------|----------------|
| Metadata generation scripts | 4 hours |
| Backend model & registry | 3 hours |
| MetricsScrapingService refactor | 4 hours |
| Database migration | 1 hour |
| Documentation API endpoint | 2 hours |
| Markdown doc generation | 3 hours |
| Frontend UI page | 6 hours |
| Testing | 4 hours |
| Integration & polish | 3 hours |
| **Total** | **~30 hours** |

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking change impacts existing Grafana dashboards | High | Provide comprehensive migration guide, regex patterns, updated dashboard examples |
| Metadata parsing errors from refdoc JSON | Medium | Extensive validation, fallback to manual mapping, comprehensive tests |
| Performance impact from metadata lookups | Low | Cache metadata in memory, use efficient lookup structures (HashMap) |
| Multi-channel metric explosion (cardinality) | Medium | Document cardinality implications, provide aggregation examples, limit channel count |
| Missing metadata for unmapped fields | Low | Fallback naming pattern, log warnings, allow custom metric names |

## Success Criteria

1. All metrics use semantic names without model ID in name
2. Multi-channel metrics (MPPT) properly tagged with `channel`
3. Comprehensive `docs/METRICS.md` generated automatically
4. Web UI displays searchable metrics documentation
5. Prometheus /q/metrics endpoint shows new naming schema
6. All tests pass (unit + integration)
7. Migration guide helps users update Grafana dashboards
8. No performance regression in scraping service

## Future Enhancements (Out of Scope)

- Automated Grafana dashboard generator from metadata
- Metrics metadata versioning (track changes over time)
- Alerting rule templates based on metric metadata
- Metrics comparison tool (compare devices, time ranges)
- Export metrics metadata to OpenMetrics format

---

**Status**: Awaiting review before implementation begins.
