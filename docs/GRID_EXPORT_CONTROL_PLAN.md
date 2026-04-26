# Grid Export Control Plan

## Motivation

Grid export control enables temporarily preventing energy feed-in to the grid by limiting
inverter output power to 0 % via SunSpec Model 123 (Immediate Controls). The primary use
case is **suppressing grid export when electricity spot prices are negative**, avoiding
costs or penalties associated with exporting power at those times.

---

## Overview

The feature consists of three layers:

| Layer | Change |
|-------|--------|
| Backend – Service | New `SunSpecService.setPowerLimit()` convenience method |
| Backend – REST | New `POST /api/devices/{id}/sunspec/controls/power-limit` endpoint |
| Frontend – API | New `sunspecApi.setPowerLimit()` call |
| Frontend – Hooks | New `useSunSpecControls()` query + `useSetPowerLimit()` mutation |
| Frontend – UI | "Grid Export" toggle switch in `GridStatusCard` |

---

## SunSpec Background: How Power Limiting Works

**Model 123 (Immediate Controls)** contains the registers to throttle inverter output:

| Field | Offset | Type | Access | Description |
|-------|--------|------|--------|-------------|
| `WMaxLimPct` | 3 | uint16 | W | Power output limit as % of WMax (scaled by `WMaxLimPct_SF`) |
| `WMaxLimPct_WinTms` | 4 | uint16 | W | Time window before change takes effect (secs, optional) |
| `WMaxLimPct_RvrtTms` | 5 | uint16 | W | Timeout after which the limit reverts (secs, 0=no timeout) |
| `WMaxLimPct_RmpTms` | 6 | uint16 | W | Ramp time for smooth transitions (secs, 0=immediate) |
| `WMaxLim_Ena` | 7 | enum16 | W | Throttle enable: `0` = limit inactive, `1` = limit active |
| `WMaxLimPct_SF` | 21 | sunssf | R | Scale factor for `WMaxLimPct` (read-only, device-specific) |

**Register address calculation:**
```
dataStart = block.dataAddress()          // = block.address() + 2
WMaxLimPct_addr   = dataStart + 3
WMaxLimPct_RvrtTms_addr = dataStart + 5
WMaxLimPct_RmpTms_addr  = dataStart + 6
WMaxLim_Ena_addr  = dataStart + 7
WMaxLimPct_SF_addr = dataStart + 21
```

**Scaling:** `realValue = rawValue × 10^SF`, so `rawValue = percent × 10^(−SF)`.
Example: SF = −2, desired 50 % → `rawValue = 50 × 100 = 5000`.

**Write sequence to block export (Nulleinspeisung):**
1. Read Model 123 registers to get `WMaxLimPct_SF` value.
2. Compute `rawValue = 0 × 10^(−SF) = 0`.
3. Write `WMaxLimPct = 0`.
4. Write `WMaxLim_Ena = 1` (enable the limit).

**Write sequence to re-enable export:**
1. Write `WMaxLim_Ena = 0` (disable the limit; inverter returns to normal operation).

---

## Phase 1: Backend

### 1.1  New DTO: `PowerLimitRequest`

**File:** `src/main/java/at/or/reder/frodo/api/dto/PowerLimitRequest.java`

```java
package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for setting the inverter power output limit.
 *
 * @param limitPercent  power output limit in percent of WMax (0–100).
 *                      Only used when {@code enable} is {@code true}.
 * @param enable        {@code true} to apply the limit (WMaxLim_Ena = 1),
 *                      {@code false} to remove the limit (WMaxLim_Ena = 0).
 * @param rampSeconds   optional ramp time in seconds for smooth transitions
 *                      (0 or null = immediate).
 * @param revertSeconds optional timeout in seconds after which the device
 *                      automatically reverts (0 or null = no auto-revert).
 */
@Schema(description = "Request to set the inverter power output limit")
public record PowerLimitRequest(
  @Schema(description = "Power output limit in % of WMax (0–100)", minimum = "0", maximum = "100")
  int limitPercent,

  @Schema(description = "true = apply limit, false = remove limit")
  boolean enable,

  @Schema(description = "Ramp time in seconds (null or 0 = immediate)", nullable = true)
  Integer rampSeconds,

  @Schema(description = "Auto-revert timeout in seconds (null or 0 = no revert)", nullable = true)
  Integer revertSeconds
) {
  public PowerLimitRequest {
    if (limitPercent < 0 || limitPercent > 100) {
      throw new IllegalArgumentException("limitPercent must be 0–100, got " + limitPercent);
    }
  }
}
```

