# Metrics Aggregation Modes

## Overview

Each SunSpec parameter can be stored with its own aggregation mode, controlling how
scraped values are reduced before writing to the database. Choosing a coarser mode for
slow-changing or low-priority metrics can cut disk usage by **60–1,440×** compared to the
default 1-minute average.

Prometheus gauges are always updated on every scrape regardless of the chosen mode.

---

## Modes

| Mode | Window | Aggregation | ~Rows/year* | Use Case |
|---|---|---|---|---|
| `MINUTE_AVERAGE` | 1 min | Arithmetic mean | 525,600 | Default; high-frequency analysis |
| `MINUTE_CURRENT` | 1 min | First value in window | 525,600 | State/status fields |
| `MINUTE_DIFF` | 1 min | Last − previous minute | 525,600 | Incremental counters (Wh, pulses) |
| `HOUR_AVERAGE` | 1 hour | Arithmetic mean | 8,760 | Trend analysis; slow-changing values |
| `HOUR_CURRENT` | 1 hour | First value at window start | 8,760 | Hourly snapshots |
| `HOUR_DIFF` | 1 hour | Last − previous hour | 8,760 | Hourly energy deltas |
| `DAY_AVERAGE` | 1 day | Arithmetic mean | 365 | Long-term trends |
| `DAY_CURRENT` | 1 day | First value at 00:00 UTC | 365 | Daily snapshots |
| `DAY_DIFF` | 1 day | Last − previous day | 365 | Daily energy deltas |

\* Estimated at 30 s scrape interval, 1 year retention, 1 parameter.

---

## Window Boundaries

All windows are aligned to **UTC clock boundaries**:

- **Minute windows** — 00:00, 00:01, 00:02, … (truncated to `ChronoUnit.MINUTES`)
- **Hour windows** — 00:00, 01:00, 02:00, … (truncated to `ChronoUnit.HOURS`)
- **Day windows** — midnight UTC (truncated to `ChronoUnit.DAYS`)

On window boundary crossing, the previous window is flushed to the database using the
first scrape that falls into the new window.

---

## Current Modes

`MINUTE_CURRENT`, `HOUR_CURRENT`, `DAY_CURRENT` store the **first** scraped value that
falls within the window. This is effectively a snapshot at the start of each window.

For `HOUR_CURRENT` with a 30 s scrape interval the snapshot is the value scraped between
`HH:00:00` and `HH:00:30`, which is close enough to the top of the hour.

---

## Diff Modes

`MINUTE_DIFF`, `HOUR_DIFF`, `DAY_DIFF` store `current_window_last_value − previous_window_last_value`.

- The diff **can be negative** if the value decreases (e.g. a counter reset or a negative
  power value).
- The **first** recorded diff value for a parameter after a service (re)start is skipped
  because there is no previous value to compare against. No data point is written.
- Diff state is held in memory only; it is not persisted between restarts.

---

## Disk Usage Examples

### Single device, 10 parameters, 30 s scrape interval, 1 year retention

| Configuration | Rows/year | Reduction vs. baseline |
|---|---|---|
| All `MINUTE_AVERAGE` | 5,256,000 | baseline |
| All `HOUR_AVERAGE` | 87,600 | **60×** |
| All `DAY_AVERAGE` | 3,650 | **1,440×** |
| Mixed (see below) | 2,655,010 | **~2×** |

Mixed example: 5 × `MINUTE_AVERAGE` + 3 × `HOUR_AVERAGE` + 2 × `DAY_AVERAGE`:

```
5 × 525,600 = 2,628,000
3 ×   8,760 =    26,280
2 ×     365 =       730
             ─────────────
             2,655,010 rows/year
```

---

## Recommendations

| SunSpec Field | Suggested Mode | Reason |
|---|---|---|
| W (AC power) | `MINUTE_AVERAGE` | High-frequency, needs resolution |
| V (voltage) | `MINUTE_AVERAGE` | Spot-check quality |
| WH (energy export) | `HOUR_DIFF` or `DAY_DIFF` | Incremental counter |
| TmpCab (temperature) | `HOUR_AVERAGE` | Slow-changing |
| ChaState (battery SOC) | `HOUR_AVERAGE` | Slow-changing trend |
| St (inverter status) | `MINUTE_CURRENT` | State field, not numeric trend |
| DCAhr (DC amp-hours) | `DAY_DIFF` | Daily production summary |

---

## API

### Get available modes

```
GET /api/metrics-docs/aggregation-modes
```

Returns all 9 modes with descriptions, window size, and estimated rows/year.

### Configure per parameter

In `PUT /api/devices/{id}/metrics/config`, each parameter entry can include:

```json
{
  "sunspecModelId": 113,
  "fieldName": "WH",
  "enabled": true,
  "aggregationMode": "HOUR_DIFF"
}
```

`aggregationMode` defaults to `MINUTE_AVERAGE` when omitted.

---

## Implementation Notes

- `AggregatingAccumulator` (inner class in `MetricsScrapingService`) handles all 9 modes.
- The accumulator key is `{modelId}_{fieldName}_{mode}`. Each parameter has exactly one
  active accumulator at a time.
- Incomplete windows (shutdown, config change) are discarded — no partial averages written.
- `lastDiffValues` map tracks the previous window's last value per device+parameter for
  diff computation. Cleared when scraping is cancelled for a device.
- Prometheus gauges are always live-updated regardless of aggregation mode.
