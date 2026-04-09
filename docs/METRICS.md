# Frodo SunSpec Metrics Reference

This document is the authoritative reference for all Prometheus metrics exported by Frodo's SunSpec metrics scraping system. It is generated from the semantic mapping in `src/main/resources/metrics-semantic-mapping.json`.

## Naming Conventions

All metrics follow the pattern:

```
frodo_sunspec_{semantic_name}_{base_unit}
```

- **`frodo_sunspec_`** -- fixed prefix identifying the application and subsystem
- **`{semantic_name}`** -- human-readable description of the measurement (e.g., `ac_power`, `dc_current`, `battery_voltage`)
- **`{base_unit}`** -- ISO base unit suffix (e.g., `watts`, `amperes`, `volts`, `celsius`)
- Metrics without a physical unit (state enums, status codes) omit the unit suffix

### Tags (Labels)

Every metric carries at least these base tags:

| Tag | Description |
|-----|-------------|
| `device_id` | Modbus device ID |
| `device_name` | Device friendly name |
| `model_id` | SunSpec model ID that sourced this reading |
| `field` | SunSpec field name |

Additional dimensional tags are added where applicable:

| Tag | When Used | Values |
|-----|-----------|--------|
| `phase` | Per-phase AC metrics | `A`, `B`, `C` |
| `line` | Line-to-line voltage | `AB`, `BC`, `CA` |
| `channel` | Per-MPPT-string DC metrics | `1`, `2`, ... |
| `location` | Temperature sensor position | `cabinet`, `heatsink`, `transformer`, `other` |
| `quadrant` | Reactive power/energy quadrants | `1`, `2`, `3`, `4` |

### Prometheus Tag Key Constraint

All meters registered under the same metric name **must** have the same set of tag keys. When a measurement has variants with different tag key sets (e.g., inverter-aggregate DC current with no `channel` vs per-MPPT DC current with `channel`), distinct metric names are used:

- `frodo_sunspec_dc_current_amperes` -- aggregate, no `channel` tag
- `frodo_sunspec_mppt_dc_current_amperes` -- per-string, with `channel` tag

### Unit Suffixes

| Physical Quantity | Unit Suffix | Symbol |
|-------------------|-------------|--------|
| Power | `_watts` | W |
| Apparent power | `_voltamperes` | VA |
| Reactive power | `_vars` | var |
| Energy | `_watt_hours` | Wh |
| Apparent energy | `_voltampere_hours` | VAh |
| Reactive energy | `_var_hours` | varh |
| Current | `_amperes` | A |
| Voltage | `_volts` | V |
| Frequency | `_hertz` | Hz |
| Temperature | `_celsius` | C |
| Ratio / percentage | `_ratio` | -- |
| Resistance | `_ohms` | ohm |
| Capacity | `_ampere_hours` | Ah |
| Time | `_seconds` | s |

---

## Metrics by Category

### AC Power & Energy

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_ac_power_watts` | W | AC power output from the inverter. Positive values indicate power generation, negative values indicate consumption. | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_apparent_power_voltamperes` | VA | AC apparent power (VA) from the inverter. | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_reactive_power_vars` | var | AC reactive power (var) from the inverter. | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_power_factor_ratio` | -- | AC power factor as a ratio (0.0 to 1.0). Indicates the phase angle between voltage and current. | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_frequency_hertz` | Hz | AC line frequency. | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_energy_total_watt_hours` | Wh | Cumulative AC energy production since inverter installation (lifetime energy). | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_available_power_watts` | W | Available watts (current generation capacity based on conditions). | -- | 122 |
| `frodo_sunspec_available_reactive_power_vars` | var | Available reactive power (VARs). | -- | 122 |

### AC Current

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_ac_current_amperes` | A | Total AC current from the inverter (sum of all phases). | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_phase_current_amperes` | A | AC current per phase. | `phase` (A, B, C) | 101, 102, 103, 111, 112, 113 |

### AC Voltage

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_ac_voltage_line_volts` | V | AC line-to-line (phase-to-phase) voltage. | `line` (AB, BC, CA) | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_ac_voltage_phase_volts` | V | AC phase-to-neutral voltage. | `phase` (A, B, C) | 101, 102, 103, 111, 112, 113 |

### DC Power & Energy (Inverter Aggregate)

These metrics report the aggregate DC side values from inverter models (no channel dimension).

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_dc_current_amperes` | A | Total DC current from the PV array (aggregate from inverter model). | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_dc_voltage_volts` | V | Total DC voltage from the PV array (aggregate from inverter model). | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_dc_power_watts` | W | Total DC power from the PV array (aggregate from inverter model). | -- | 101, 102, 103, 111, 112, 113 |

### DC Power & Energy (MPPT Per-String)