### 1.2  New Service Method: `SunSpecService.setPowerLimit()`

**File:** `src/main/java/at/or/reder/frodo/modbus/sunspec/SunSpecService.java`

Add the following public method after `writeMultipleRegisters()`:

```java
/**
 * Sets the inverter power output limit using SunSpec Model 123 (Immediate Controls).
 *
 * <p>When {@code enable} is {@code true}, the limit is activated by writing
 * {@code WMaxLimPct} (scaled) and then setting {@code WMaxLim_Ena = 1}.
 * When {@code enable} is {@code false}, {@code WMaxLim_Ena} is set to {@code 0},
 * disabling the throttle regardless of the percent value.</p>
 *
 * <p>Requires {@code frodo.modbus.write-enabled=true}.</p>
 *
 * @param address       target device address
 * @param limitPercent  power output limit in % of WMax (0–100)
 * @param enable        true = apply limit, false = remove limit
 * @param rampSeconds   ramp time in seconds (0 = immediate, null treated as 0)
 * @param revertSeconds auto-revert timeout in seconds (0 = no revert, null treated as 0)
 * @throws IllegalStateException  if write operations are disabled
 * @throws IllegalArgumentException if Model 123 is not found on the device
 * @throws IOException            if communication fails
 * @throws TimeoutException       if the request times out
 */
public void setPowerLimit(DeviceAddress address, int limitPercent, boolean enable,
                          int rampSeconds, int revertSeconds)
    throws IOException, TimeoutException {

  if (!writeEnabled) {
    throw new IllegalStateException(
        "Write operations are disabled. Set frodo.modbus.write-enabled=true to allow writes.");
  }

  SunSpecDiscoveryResult discovery = getOrDiscover(address);
  SunSpecModelBlock block = discovery.findModel(SunSpecConstants.MODEL_CONTROLS)
      .orElseThrow(() -> new IllegalArgumentException(
          "Model 123 (Immediate Controls) not found on " + address));

  int dataStart = block.dataAddress();  // block.address() + 2

  // Register addresses (offsets from SunSpecModelRegistry Model 123)
  int wMaxLimPctAddr   = dataStart + 3;
  int wMaxLimRvrtAddr  = dataStart + 5;
  int wMaxLimRmpAddr   = dataStart + 6;
  int wMaxLimEnaAddr   = dataStart + 7;
  int wMaxLimPctSfAddr = dataStart + 21;

  if (enable) {
    // Read scale factor (signed int16 / sunssf)
    int[] sfReg = modbusTcpService.readHoldingRegisters(address, wMaxLimPctSfAddr, 1);
    int sf = (short) sfReg[0];  // cast to short for sign extension

    // rawValue = limitPercent * 10^(-sf)
    int rawValue = (int) Math.round(limitPercent * Math.pow(10, -sf));

    LOG.infof("Setting power limit: device=%s, limitPct=%d, SF=%d, rawValue=%d, ramp=%ds, revert=%ds",
        address, limitPercent, sf, rawValue, rampSeconds, revertSeconds);

    // Write ramp and revert times first (optional fields)
    if (rampSeconds > 0) {
      modbusTcpService.writeSingleRegister(address, wMaxLimRmpAddr, rampSeconds);
    }
    if (revertSeconds > 0) {
      modbusTcpService.writeSingleRegister(address, wMaxLimRvrtAddr, revertSeconds);
    }

    // Write the percentage value, then enable the limit
    modbusTcpService.writeSingleRegister(address, wMaxLimPctAddr, rawValue);
    modbusTcpService.writeSingleRegister(address, wMaxLimEnaAddr, 1);

  } else {
    LOG.infof("Disabling power limit: device=%s", address);
    modbusTcpService.writeSingleRegister(address, wMaxLimEnaAddr, 0);
  }
}

/**
 * Convenience overload with no ramp or revert time (immediate, no auto-revert).
 */
public void setPowerLimit(DeviceAddress address, int limitPercent, boolean enable)
    throws IOException, TimeoutException {
  setPowerLimit(address, limitPercent, enable, 0, 0);
}
```

