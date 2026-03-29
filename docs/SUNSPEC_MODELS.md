# SunSpec Model Registry

This document describes the SunSpec protocol implementation in Frodo, including supported models, data types, register decoding, and the discovery process.

## Overview

[SunSpec](https://sunspec.org/) defines a standardized Modbus register map for solar and storage devices. Frodo implements the SunSpec client protocol for Fronius Gen24 PV inverters, supporting both **Float** and **Int+SF** (Integer with Scale Factor) register map variants.

**Protocol References:**
- Fronius Gen24 Register Maps: `refdoc/gen24-modbus-api-external-docs/`
  - Float models: `Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx`
  - Int+SF models: `Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_ROW.xlsx`
  - Storage Float: `Gen24_Primo_Symo_Storage_Register_Map_Float_ROW.xlsx`
  - Storage Int+SF: `Gen24_Primo_Symo_Storage_Register_Map_Int&SF_ROW.xlsx`

## Model Discovery

SunSpec discovery scans a Modbus device starting from a well-known base address, reading the model chain until an end marker is found.

### Discovery Process

1. **Locate SunSpec base**: Read 2 registers at address 40000 and verify the "SunS" signature (`0x53756e53`). If not found, try alternate addresses 0 and 50000.
2. **Read model header**: At base+2, read 2 registers to get the Model ID (uint16) and Model Length (uint16).
3. **Record model block**: Store the model ID, start address, and length.
4. **Advance pointer**: Move to the next model (current address + length + 2 header registers).
5. **Repeat**: Continue until End Block marker (model ID `0xFFFF`) or max scan depth (50 models).

### Caching

Discovery results are cached in memory per unit ID. Use `?refresh=true` on the discovery endpoint to force a new scan.

```
Base Address (40000)
  ├── [0-1]  "SunS" signature (0x53756e53)
  ├── [2-3]  Model 1 header: ID=1, Length=66
  ├── [4-69] Model 1 data (Common)
  ├── [70-71] Model 113 header: ID=113, Length=60
  ├── [72-131] Model 113 data (Inverter Float)
  ├── ...
  └── [N]    End Block: ID=0xFFFF, Length=0
```

## Supported Models

| Model ID | Name | Format | Description |
|----------|------|--------|-------------|
| 1 | Common | -- | Device identification (manufacturer, model, serial) |
| 101 | Inverter (Single Phase) | Int+SF | AC/DC measurements, operating state |
| 102 | Inverter (Split Phase) | Int+SF | AC/DC measurements, operating state |
| 103 | Inverter (Three Phase) | Int+SF | AC/DC measurements, operating state |
| 111 | Inverter (Single Phase) | Float | AC/DC measurements, operating state |
| 112 | Inverter (Split Phase) | Float | AC/DC measurements, operating state |
| 113 | Inverter (Three Phase) | Float | AC/DC measurements, operating state |
| 120 | Nameplate Ratings | Int+SF | Max power, VA, VAR, current ratings |
| 121 | Basic Settings | Int+SF | Power output limits, voltage, frequency |
| 122 | Extended Measurements & Status | Mixed | Lifetime energy, connection status, controls |
| 123 | Immediate Controls | Int+SF | Power limit, PF, VAR control (writable) |
| 124 | Basic Storage Controls | Int+SF | Charge/discharge settings (writable) |
| 160 | Multiple MPPT Extension | Int+SF | Per-string DC current, voltage, power |

### Inverter Model Auto-Detection

The `/sunspec/inverter` endpoint auto-detects which inverter model variant is available on the device:
- Checks Float models first (111, 112, 113)
- Falls back to Int+SF models (101, 102, 103)
- Returns the first match found in the device's model chain

## Data Types

SunSpec defines specific data types for register values. Each type occupies one or more 16-bit Modbus holding registers.

| Type | Registers | Signed | Description |
|------|-----------|--------|-------------|
| `uint16` | 1 | No | Unsigned 16-bit integer |
| `int16` | 1 | Yes | Signed 16-bit integer |
| `uint32` | 2 | No | Unsigned 32-bit integer (big-endian) |
| `int32` | 2 | Yes | Signed 32-bit integer (big-endian) |
| `float32` | 2 | -- | IEEE 754 single-precision float (big-endian) |
| `acc32` | 2 | No | Accumulated 32-bit unsigned counter |
| `acc64` | 4 | No | Accumulated 64-bit unsigned counter |
| `enum16` | 1 | No | 16-bit enumerated value |
| `enum32` | 2 | No | 32-bit enumerated value |
| `bitfield16` | 1 | No | 16-bit bitmask |
| `bitfield32` | 2 | No | 32-bit bitmask |
| `sunssf` | 1 | Yes | Scale factor: signed 16-bit exponent (base 10) |
| `string` | Variable | -- | UTF-8/ASCII string (2 bytes per register) |
| `pad` | 1 | -- | Padding register (ignored) |
| `count` | 1 | No | Repeating group count |

### Scale Factors (Int+SF Models)

In Int+SF format models, raw integer values are combined with scale factors to produce real values:

```
real_value = raw_value * 10^(scale_factor)
```

For example, if `W` (AC Power) raw value is `5000` and `W_SF` (scale factor) is `-1`:
```
AC Power = 5000 * 10^(-1) = 500.0 W
```

Float models store values directly as IEEE 754 floats and do not use scale factors.

### Not-Implemented Values

SunSpec defines special "not implemented" sentinel values per type:
- `uint16`: `0xFFFF` (65535)
- `int16`: `0x8000` (-32768)
- `uint32`: `0xFFFFFFFF`
- `int32`: `0x80000000`
- `float32`: `NaN`
- `acc32` / `acc64`: `0`
- `string`: empty/null

Frodo's decoder returns `null` for fields with not-implemented values.

## Model Field Reference

### Model 1: Common

Device identification fields. All fields are read-only strings.

| Field | Offset | Size | Type | Description |
|-------|--------|------|------|-------------|
| `Mn` | 0 | 16 | string | Manufacturer |
| `Md` | 16 | 16 | string | Device model |
| `Opt` | 32 | 8 | string | Options |
| `Vr` | 40 | 8 | string | Software version |
| `SN` | 48 | 16 | string | Serial number |
| `DA` | 64 | 1 | uint16 | Modbus device address |

### Models 111-113: Inverter (Float)

AC/DC electrical measurements and operating state. All fields are read-only.

| Field | Offset | Size | Type | Unit | Description |
|-------|--------|------|------|------|-------------|
| `A` | 0 | 2 | float32 | A | AC Total Current |
| `AphA` | 2 | 2 | float32 | A | Phase A Current |
| `AphB` | 4 | 2 | float32 | A | Phase B Current |
| `AphC` | 6 | 2 | float32 | A | Phase C Current |
| `PPVphAB` | 8 | 2 | float32 | V | Phase Voltage AB |
| `PPVphBC` | 10 | 2 | float32 | V | Phase Voltage BC |
| `PPVphCA` | 12 | 2 | float32 | V | Phase Voltage CA |
| `PhVphA` | 14 | 2 | float32 | V | Phase Voltage AN |
| `PhVphB` | 16 | 2 | float32 | V | Phase Voltage BN |
| `PhVphC` | 18 | 2 | float32 | V | Phase Voltage CN |
| `W` | 20 | 2 | float32 | W | AC Power |
| `Hz` | 22 | 2 | float32 | Hz | Line Frequency |
| `VA` | 24 | 2 | float32 | VA | AC Apparent Power |
| `VAr` | 26 | 2 | float32 | var | AC Reactive Power |
| `PF` | 28 | 2 | float32 | Pct | AC Power Factor |
| `WH` | 30 | 2 | float32 | Wh | AC Lifetime Energy |
| `DCA` | 32 | 2 | float32 | A | DC Current |
| `DCV` | 34 | 2 | float32 | V | DC Voltage |
| `DCW` | 36 | 2 | float32 | W | DC Power |
| `TmpCab` | 38 | 2 | float32 | C | Cabinet Temperature |
| `TmpSnk` | 40 | 2 | float32 | C | Heat Sink Temperature |
| `TmpTrns` | 42 | 2 | float32 | C | Transformer Temperature |
| `TmpOt` | 44 | 2 | float32 | C | Other Temperature |
| `St` | 46 | 1 | enum16 | -- | Operating State |
| `StVnd` | 47 | 1 | enum16 | -- | Vendor Operating State |
| `Evt1` | 48 | 2 | bitfield32 | -- | Event Flags |
| `Evt2` | 50 | 2 | bitfield32 | -- | Reserved Events |
| `EvtVnd1-4` | 52-58 | 2 ea. | bitfield32 | -- | Vendor Events |

### Models 101-103: Inverter (Int+SF)

Same measurements as Float models but using integer values with scale factors. Includes additional `*_SF` fields (sunssf type) for each measurement group. Total: 50 registers.

Key differences from Float models:
- Measurement fields use `uint16` / `int16` instead of `float32`
- Each measurement group has an associated `*_SF` scale factor field
- Example: `A` (uint16) + `A_SF` (sunssf) = AC Current in Amps

### Model 120: Nameplate Ratings

Maximum device ratings. All fields are read-only with scale factors.

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `DERTyp` | enum16 | -- | Type of DER device |
| `WRtg` | uint16 | W | Continuous power output capability |
| `VARtg` | uint16 | VA | Continuous VA capability |
| `VArRtgQ1-Q4` | int16 | var | Continuous VAR capability per quadrant |
| `ARtg` | uint16 | A | Maximum RMS AC current |
| `PFRtgQ1-Q4` | int16 | cos() | Minimum power factor per quadrant |
| `WHRtg` | uint16 | Wh | Nominal energy rating of storage |
| `AhrRtg` | uint16 | AH | Usable battery capacity |
| `MaxChaRte` | uint16 | W | Maximum charge rate |
| `MaxDisChaRte` | uint16 | W | Maximum discharge rate |

### Model 121: Basic Settings

Operational settings and limits. All fields are read-only with scale factors.

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `WMax` | uint16 | W | Maximum power output setting |
| `VRef` | uint16 | V | Voltage at the PCC |
| `VRefOfs` | int16 | V | Offset from PCC to inverter |
| `VMax` / `VMin` | uint16 | V | Voltage setpoint limits |
| `VAMax` | uint16 | VA | Maximum apparent power |
| `VArMaxQ1-Q4` | int16 | var | Reactive power limits per quadrant |
| `WGra` | uint16 | % WMax/sec | Default ramp rate |
| `PFMinQ1-Q4` | int16 | cos() | Minimum power factor per quadrant |
| `ECPNomHz` | uint16 | Hz | Nominal frequency at ECP |
| `ConnPh` | enum16 | -- | Connected phase identity |

### Model 122: Extended Measurements & Status

Lifetime energy accumulators and system status.

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `PVConn` | bitfield16 | -- | PV inverter present/available |
| `StorConn` | bitfield16 | -- | Storage inverter present/available |
| `ECPConn` | bitfield16 | -- | ECP connection status |
| `ActWh` | acc64 | Wh | Lifetime active energy output |
| `ActVAh` | acc64 | VAh | Lifetime apparent energy output |
| `ActVArhQ1-Q4` | acc64 | varh | Lifetime reactive energy per quadrant |
| `VArAval` | int16 | var | Available VARs |
| `WAval` | uint16 | W | Available Watts |
| `StSetLimMsk` | bitfield32 | -- | Setpoint limits reached |
| `StActCtl` | bitfield32 | -- | Active inverter controls |
| `TmSrc` | string | -- | Time sync source |
| `Tms` | uint32 | Secs | Seconds since 2000-01-01 UTC |
| `RtSt` | bitfield16 | -- | Ride-through status |
| `Ris` | uint16 | ohms | Isolation resistance |

### Model 123: Immediate Controls

Active power, power factor, and reactive power controls. **Writable fields** allow real-time inverter control (requires `frodo.modbus.write-enabled=true`).

| Field | R/W | Type | Unit | Description |
|-------|-----|------|------|-------------|
| `Conn_WinTms` | W | uint16 | Secs | Connect/disconnect time window |
| `Conn_RvrtTms` | W | uint16 | Secs | Connect/disconnect timeout |
| `Conn` | W | enum16 | -- | Connection control |
| `WMaxLimPct` | W | uint16 | % WMax | Power output limit |
| `WMaxLim_Ena` | W | enum16 | -- | Throttle enable/disable |
| `OutPFSet` | W | int16 | cos() | Fixed power factor |
| `OutPFSet_Ena` | W | enum16 | -- | Fixed PF enable/disable |
| `VArMaxPct` | W | int16 | % VArMax | Reactive power percent |
| `VArPct_Ena` | W | enum16 | -- | VAR percent limit enable |

### Model 124: Basic Storage Controls

Battery charge/discharge control. Mix of read-only status and **writable** control fields.

| Field | R/W | Type | Unit | Description |
|-------|-----|------|------|-------------|
| `WChaMax` | R | uint16 | W | Maximum charge setpoint |
| `WChaGra` | R | uint16 | % WChaMax/sec | Maximum charging rate |
| `WDisChaGra` | R | uint16 | % WChaMax/sec | Maximum discharge rate |
| `StorCtl_Mod` | W | bitfield16 | -- | Storage control mode |
| `VAChaMax` | W | uint16 | VA | Maximum charging VA |
| `MinRsvPct` | W | uint16 | % WChaMax | Minimum reserve percentage |
| `ChaState` | R | uint16 | % AhrRtg | Current charge state |
| `StorAval` | R | uint16 | AH | Available storage capacity |
| `InBatV` | R | uint16 | V | Internal battery voltage |
| `ChaSt` | R | enum16 | -- | Charge status |
| `OutWRte` | W | int16 | % WChaMax | Discharge rate setting |
| `InWRte` | W | int16 | % WChaMax | Charge rate setting |
| `ChaGriSet` | W | enum16 | -- | Grid charging setting |

### Model 160: Multiple MPPT Extension

Per-string DC measurements. Contains a fixed header with scale factors followed by repeating module blocks (2 modules supported).

**Header Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `DCA_SF` | sunssf | Current scale factor |
| `DCV_SF` | sunssf | Voltage scale factor |
| `DCW_SF` | sunssf | Power scale factor |
| `DCWH_SF` | sunssf | Energy scale factor |
| `Evt` | bitfield32 | Global events |
| `N` | count | Number of modules |
| `TmsPer` | uint16 | Timestamp period |

**Per-Module Fields** (repeated for each MPPT string):

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `ID` | uint16 | -- | Input ID |
| `IDStr` | string | -- | Input ID string |
| `DCA` | uint16 | A | DC Current |
| `DCV` | uint16 | V | DC Voltage |
| `DCW` | uint16 | W | DC Power |
| `DCWH` | acc32 | Wh | Lifetime Energy |
| `Tms` | uint32 | Secs | Timestamp |
| `Tmp` | int16 | C | Temperature |
| `DCSt` | enum16 | -- | Operating State |
| `DCEvt` | bitfield32 | -- | Module Events |

## Register Decoding Pipeline

Raw Modbus registers are decoded through a multi-stage pipeline:

```
Modbus FC 0x03 (Read Holding Registers)
    │
    ▼
Raw byte[] response (2 bytes per register)
    │
    ▼
SunSpecRegisterDecoder
    ├── Extracts typed values based on SunSpecDataType
    ├── Handles big-endian byte ordering
    ├── Detects not-implemented sentinel values → null
    └── Applies scale factors (Int+SF models)
    │
    ▼
SunSpecModelDataDecoder
    ├── Iterates over SunSpecFieldDefinition list
    ├── Decodes each field using SunSpecRegisterDecoder
    ├── Resolves scale factor references
    └── Builds SunSpecModelData (field name → typed value map)
    │
    ▼
SunSpecModelData
    ├── modelId, modelName
    ├── fields: Map<String, Object>  (field name → decoded value)
    └── scaledFields: Map<String, Double>  (scaled numeric values)
```

### Example: Raw Registers to Decoded Data

Given raw Modbus registers for Inverter Float model (113) at address 40072:

```
Register 40072-40073 (A):    0x4248_0000  → float32 = 50.0 A
Register 40092-40093 (W):    0xC5FA_0000  → float32 = -8000.0 W
Register 40094-40095 (Hz):   0x4248_0000  → float32 = 50.0 Hz
Register 40102-40103 (WH):   0x49742400   → float32 = 1000000.0 Wh
```

Decoded SunSpecModelData:
```json
{
  "modelId": 113,
  "modelName": "Inverter (Three Phase, Float)",
  "fields": {
    "A": 50.0,
    "W": -8000.0,
    "Hz": 50.0,
    "WH": 1000000.0,
    "St": 4,
    "DCA": 12.5,
    "DCV": 640.0,
    "DCW": 8000.0
  }
}
```

## Source Files

| File | Description |
|------|-------------|
| `SunSpecService.java` | High-level service: discovery, model reading, caching |
| `SunSpecModelRegistry.java` | Static registry of all model field definitions |
| `SunSpecModelDataDecoder.java` | Decodes raw registers into SunSpecModelData |
| `SunSpecRegisterDecoder.java` | Low-level register-to-value type decoder |
| `SunSpecConstants.java` | Model IDs, base addresses, signature constants |
| `SunSpecDataType.java` | Enum of all SunSpec data types with metadata |
| `SunSpecDiscoveryResult.java` | Discovery result record (model chain + timestamp) |
| `SunSpecModelBlock.java` | Record: model location (address, length) in register space |
| `SunSpecModelData.java` | Record: decoded model data (field name to value map) |
| `SunSpecModelDefinition.java` | Record: model metadata (ID, name, field list) |
| `SunSpecFieldDefinition.java` | Record: field metadata (name, offset, size, type, unit, scale factor) |