These metrics report per-MPPT-string values from model 160. Use the `channel` tag to distinguish strings.

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_mppt_dc_current_amperes` | A | DC current per MPPT string. | `channel` (1, 2) | 160 |
| `frodo_sunspec_mppt_dc_voltage_volts` | V | DC voltage per MPPT string. | `channel` (1, 2) | 160 |
| `frodo_sunspec_mppt_dc_power_watts` | W | DC power per MPPT string. | `channel` (1, 2) | 160 |
| `frodo_sunspec_dc_energy_total_watt_hours` | Wh | Cumulative DC energy production per MPPT string (lifetime energy). | `channel` (1, 2) | 160 |

### Temperature

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_temperature_celsius` | C | Inverter temperature readings. | `location` (cabinet, heatsink, transformer, other) | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_mppt_temperature_celsius` | C | MPPT string temperature per channel. | `channel` (1, 2) | 160 |

### Status

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_operating_state` | -- | Inverter operating state. Values: 1=Off, 2=Sleeping, 3=Starting, 4=Running (MPPT), 5=Throttled, 6=Shutting Down, 7=Fault, 8=Standby. | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_vendor_state` | -- | Vendor-specific operating state code (Fronius-defined). | -- | 101, 102, 103, 111, 112, 113 |
| `frodo_sunspec_dc_operating_state` | -- | DC MPPT string operating state per channel. | `channel` (1, 2) | 160 |
| `frodo_sunspec_isolation_resistance_ohms` | ohm | Isolation resistance measurement. | -- | 122 |

### Nameplate Ratings (Model 120)

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_rating_power_watts` | W | Continuous power output capability of the inverter (nameplate rating). | -- | 120 |
| `frodo_sunspec_rating_apparent_power_voltamperes` | VA | Continuous VA capability of the inverter (nameplate rating). | -- | 120 |
| `frodo_sunspec_rating_reactive_power_vars` | var | Continuous reactive power (VAR) capability per quadrant. | `quadrant` (1, 2, 3, 4) | 120 |
| `frodo_sunspec_rating_current_amperes` | A | Maximum RMS AC current level (nameplate rating). | -- | 120 |
| `frodo_sunspec_rating_energy_watt_hours` | Wh | Nominal energy rating of storage (nameplate rating). | -- | 120 |
| `frodo_sunspec_rating_max_charge_power_watts` | W | Maximum charge rate (nameplate rating). | -- | 120 |
| `frodo_sunspec_rating_max_discharge_power_watts` | W | Maximum discharge rate (nameplate rating). | -- | 120 |
| `frodo_sunspec_rating_capacity_ampere_hours` | Ah | Usable battery capacity (nameplate rating). | -- | 120 |

### Settings (Model 121)

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_setting_max_power_watts` | W | Maximum power output setting. | -- | 121 |
| `frodo_sunspec_setting_voltage_reference_volts` | V | Voltage at the point of common coupling (PCC). | -- | 121 |
| `frodo_sunspec_setting_max_apparent_power_voltamperes` | VA | Maximum apparent power setpoint. | -- | 121 |
| `frodo_sunspec_setting_nominal_frequency_hertz` | Hz | Nominal frequency at the ECP (Electrical Connection Point). | -- | 121 |

### Lifetime Energy (Model 122)

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_lifetime_energy_active_watt_hours` | Wh | Lifetime active energy output (accumulated Wh since installation). | -- | 122 |
| `frodo_sunspec_lifetime_energy_apparent_voltampere_hours` | VAh | Lifetime apparent energy output (accumulated VAh since installation). | -- | 122 |
| `frodo_sunspec_lifetime_energy_reactive_var_hours` | varh | Lifetime reactive energy output per quadrant (accumulated varh since installation). | `quadrant` (1, 2, 3, 4) | 122 |