### 1.3  New REST Endpoint: `POST /devices/{id}/sunspec/controls/power-limit`

**File:** `src/main/java/at/or/reder/frodo/api/SunSpecResource.java`

Add import for `PowerLimitRequest`, `POST`, `Consumes`, `Response`, `jakarta.ws.rs.core.Response`.

Add the following endpoint method after `readControls()`:

```java
/**
 * Sets the inverter power output limit (WMaxLimPct / WMaxLim_Ena in Model 123).
 *
 * <p>Use this to temporarily block or reduce grid export.
 * Requires {@code frodo.modbus.write-enabled=true}.</p>
 *
 * @param id      device ID
 * @param request power limit parameters
 * @return 200 with updated controls data, 409 if writes disabled, 503 on connection failure
 */
@POST
@Path("/controls/power-limit")
@Consumes(MediaType.APPLICATION_JSON)
@Operation(
  summary = "Set inverter power output limit",
  description = "Controls grid export by writing WMaxLimPct and WMaxLim_Ena in "
    + "SunSpec Model 123 (Immediate Controls). Requires frodo.modbus.write-enabled=true. "
    + "Set enable=true and limitPercent=0 to block all grid export. "
    + "Set enable=false to remove the limit and restore normal operation."
)
@APIResponses({
  @APIResponse(responseCode = "200", description = "Limit applied; returns updated controls data",
    content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))),
  @APIResponse(responseCode = "400", description = "Invalid request (limitPercent out of range)",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @APIResponse(responseCode = "404", description = "Device not found or Model 123 not present",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @APIResponse(responseCode = "409", description = "Write operations are disabled",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  @APIResponse(responseCode = "503", description = "Device connection failed",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@Blocking
public SunSpecModelResponse setPowerLimit(
  @PathParam("id") Long id,
  @jakarta.validation.Valid PowerLimitRequest request
) {
  ModbusDeviceEntity device = requireDevice(id);
  DeviceAddress address = DeviceAddress.fromEntity(device);
  LOG.infof("Set power limit: device=%d, limitPct=%d, enable=%b, ramp=%s, revert=%s",
    id, request.limitPercent(), request.enable(),
    request.rampSeconds(), request.revertSeconds());

  try {
    int ramp   = request.rampSeconds()   != null ? request.rampSeconds()   : 0;
    int revert = request.revertSeconds() != null ? request.revertSeconds() : 0;
    sunSpecService.setPowerLimit(address, request.limitPercent(), request.enable(), ramp, revert);

    // Read back the updated controls and return them
    SunSpecModelData data = sunSpecService.readModel(address, SunSpecConstants.MODEL_CONTROLS);
    return SunSpecModelResponse.fromModelData(id, device.unitId, data);

  } catch (IllegalStateException ex) {
    // write-enabled = false  →  409 Conflict
    throw new jakarta.ws.rs.WebApplicationException(
      jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.CONFLICT)
        .entity(new ErrorResponse("WRITE_DISABLED", ex.getMessage()))
        .type(MediaType.APPLICATION_JSON)
        .build());
  } catch (IllegalArgumentException ex) {
    throw new DeviceNotFoundException(ex.getMessage());
  } catch (ModbusException ex) {
    throw new DeviceConnectionException("Failed to set power limit: " + ex.getMessage(), ex);
  } catch (IOException | TimeoutException ex) {
    throw new DeviceConnectionException("Failed to set power limit: " + ex.getMessage(), ex);
  }
}
```

### 1.4  Configuration

**File:** `src/main/resources/application.properties`

Add/confirm the following property (already present, but document it clearly):

```properties
# Set to true to enable Modbus write operations (FC 0x06 / FC 0x10).
# Required for power limit control via Model 123.
# WARNING: enabling writes allows REST API callers to modify inverter settings.
frodo.modbus.write-enabled=false
```

For development/testing with a real device, override in `application-dev.properties` (not committed):
```properties
%dev.frodo.modbus.write-enabled=true
```

---

## Phase 2: Frontend

### 2.1  API Service: `sunspecApi.setPowerLimit()`

**File:** `src/main/webui/src/services/sunspecApi.js`

Add after `getControls`:

```js
/**
 * Set inverter power output limit (SunSpec Model 123)
 * @param {number} deviceId - Device ID
 * @param {Object} params
 * @param {number} params.limitPercent - 0-100 percent of WMax
 * @param {boolean} params.enable - true = apply limit, false = remove limit
 * @param {number|null} [params.rampSeconds] - ramp time in seconds (optional)
 * @param {number|null} [params.revertSeconds] - auto-revert timeout (optional)
 * @returns {Promise<Object>} Updated controls model data
 */
setPowerLimit: async (deviceId, { limitPercent, enable, rampSeconds = null, revertSeconds = null }) => {
  const response = await apiClient.post(`/devices/${deviceId}/sunspec/controls/power-limit`, {
    limitPercent,
    enable,
    rampSeconds,
    revertSeconds,
  });
  return response.data;
},
```

### 2.2  Hooks: `useSunSpecControls` and `useSetPowerLimit`

**File:** `src/main/webui/src/hooks/useSunSpec.js`

Add the `controls` query key to `sunspecKeys`:
```js
controls: (deviceId) => [...sunspecKeys.all, 'controls', deviceId],
```

Add the two new hooks after `useSunSpecMppt`:

```js
/**
 * Hook to fetch Immediate Controls model data (Model 123).
 * Reads WMaxLimPct, WMaxLim_Ena, and scale factors.
 * Poll interval is intentionally slow — controls change rarely.
 */
export function useSunSpecControls(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.controls(deviceId),
    queryFn: () => sunspecApi.getControls(deviceId),
    enabled: !!deviceId,
    staleTime: 30 * 1000,           // 30 s
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: 60 * 1000,     // re-read every 60 s
    ...options,
  });
}

/**
 * Mutation hook to set the inverter power output limit.
 * On success: invalidates controls and status queries so the UI reflects new state.
 */
export function useSetPowerLimit() {
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useUiStore();  // import useUiStore at top of file

  return useMutation({
    mutationFn: ({ deviceId, limitPercent, enable, rampSeconds, revertSeconds }) =>
      sunspecApi.setPowerLimit(deviceId, { limitPercent, enable, rampSeconds, revertSeconds }),
    onSuccess: (_, { deviceId, enable, limitPercent }) => {
      queryClient.invalidateQueries({ queryKey: sunspecKeys.controls(deviceId) });
      queryClient.invalidateQueries({ queryKey: sunspecKeys.status(deviceId) });
      const msg = enable && limitPercent === 0
        ? 'Grid export blocked'
        : enable
          ? `Power limit set to ${limitPercent}%`
          : 'Grid export limit removed';
      showSuccess(msg);
    },
    onError: (error) => {
      const msg = error?.response?.data?.message || error?.message || 'Failed to set power limit';
      showError(msg);
    },
  });
}
```

Additional imports needed at the top of `useSunSpec.js`:
```js
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import useUiStore from '../stores/useUiStore';
```

### 2.3  `DeviceDashboard.jsx` — Detect Model 123 + Pass Props

**File:** `src/main/webui/src/components/dashboard/DeviceDashboard.jsx`

Add `hasControls` detection alongside `hasStorage` and `hasStatus`:

```js
const hasControls = useMemo(() => {
  if (!discovery?.models) return false;
  return discovery.models.some((m) => m.modelId === 123);
}, [discovery]);
```

Update the `GridStatusCard` usage to pass `deviceId` and `hasControls`:

```jsx
<GridStatusCard
  deviceId={deviceId}                        {/* NEW */}
  hasControls={hasControls}                  {/* NEW */}
  statusData={statusQuery.data}
  inverterData={inverterQuery.data}
  isLoading={(statusQuery.isLoading && hasStatus) || isDiscovering}
  isError={statusQuery.isError && hasStatus}
/>
```

Also include controls in `handleRefreshAll`:
```js
if (hasControls) {
  queryClient.invalidateQueries({ queryKey: sunspecKeys.controls(deviceId) });
}
```

### 2.4  `GridStatusCard.jsx` — Add Grid Export Toggle

**File:** `src/main/webui/src/components/dashboard/GridStatusCard.jsx`

#### New props accepted:

| Prop | Type | Description |
|------|------|-------------|
| `deviceId` | number | Device ID (required for controls query) |
| `hasControls` | boolean | Whether Model 123 is present on the device |

#### Logic:

```js
// Determine current export state from controls data
const controlsQuery = useSunSpecControls(deviceId, { enabled: !!deviceId && !!hasControls });
const wMaxLimEna  = controlsQuery.data?.fields?.WMaxLim_Ena;
const wMaxLimPct  = controlsQuery.data?.scaledFields?.WMaxLimPct;

// Grid export is considered "blocked" when the limit is enabled AND set to 0%
const isExportBlocked = wMaxLimEna === 1 && wMaxLimPct === 0;
const exportToggleChecked = !isExportBlocked;  // checked = export allowed

const setLimit = useSetPowerLimit();
const isTogglePending = setLimit.isPending;

const handleExportToggle = async (event) => {
  const allowExport = event.target.checked;
  await setLimit.mutateAsync({
    deviceId,
    limitPercent: allowExport ? 100 : 0,
    enable: !allowExport,   // enable=true blocks, enable=false removes limit
  });
};
```

#### UI placement:

Add the toggle to the card header `Stack`, alongside the existing status label:

```jsx
<Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
  <Stack direction="row" spacing={1} alignItems="center">
    <ElectricalServicesIcon
      sx={{ color: gridConnected ? 'success.main' : 'text.disabled' }}
    />
    <Typography variant="h6" sx={{ color: 'primary.main' }}>
      Grid
    </Typography>
  </Stack>

  <Stack direction="row" spacing={1} alignItems="center">
    <Typography variant="caption" sx={{ color: gridStatusColor, fontWeight: 600 }}>
      {gridStatusLabel}
    </Typography>

    {/* Grid Export Toggle — only shown when Model 123 is available */}
    {hasControls && (
      <Tooltip
        title={
          controlsQuery.isLoading
            ? 'Reading controls...'
            : exportToggleChecked
              ? 'Grid export allowed — click to block'
              : 'Grid export blocked — click to allow'
        }
      >
        <span>  {/* wrapper needed for disabled Tooltip */}
          <Switch
            size="small"
            checked={exportToggleChecked}
            onChange={handleExportToggle}
            disabled={isTogglePending || controlsQuery.isLoading || isError}
            color={exportToggleChecked ? 'success' : 'error'}
            inputProps={{ 'aria-label': 'Toggle grid export' }}
          />
        </span>
      </Tooltip>
    )}
  </Stack>
</Stack>
```

Additional MUI imports needed:
```js
import Switch from '@mui/material/Switch';
import Tooltip from '@mui/material/Tooltip';
```

Additional hook imports needed:
```js
import { useSunSpecControls, useSetPowerLimit } from '../../hooks/useSunSpec';
```

---

## Phase 3: Tests

### 3.1  Backend Unit Test: Scale Factor Calculation

**File:** `src/test/java/at/or/reder/frodo/modbus/sunspec/SunSpecServicePowerLimitTest.java`