### Controls (Model 123)

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_control_power_limit_ratio` | -- | Power output limit as a ratio of WMax (0.0 to 1.0). Writable control parameter. | -- | 123 |
| `frodo_sunspec_control_power_factor_ratio` | -- | Fixed power factor setting. Writable control parameter. | -- | 123 |
| `frodo_sunspec_control_connection_state` | -- | Connection control state. Values: 0=Disconnect, 1=Connect. | -- | 123 |

### Battery / Storage (Model 124)

| Metric Name | Unit | Description | Tags | Models |
|-------------|------|-------------|------|--------|
| `frodo_sunspec_battery_charge_state_ratio` | -- | Battery state of charge as a ratio (0.0 = empty, 1.0 = full). | -- | 124 |
| `frodo_sunspec_battery_voltage_volts` | V | Internal battery voltage. | -- | 124 |
| `frodo_sunspec_battery_charge_status` | -- | Battery charge status. Values: 1=Off, 2=Empty, 3=Discharging, 4=Charging, 5=Full, 6=Holding, 7=Testing. | -- | 124 |
| `frodo_sunspec_battery_available_capacity_ampere_hours` | Ah | Available storage capacity in ampere-hours. | -- | 124 |
| `frodo_sunspec_battery_max_charge_power_watts` | W | Maximum charge setpoint. | -- | 124 |

---

## PromQL Query Examples

### Total AC power across all devices

```promql
sum(frodo_sunspec_ac_power_watts)
```

### AC power for a specific device

```promql
frodo_sunspec_ac_power_watts{device_name="Inverter01"}
```

### Per-phase AC current

```promql
frodo_sunspec_ac_phase_current_amperes{device_name="Inverter01", phase="A"}
```

### All phases at once

```promql
frodo_sunspec_ac_phase_current_amperes{device_name="Inverter01"}
```

### Per-MPPT-string DC power

```promql
frodo_sunspec_mppt_dc_power_watts{device_name="Inverter01"}
```

This returns one time series per channel (channel="1", channel="2").

### Compare MPPT string 1 vs string 2

```promql
frodo_sunspec_mppt_dc_current_amperes{device_name="Inverter01", channel="1"}
/
frodo_sunspec_mppt_dc_current_amperes{device_name="Inverter01", channel="2"}
```

### Daily energy production (rate over 24h)

```promql
increase(frodo_sunspec_ac_energy_total_watt_hours{device_name="Inverter01"}[24h])
```

### Battery state of charge

```promql
frodo_sunspec_battery_charge_state_ratio{device_name="Inverter01"} * 100
```

### Device availability (is inverter running?)

```promql
frodo_sunspec_operating_state{device_name="Inverter01"} == 4
```

### Temperature monitoring with alert threshold

```promql
frodo_sunspec_temperature_celsius{location="heatsink"} > 85
```

---

## SunSpec Model Reference

| Model ID | Name | Description |
|----------|------|-------------|
| 1 | Common | Device identification (manufacturer, model, serial) |
| 101 | Inverter (Single Phase, Int+SF) | Single-phase inverter with integer + scale factor encoding |
| 102 | Inverter (Split Phase, Int+SF) | Split-phase inverter with integer + scale factor encoding |
| 103 | Inverter (Three Phase, Int+SF) | Three-phase inverter with integer + scale factor encoding |
| 111 | Inverter (Single Phase, Float) | Single-phase inverter with float encoding |
| 112 | Inverter (Split Phase, Float) | Split-phase inverter with float encoding |
| 113 | Inverter (Three Phase, Float) | Three-phase inverter with float encoding |
| 120 | Nameplate | Inverter nameplate ratings |
| 121 | Settings | Basic settings |
| 122 | Extended Measurements & Status | Lifetime energy, available power, isolation |
| 123 | Immediate Controls | Power limit, power factor, connection control |
| 124 | Basic Storage Controls | Battery state, charge control |
| 160 | Multiple MPPT Extension | Per-string DC measurements for multi-tracker inverters |

---

## Migration from Legacy Naming

The previous naming scheme embedded the model ID in the metric name:

```
# Old format
frodo_sunspec_113_w{device_id="1", model_id="113", field="W"}

# New format
frodo_sunspec_ac_power_watts{device_id="1", model_id="113", field="W"}
```

### Grafana Dashboard Update Guide

For bulk-updating Grafana dashboards, use these patterns:

| Old Pattern | New Metric |
|-------------|------------|
| `frodo_sunspec_*_w` | `frodo_sunspec_ac_power_watts` |
| `frodo_sunspec_*_a` | `frodo_sunspec_ac_current_amperes` |
| `frodo_sunspec_*_apha` | `frodo_sunspec_ac_phase_current_amperes{phase="A"}` |
| `frodo_sunspec_*_hz` | `frodo_sunspec_ac_frequency_hertz` |
| `frodo_sunspec_*_dca` | `frodo_sunspec_dc_current_amperes` |
| `frodo_sunspec_*_dcv` | `frodo_sunspec_dc_voltage_volts` |
| `frodo_sunspec_*_dcw` | `frodo_sunspec_dc_power_watts` |
| `frodo_sunspec_160_module_1_dca` | `frodo_sunspec_mppt_dc_current_amperes{channel="1"}` |
| `frodo_sunspec_*_tmpcab` | `frodo_sunspec_temperature_celsius{location="cabinet"}` |
| `frodo_sunspec_*_wh` | `frodo_sunspec_ac_energy_total_watt_hours` |

---

## API Endpoint

The full metrics mapping is available as a REST API:

```
GET /api/metrics-docs
```

Returns all metric definitions as JSON, including field mappings, tag definitions, and descriptions. This endpoint powers the Metrics Documentation page in the web UI.

---

## Source of Truth

The canonical source for all metric definitions is:

```
src/main/resources/metrics-semantic-mapping.json
```

This file is loaded at application startup by `MetricMetadataRegistry` and drives:
- Prometheus metric name resolution
- Tag assignment (phase, channel, location, quadrant, line)
- The `/api/metrics-docs` REST endpoint
- The web UI Metrics Documentation page
- This document