Test cases:
- `setPowerLimit` with SF = 0: limitPercent=50 → rawValue=50
- `setPowerLimit` with SF = −2: limitPercent=50 → rawValue=5000
- `setPowerLimit` with SF = −2: limitPercent=0 → rawValue=0
- `setPowerLimit` with SF = −2: limitPercent=100 → rawValue=10000
- `setPowerLimit` with enable=false: only writes WMaxLim_Ena=0, no WMaxLimPct write
- `setPowerLimit` throws `IllegalStateException` when writeEnabled=false
- `setPowerLimit` throws `IllegalArgumentException` when Model 123 not in discovery result

Use a mock `ModbusTcpService` (Mockito or CDI `@InjectMock`).

### 3.2  Backend Integration Test: REST Endpoint

**File:** `src/test/java/at/or/reder/frodo/api/SunSpecControlsResourceTest.java`

Test cases (QuarkusTest + RestAssured):
- `POST /api/devices/1/sunspec/controls/power-limit` with valid body and write-enabled=true → 200
- Same with write-enabled=false → 409
- Same with limitPercent=−1 → 400
- Same with limitPercent=101 → 400
- Same with non-existent deviceId → 404

---

## Phase 4: Documentation Updates

### 4.1  `AGENTS.md` — Key Endpoints Table

Add:

```
| `POST /api/devices/{id}/sunspec/controls/power-limit` | Set power output limit (block/allow grid export) |
```

### 4.2  `docs/SUNSPEC_MODELS.md` — New Section

Add a "Write Operations" section documenting:
- The `POST .../controls/power-limit` endpoint
- Configuration requirement (`frodo.modbus.write-enabled=true`)
- Example `curl` commands for blocking and re-enabling grid export

Example curl commands:
```bash
# Block grid export (limit to 0%)
curl -X POST http://localhost:8080/api/devices/1/sunspec/controls/power-limit \
  -H 'Content-Type: application/json' \
  -d '{"limitPercent":0,"enable":true}'

# Remove limit (restore normal operation)
curl -X POST http://localhost:8080/api/devices/1/sunspec/controls/power-limit \
  -H 'Content-Type: application/json' \
  -d '{"limitPercent":0,"enable":false}'

# Limit to 50% with 30-second ramp
curl -X POST http://localhost:8080/api/devices/1/sunspec/controls/power-limit \
  -H 'Content-Type: application/json' \
  -d '{"limitPercent":50,"enable":true,"rampSeconds":30}'
```

---

## Implementation Order

```
1. PowerLimitRequest DTO
2. SunSpecService.setPowerLimit() (with unit tests)
3. SunSpecResource POST endpoint (with integration tests)
4. sunspecApi.setPowerLimit()
5. useSunSpec.js — controls query key + useSunSpecControls + useSetPowerLimit
6. DeviceDashboard.jsx — hasControls + pass deviceId
7. GridStatusCard.jsx — toggle switch
8. Documentation updates (AGENTS.md, SUNSPEC_MODELS.md)
```

---

## Open Questions / Review Points

1. **Confirmation dialog**: Should toggling OFF (blocking export) show a confirmation dialog?
   Currently planned as a direct toggle without confirmation. If the feature is used in
   automated price-driven logic this is fine; for manual use a confirm step may be safer.

2. **Write-enabled UI feedback**: The toggle should be visually disabled (or hidden) when
   `frodo.modbus.write-enabled=false`. Consider adding a `/api/info` or `/api/capabilities`
   endpoint that exposes whether writes are enabled, so the UI can adapt without attempting
   a write first.

3. **Re-enable strategy**: On re-enable, the plan writes `WMaxLim_Ena = 0` (disable throttle).
   An alternative is `WMaxLimPct = 100, WMaxLim_Ena = 1` (keep throttle on but at 100%).
   The current plan uses the cleaner `WMaxLim_Ena = 0` approach.

4. **Revert timeout**: For automated price-driven control, `revertSeconds` could act as a
   safety net — e.g. `revertSeconds = 3600` causes the device to automatically restore
   export after 1 hour if Frodo crashes or loses connectivity.

5. **Partial limit**: The API supports any 0–100 % value. The UI toggle is binary
   (allow/block). A future slider could expose partial limits without additional backend work.

6. **MQTT integration**: Consider publishing the current power limit state to MQTT so
   external systems can observe the current mode without polling.
