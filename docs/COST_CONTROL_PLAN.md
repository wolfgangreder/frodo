# Cost Control Feature - Implementation Plan

## Overview

Add cost control system to track grid import/export costs, calculate hourly/monthly expenses,
and expose metrics to Prometheus. Vendor-neutral design with a formal SPI for price providers.
Import and export prices are managed by **independent, separately configurable providers**.

## Key Design Decisions

- **Energy source:** P_Grid integration from Solar API (no SecondaryMeters parsing)
- **Price providers:** Formal SPI (`EnergyPriceProviderSpi`); aWATTar is the first implementation.
  Import and export prices are served by **separate, independently configured providers**.
- **Tariff time windows:** Core feature — fixed-price time slots (e.g., peak/off-peak) that
  override provider spot prices for a given direction and time.
- **Price resolution order:** tariff window (if matching) → provider price → warn/skip
- **Grid fees:** Multiple simultaneous active fees
- **Monthly calculation:** Real-time on every hourly cost insert
- **Price data migration:** Keep existing FroMarketPrice table; FroEnergyPrice is new and separate
- **Frontend:** Integrate into existing MUI/React app
- **CSV export:** Hourly data export

---

## Package Structure

New top-level package `at.or.reder.frodo.cost` alongside existing `modbus`, `solarapi`, `gpio`:

```
at.or.reder.frodo.cost/
├── spi/
│   ├── EnergyPriceProviderSpi      # SPI interface for price vendors
│   ├── PriceDirection              # enum: IMPORT, EXPORT
│   └── HourlyPrice                 # record: startTime, endTime, priceCt
├── provider/
│   ├── AwattarPriceProvider        # first SPI implementation
│   └── ManualPriceProvider         # no-op; prices entered via REST
├── entity/                         # JPA entities (all Fro-prefixed)
│   ├── EnergyPriceEntity
│   ├── FixedCostEntity
│   ├── GridFeeEntity
│   ├── TariffWindowEntity
│   ├── HourlyEnergyEntity
│   ├── HourlyCostEntity
│   └── MonthlyCostEntity
├── repository/
│   ├── EnergyPriceRepository
│   ├── FixedCostRepository
│   ├── GridFeeRepository
│   ├── TariffWindowRepository
│   ├── HourlyEnergyRepository
│   ├── HourlyCostRepository
│   └── MonthlyCostRepository
└── service/
    ├── EnergyIntegrationService
    ├── EnergyPriceSchedulerService
    ├── CostCalculationService
    └── CostRetentionService
```

Metrics and health checks in `at.or.reder.frodo.health` (existing pattern).  
REST resource in `at.or.reder.frodo.api` (existing pattern).

---

## Phase 1: Database Schema & Entities

### 1.1 Liquibase Changelog

**File:** `src/main/resources/db/changelog/v1.8.0-cost-control.xml`

Seven new tables:

#### FroCostControlConfig — single-row runtime configuration (id always 1)

```sql
CREATE TABLE FroCostControlConfig (
  id                       BIGINT           NOT NULL CONSTRAINT pk_FroCostControlConfig PRIMARY KEY,
  import_provider_id       VARCHAR(50)      NOT NULL,
  export_provider_id       VARCHAR(50)      NOT NULL,
  import_fetch_cron        VARCHAR(100)     NOT NULL,
  export_fetch_cron        VARCHAR(100)     NOT NULL,
  sample_interval_seconds  INTEGER          NOT NULL,
  dead_band_watts          DOUBLE PRECISION NOT NULL,
  retention_hourly_days    INTEGER          NOT NULL,
  retention_monthly_years  INTEGER          NOT NULL,
  updated_at               TIMESTAMP        NOT NULL
);
-- Seeded by Liquibase with id=1 defaults; no sequence needed
```

#### FroEnergyPrice — hourly raw provider prices per direction (nullable per direction)

```sql
CREATE TABLE FroEnergyPrice (
  id              BIGINT           NOT NULL CONSTRAINT pk_FroEnergyPrice PRIMARY KEY,
  start_time      TIMESTAMP        NOT NULL,
  end_time        TIMESTAMP        NOT NULL,
  price_import_ct DOUBLE PRECISION,             -- ct/kWh, null if no import provider active
  price_export_ct DOUBLE PRECISION,             -- ct/kWh, null if no export provider active
  import_source   VARCHAR(50),                  -- provider id: 'AWATTAR', 'MANUAL', etc.
  export_source   VARCHAR(50),                  -- provider id: 'AWATTAR', 'MANUAL', etc.
  created_at      TIMESTAMP        NOT NULL,
  updated_at      TIMESTAMP,
  CONSTRAINT uk_FroEnergyPrice_time UNIQUE (start_time)
);
CREATE INDEX idx_FroEnergyPrice_time ON FroEnergyPrice (start_time);
CREATE SEQUENCE FroEnergyPrice_SEQ START WITH 1 INCREMENT BY 50;
```

Both price columns are **nullable** because import and export are managed by independent
providers and may arrive at different times.

#### FroFixedCost — monthly fixed costs (global, one row per calendar month)

```sql
CREATE TABLE FroFixedCost (
  id               BIGINT           NOT NULL CONSTRAINT pk_FroFixedCost PRIMARY KEY,
  valid_from       DATE             NOT NULL,   -- first day of month: 2026-05-01
  monthly_cost_eur DOUBLE PRECISION NOT NULL,
  description      VARCHAR(255),
  created_at       TIMESTAMP        NOT NULL,
  CONSTRAINT uk_FroFixedCost_month UNIQUE (valid_from)
);
CREATE INDEX idx_FroFixedCost_month ON FroFixedCost (valid_from);
CREATE SEQUENCE FroFixedCost_SEQ START WITH 1 INCREMENT BY 50;
```

#### FroGridFee — grid fees (multiple active simultaneously, time-based)

```sql
CREATE TABLE FroGridFee (
  id          BIGINT           NOT NULL CONSTRAINT pk_FroGridFee PRIMARY KEY,
  valid_from  TIMESTAMP        NOT NULL,
  fee_type    VARCHAR(20)      NOT NULL,   -- 'PERCENT', 'ABSOLUTE_ENERGY', 'ABSOLUTE_TIME'
  fee_value   DOUBLE PRECISION NOT NULL,   -- 5.0 (%), 2.0 (ct/kWh), 15.0 (EUR/month)
  applies_to  VARCHAR(20)      NOT NULL,   -- 'EXPORT', 'IMPORT', 'BOTH'
  description VARCHAR(255),
  created_at  TIMESTAMP        NOT NULL,
  CONSTRAINT ck_FroGridFee_type    CHECK (fee_type   IN ('PERCENT','ABSOLUTE_ENERGY','ABSOLUTE_TIME')),
  CONSTRAINT ck_FroGridFee_applies CHECK (applies_to IN ('EXPORT','IMPORT','BOTH'))
);
CREATE INDEX idx_FroGridFee_valid ON FroGridFee (valid_from);
CREATE SEQUENCE FroGridFee_SEQ START WITH 1 INCREMENT BY 50;
```

#### FroTariffWindow — fixed-price time slots that override provider prices

```sql
CREATE TABLE FroTariffWindow (
  id           BIGINT           NOT NULL CONSTRAINT pk_FroTariffWindow PRIMARY KEY,
  direction    VARCHAR(10)      NOT NULL,   -- 'IMPORT' or 'EXPORT'
  valid_from   DATE             NOT NULL,   -- tariff effective from date (inclusive)
  valid_to     DATE,                        -- tariff end date (exclusive); null = still active
  days_of_week VARCHAR(35),               -- null = all days; 'MON,TUE,WED,THU,FRI' etc.
  time_from    TIME             NOT NULL,   -- window start within day (e.g. 07:00:00)
  time_to      TIME             NOT NULL,   -- window end within day (e.g. 22:00:00); 00:00 = midnight
  price_ct     DOUBLE PRECISION NOT NULL,   -- fixed price in ct/kWh
  priority     INTEGER          NOT NULL DEFAULT 0, -- higher priority wins on overlap
  description  VARCHAR(255),
  created_at   TIMESTAMP        NOT NULL,
  CONSTRAINT ck_FroTariffWindow_dir CHECK (direction IN ('IMPORT','EXPORT'))
);
CREATE INDEX idx_FroTariffWindow_dir_from ON FroTariffWindow (direction, valid_from);
CREATE SEQUENCE FroTariffWindow_SEQ START WITH 1 INCREMENT BY 50;
```

**Notes:**
- `time_from` < `time_to` is required; midnight-crossing tariffs need two rows.
- `time_to = 00:00` is interpreted as end-of-day (24:00).
- Multiple windows can match a given hour; highest `priority` wins.

#### FroHourlyEnergy — hourly grid import/export kWh (integrated from P_Grid)

```sql
CREATE TABLE FroHourlyEnergy (
  id           BIGINT           NOT NULL CONSTRAINT pk_FroHourlyEnergy PRIMARY KEY,
  hour_start   TIMESTAMP        NOT NULL,
  hour_end     TIMESTAMP        NOT NULL,
  import_kwh   DOUBLE PRECISION NOT NULL DEFAULT 0,
  export_kwh   DOUBLE PRECISION NOT NULL DEFAULT 0,
  sample_count INTEGER          NOT NULL,
  created_at   TIMESTAMP        NOT NULL,
  CONSTRAINT uk_FroHourlyEnergy_hour UNIQUE (hour_start)
);
CREATE INDEX idx_FroHourlyEnergy_hour ON FroHourlyEnergy (hour_start);
CREATE SEQUENCE FroHourlyEnergy_SEQ START WITH 1 INCREMENT BY 50;
```

#### FroHourlyCost — hourly cost/income calculation (stores effective prices used)

```sql
CREATE TABLE FroHourlyCost (
  id                     BIGINT           NOT NULL CONSTRAINT pk_FroHourlyCost PRIMARY KEY,
  hour_start             TIMESTAMP        NOT NULL,
  hour_end               TIMESTAMP        NOT NULL,
  import_kwh             DOUBLE PRECISION NOT NULL,
  export_kwh             DOUBLE PRECISION NOT NULL,
  price_import_ct        DOUBLE PRECISION NOT NULL,   -- effective price used (window or provider)
  price_export_ct        DOUBLE PRECISION NOT NULL,   -- effective price used (window or provider)
  import_price_source    VARCHAR(20)      NOT NULL,   -- 'TARIFF_WINDOW' or provider id
  export_price_source    VARCHAR(20)      NOT NULL,   -- 'TARIFF_WINDOW' or provider id
  import_cost_eur        DOUBLE PRECISION NOT NULL,   -- import_kwh * price_import_ct / 100
  export_income_eur      DOUBLE PRECISION NOT NULL,   -- export_kwh * price_export_ct / 100
  fee_eur                DOUBLE PRECISION NOT NULL,   -- sum of all active fee amounts
  net_cost_eur           DOUBLE PRECISION NOT NULL,   -- import_cost - export_income + fee_eur
  created_at             TIMESTAMP        NOT NULL,
  CONSTRAINT uk_FroHourlyCost_hour UNIQUE (hour_start)
);
CREATE INDEX idx_FroHourlyCost_hour ON FroHourlyCost (hour_start);
CREATE SEQUENCE FroHourlyCost_SEQ START WITH 1 INCREMENT BY 50;
```

`import_price_source` / `export_price_source` track which source determined the effective price
(enables audit and recalculation analysis).

#### FroMonthlyCost — monthly summary (pre-calculated, updated real-time)

```sql
CREATE TABLE FroMonthlyCost (
  id                      BIGINT           NOT NULL CONSTRAINT pk_FroMonthlyCost PRIMARY KEY,
  year_month              VARCHAR(7)       NOT NULL,   -- '2026-05'
  total_import_kwh        DOUBLE PRECISION NOT NULL,
  total_export_kwh        DOUBLE PRECISION NOT NULL,
  total_import_cost_eur   DOUBLE PRECISION NOT NULL,
  total_export_income_eur DOUBLE PRECISION NOT NULL,
  total_fee_eur           DOUBLE PRECISION NOT NULL,
  fixed_cost_eur          DOUBLE PRECISION NOT NULL,
  net_cost_eur            DOUBLE PRECISION NOT NULL,
  hours_calculated        INTEGER          NOT NULL,   -- count of FroHourlyCost rows summed
  created_at              TIMESTAMP        NOT NULL,
  updated_at              TIMESTAMP        NOT NULL,
  CONSTRAINT uk_FroMonthlyCost_month UNIQUE (year_month)
);
CREATE INDEX idx_FroMonthlyCost_month ON FroMonthlyCost (year_month);
CREATE SEQUENCE FroMonthlyCost_SEQ START WITH 1 INCREMENT BY 50;
```

### 1.2 Java Entities

**Package:** `at.or.reder.frodo.cost.entity`

| Entity | Table | Key fields |
|--------|-------|-----------|
| `CostControlConfigEntity` | FroCostControlConfig | id=1 (fixed), importProviderId, exportProviderId, cronSchedules, sampleIntervalSeconds, deadBandWatts, retentionDays/Years |
| `EnergyPriceEntity` | FroEnergyPrice | priceImportCt (Double, nullable), priceExportCt (Double, nullable), importSource, exportSource |
| `FixedCostEntity` | FroFixedCost | validFrom (LocalDate), monthlyCostEur |
| `GridFeeEntity` | FroGridFee | feeType (FeeType enum), feeValue, appliesTo (FeeAppliesTo enum) |
| `TariffWindowEntity` | FroTariffWindow | direction (PriceDirection enum), validFrom, validTo, daysOfWeek, timeFrom, timeTo, priceCt, priority |
| `HourlyEnergyEntity` | FroHourlyEnergy | hourStart, importKwh, exportKwh, sampleCount |
| `HourlyCostEntity` | FroHourlyCost | all cost fields + source tracking fields |
| `MonthlyCostEntity` | FroMonthlyCost | yearMonth, aggregated totals, hoursCalculated |

**Note:** `CostControlConfigEntity` has a fixed `@Id` of `1L` (no sequence). `CostControlConfigService` implements `PanacheRepository<CostControlConfigEntity>` (not `@Inject EntityManager`) to survive test profile where Hibernate is disabled.

**Enums** (`at.or.reder.frodo.cost.spi`):

```java
public enum PriceDirection  { IMPORT, EXPORT }
public enum FeeType         { PERCENT, ABSOLUTE_ENERGY, ABSOLUTE_TIME }
public enum FeeAppliesTo    { EXPORT, IMPORT, BOTH }
```

### 1.3 Repositories

**Package:** `at.or.reder.frodo.cost.repository`

**EnergyPriceRepository**
- `findByStartTime(LocalDateTime)`
- `findForTime(LocalDateTime)` — WHERE start_time <= ? AND end_time > ?
- `upsertImport(startTime, endTime, priceCt, source)` — partial update: only import columns
- `upsertExport(startTime, endTime, priceCt, source)` — partial update: only export columns
- `listRecent(limit)`
- `deleteExpired(before)`

**FixedCostRepository**
- `findByYearMonth(String)` — "2026-05"
- `findForDate(LocalDate)`

**GridFeeRepository**
- `findActiveFeesForTime(LocalDateTime)` — WHERE valid_from <= ? ORDER BY valid_from ASC
- `listAll()`

**TariffWindowRepository**
- `findMatchingWindow(PriceDirection, LocalDateTime)` — returns highest-priority matching window
  - Matches: direction, valid_from <= date < valid_to (or valid_to null), day_of_week includes weekday, time_from <= hour_time < time_to
- `listAll()`
- `listByDirection(PriceDirection)`

**HourlyEnergyRepository**
- `findByHourStart(LocalDateTime)`
- `upsert(hourStart, hourEnd, importKwh, exportKwh, sampleCount)`
- `deleteOlderThan(cutoff)`

**HourlyCostRepository**
- `findByHourStart(LocalDateTime)`
- `findByDateRange(from, to)`
- `upsert(...)`
- `deleteOlderThan(cutoff)`
- `sumByDateRange(from, to)` — returns aggregate record for monthly rollup

**MonthlyCostRepository**
- `findByYearMonth(String)`
- `upsert(...)`
- `deleteOlderThan(yearMonth)`

---

## Phase 2: Energy Price Provider SPI

### 2.1 SPI Interface

**Package:** `at.or.reder.frodo.cost.spi`

```java
/**
 * SPI for hourly energy price providers.
 *
 * Implementations are CDI @ApplicationScoped beans. The scheduler selects the active
 * provider per direction via getProviderId() matching the configured provider name.
 *
 * Each provider is direction-specific: declare which directions are supported via
 * getSupportedDirections(). A provider that only supports EXPORT (e.g. aWATTar spot
 * market) returns Set.of(PriceDirection.EXPORT).
 */
public interface EnergyPriceProviderSpi {

  /** Unique string identifier, e.g. "AWATTAR", "MANUAL", "TIBBER". Used in config. */
  String getProviderId();

  /** Human-readable display name for UI and logs. */
  String getDisplayName();

  /**
   * Whether this provider can fetch prices automatically on a schedule.
   * Returns false for MANUAL (user enters prices via REST API).
   */
  boolean isAutoFetchSupported();

  /**
   * Directions this provider can supply prices for.
   * e.g. aWATTar supports EXPORT (spot market feed-in prices).
   * MANUAL supports both.
   */
  Set<PriceDirection> getSupportedDirections();

  /**
   * Fetch hourly prices for the given direction and time range.
   * Called only when isAutoFetchSupported() is true.
   *
   * @param direction IMPORT or EXPORT
   * @param from      range start (inclusive)
   * @param to        range end (exclusive)
   * @return list of hourly price records; empty list if no data available
   */
  List<HourlyPrice> fetchPrices(PriceDirection direction, LocalDateTime from, LocalDateTime to);

  record HourlyPrice(
    LocalDateTime startTime,
    LocalDateTime endTime,
    double priceCt          // ct/kWh for the specified direction
  ) {}
}
```

**CDI selection pattern in `EnergyPriceSchedulerService`:**

```java
@Inject @Any
Instance<EnergyPriceProviderSpi> providers;

EnergyPriceProviderSpi providerFor(String id) {
  return StreamSupport.stream(providers.spliterator(), false)
    .filter(p -> p.getProviderId().equals(id))
    .findFirst()
    .orElseThrow(() -> new IllegalStateException("No provider registered: " + id));
}
```

### 2.2 AwattarPriceProvider

**Class:** `at.or.reder.frodo.cost.provider.AwattarPriceProvider`

- Refactors the existing `AwattarClient` / `AwattarRestClient` fetch logic
- Supports direction: **EXPORT only** (aWATTar publishes spot market prices → feed-in tariff)
- `isAutoFetchSupported()` → `true`
- `getProviderId()` → `"AWATTAR"`
- `fetchPrices(EXPORT, from, to)` → calls aWATTar API, maps `marketprice` (EUR/MWh) to ct/kWh
- `fetchPrices(IMPORT, ...)` → throws `UnsupportedOperationException` (not in supported directions)
- Config:
  ```properties
  frodo.cost-control.awattar.url=https://api.awattar.at/v1/marketdata
  ```

### 2.3 ManualPriceProvider

**Class:** `at.or.reder.frodo.cost.provider.ManualPriceProvider`

- Supports directions: **IMPORT and EXPORT**
- `isAutoFetchSupported()` → `false`
- `fetchPrices(...)` → returns empty list (prices entered via REST POST /prices)
- Used when the user manages all prices manually or via tariff windows

### 2.4 EnergyPriceSchedulerService

**Class:** `at.or.reder.frodo.cost.service.EnergyPriceSchedulerService`

- Manages two independent scheduling loops: one for IMPORT provider, one for EXPORT provider
- Configured provider IDs from properties:
  ```properties
  frodo.cost-control.price.import.provider=MANUAL
  frodo.cost-control.price.export.provider=AWATTAR
  frodo.cost-control.price.import.fetch-cron=0 55 * * * ?
  frodo.cost-control.price.export.fetch-cron=0 55 * * * ?
  ```
- On scheduled trigger for a direction:
  1. Load configured provider for direction
  2. Skip if `!provider.isAutoFetchSupported()`
  3. Call `provider.fetchPrices(direction, now, now+48h)`
  4. Call `energyPriceRepository.upsertImport(...)` or `upsertExport(...)` per row
- On startup: fetch if current hour has no price for the direction

### 2.5 Backward Compatibility

- `MarketPriceSchedulerService` + `FroMarketPrice` table unchanged
- `frodo.awattar.enabled` still controls old aWATTar → FroMarketPrice flow
- Both can run simultaneously during transition period

**Future deprecation path** (out of scope):
1. Migrate FroMarketPrice → FroEnergyPrice (export prices only; import = 0)
2. Remove `MarketPrice*` classes and old endpoints
3. Remove `frodo.awattar.*` config

---

## Phase 3: Energy Integration Service

**Class:** `at.or.reder.frodo.cost.service.EnergyIntegrationService`

### Responsibilities

- Collect P_Grid samples from `SolarApiMetricsService.getLastData()` on a fixed schedule
- Accumulate samples in current hourly window
- On hour boundary: compute import/export kWh via trapezoidal integration, persist `FroHourlyEnergy`
- Trigger `CostCalculationService.calculateHourlyCost(hourStart)` immediately after persist

### Integration Algorithm

```
sign convention: P_Grid > 0 → importing from grid; P_Grid < 0 → exporting to grid

for i = 0 .. n-2:
  avg_power = (sample[i].powerW + sample[i+1].powerW) / 2.0
  dt_seconds = sample[i+1].timestamp - sample[i].timestamp
  energy_wh = avg_power * dt_seconds / 3600.0

  if energy_wh > 0:  import_wh += energy_wh
  else:              export_wh += abs(energy_wh)

import_kwh = import_wh / 1000.0
export_kwh = export_wh / 1000.0
```

### Edge Cases

| Scenario | Handling |
|----------|----------|
| Startup mid-hour | Discard incomplete first hour; start fresh at next boundary |
| Solar API down | Hour with 0 samples → skip persist, leave gap in DB |
| P_Grid near zero | Apply dead-band threshold (configurable, default ±10 W) |
| Hour with < minimum samples | Persist with sample_count; consumers can filter low-quality hours |

### Configuration

```properties
frodo.cost-control.integration.sample-interval-seconds=15
frodo.cost-control.integration.dead-band-watts=10.0
```

### Tests

- Unit test trapezoidal integration: pure import, pure export, mixed, dead-band
- Test hour boundary flush triggers cost calculation
- Mock `SolarApiMetricsService`; verify `FroHourlyEnergy` persistence
- Test startup mid-hour discard

---

## Phase 4: Cost Calculation Service

**Class:** `at.or.reder.frodo.cost.service.CostCalculationService`

### Price Resolution

For each hour and each direction (IMPORT, EXPORT):
1. Query `TariffWindowRepository.findMatchingWindow(direction, hourStart)`
2. If match found → `effectivePriceCt = window.priceCt`, `source = "TARIFF_WINDOW"`
3. Else → query `EnergyPriceRepository.findForTime(hourStart)` for the direction column
4. If neither found → warn, use 0.0 and source = "UNKNOWN"

### Calculation Steps

```
1. Load FroHourlyEnergy for hour
2. Resolve effective import price + source
3. Resolve effective export price + source
4. Calculate base costs:
   import_cost_eur  = import_kwh  * effective_import_ct  / 100.0
   export_income_eur = export_kwh * effective_export_ct / 100.0

5. Load all active FroGridFee entries for hour_start

6. For each fee (see fee formulas below):
   total_fee_eur += calculateFee(fee, energy, effectivePrices)

7. net_cost_eur = import_cost_eur - export_income_eur + total_fee_eur

8. Upsert FroHourlyCost (stores effective prices and sources for audit)

9. Update FroMonthlyCost (real-time aggregation)
```

### Fee Calculation (per fee)

```
FeeType.PERCENT:
  base_eur = switch applies_to:
    EXPORT → export_kwh * effective_export_ct / 100.0
    IMPORT → import_kwh * effective_import_ct / 100.0
    BOTH   → (export_kwh * effective_export_ct + import_kwh * effective_import_ct) / 100.0
  fee_eur = base_eur * fee_value / 100.0

FeeType.ABSOLUTE_ENERGY:          -- fee_value in ct/kWh
  energy_kwh = switch applies_to:
    EXPORT → export_kwh
    IMPORT → import_kwh
    BOTH   → export_kwh + import_kwh
  fee_eur = energy_kwh * fee_value / 100.0

FeeType.ABSOLUTE_TIME:            -- fee_value in EUR/month
  fee_eur = fee_value / 730.0     -- ~730 hours per month
```

All active fees are summed: `total_fee_eur = Σ fee_i`

### Monthly Update (real-time)

After every hourly cost upsert, re-aggregate all `FroHourlyCost` rows for the calendar month
and upsert `FroMonthlyCost`. Fixed cost from `FroFixedCost.findByYearMonth()` added to total.

```
net_cost_eur = total_import_cost - total_export_income + total_fee_eur + fixed_cost_eur
```

### Tests

- Unit test price resolution: tariff window overrides provider, fallback to provider, fallback to zero
- Unit test each `FeeType` calculation
- Test multiple simultaneous fees with different `FeeAppliesTo`
- Test monthly aggregation correctness
- Test missing energy data (no FroHourlyCost written)
- Test `"TARIFF_WINDOW"` vs provider ID recorded in source columns

---

## Phase 5: REST API

**Resource class:** `CostControlResource`  
**Package:** `at.or.reder.frodo.api`  
**Base path:** `/api/cost-control`

### DTO Records

**Package:** `at.or.reder.frodo.api.dto`

- `EnergyPriceRequest` / `EnergyPriceResponse` (per direction or combined)
- `FixedCostRequest` / `FixedCostResponse`
- `GridFeeRequest` / `GridFeeResponse`
- `TariffWindowRequest` / `TariffWindowResponse`
- `HourlyEnergyResponse`
- `HourlyCostResponse`
- `MonthlyCostResponse`
- `PriceProviderInfo` (SPI registry response)
- `CostControlConfigRequest` / `CostControlConfigResponse`

### Endpoints

#### Energy Prices

| Method | Path | Description |
|--------|------|-------------|
| GET | `/prices` | List all stored price rows |
| GET | `/prices/recent/{limit}` | Recent N hours (max 48) |
| GET | `/prices/current` | Row covering now (404 if none) |
| GET | `/prices/{startTime}` | Specific hour (ISO_LOCAL_DATE_TIME) |
| POST | `/prices/import` | Manual create/update import price for hour |
| POST | `/prices/export` | Manual create/update export price for hour |
| DELETE | `/prices/{startTime}/import` | Clear import price for hour |
| DELETE | `/prices/{startTime}/export` | Clear export price for hour |
| POST | `/prices/refresh/import` | Trigger import provider fetch now |
| POST | `/prices/refresh/export` | Trigger export provider fetch now |

**POST /prices/import body:**
```json
{
  "startTime": "2026-05-10T14:00:00",
  "endTime":   "2026-05-10T15:00:00",
  "priceCt":   25.50
}
```

#### Price Providers (SPI Registry)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/providers` | List all registered SPI providers with id, displayName, supportedDirections, autoFetchSupported |

#### Fixed Costs

| Method | Path | Description |
|--------|------|-------------|
| GET | `/fixed-costs` | List all |
| GET | `/fixed-costs/{yearMonth}` | Specific month (2026-05) |
| POST | `/fixed-costs` | Create/update |
| DELETE | `/fixed-costs/{yearMonth}` | Delete |

**POST /fixed-costs body:**
```json
{
  "validFrom":      "2026-05-01",
  "monthlyCostEur": 15.00,
  "description":    "Grid connection fee"
}
```

#### Grid Fees

| Method | Path | Description |
|--------|------|-------------|
| GET | `/grid-fees` | List all |
| GET | `/grid-fees/active` | Fees active now |
| POST | `/grid-fees` | Create |
| PUT | `/grid-fees/{id}` | Update |
| DELETE | `/grid-fees/{id}` | Delete |

**POST /grid-fees body:**
```json
{
  "validFrom":   "2026-01-01T00:00:00",
  "feeType":     "PERCENT",
  "feeValue":    5.0,
  "appliesTo":   "EXPORT",
  "description": "5% network export fee"
}
```

`feeType`: `PERCENT`, `ABSOLUTE_ENERGY` (feeValue in ct/kWh), `ABSOLUTE_TIME` (feeValue in EUR/month)  
`appliesTo`: `EXPORT`, `IMPORT`, `BOTH`

#### Tariff Windows

| Method | Path | Description |
|--------|------|-------------|
| GET | `/tariff-windows` | List all |
| GET | `/tariff-windows?direction=IMPORT` | Filter by direction |
| GET | `/tariff-windows/active` | Windows active at this moment (both directions) |
| GET | `/tariff-windows/active?direction=IMPORT` | Active for one direction |
| POST | `/tariff-windows` | Create |
| PUT | `/tariff-windows/{id}` | Update |
| DELETE | `/tariff-windows/{id}` | Delete |

**POST /tariff-windows body:**
```json
{
  "direction":   "IMPORT",
  "validFrom":   "2026-01-01",
  "validTo":     null,
  "daysOfWeek":  "MON,TUE,WED,THU,FRI",
  "timeFrom":    "07:00:00",
  "timeTo":      "22:00:00",
  "priceCt":     32.50,
  "priority":    10,
  "description": "Peak import tariff"
}
```

`daysOfWeek` values: `MON`, `TUE`, `WED`, `THU`, `FRI`, `SAT`, `SUN` (comma-separated; null = all)  
`timeFrom` / `timeTo`: ISO time strings; `timeTo = "00:00:00"` means end-of-day (24:00)

#### Hourly Data

| Method | Path | Description |
|--------|------|-------------|
| GET | `/hourly/energy?from={iso}&to={iso}` | Hourly energy data |
| GET | `/hourly/costs?from={iso}&to={iso}` | Hourly cost/income |
| GET | `/hourly/latest` | Latest completed hour |
| GET | `/hourly/export-csv?from={iso}&to={iso}` | CSV download |

CSV columns: `hour_start, hour_end, import_kwh, export_kwh, price_import_ct, import_price_source, price_export_ct, export_price_source, import_cost_eur, export_income_eur, fee_eur, net_cost_eur`

#### Monthly Summary

| Method | Path | Description |
|--------|------|-------------|
| GET | `/monthly` | All months |
| GET | `/monthly/{yearMonth}` | Specific month |
| GET | `/monthly/current` | Current month |
| POST | `/monthly/recalculate/{yearMonth}` | Force recalculation of all hours in month |

#### Configuration

| Method | Path | Description |
|--------|------|-------------|
| GET | `/config` | Current settings |
| PUT | `/config` | Update settings |

**PUT /config body:**
```json
{
  "importProvider":         "MANUAL",
  "exportProvider":         "AWATTAR",
  "importFetchCron":        "0 55 * * * ?",
  "exportFetchCron":        "0 55 * * * ?",
  "retentionHourlyDays":    365,
  "retentionMonthlyYears":  10,
  "sampleIntervalSeconds":  15,
  "deadBandWatts":          10.0
}
```

### OpenAPI

All endpoints annotated with `@Tag(name = "Cost Control")`, `@Operation`, `@APIResponse`.

---

## Phase 6: Prometheus Metrics

**Class:** `CostMetrics`  
**Package:** `at.or.reder.frodo.health`

### Gauges

| Metric name | Description | Unit |
|-------------|-------------|------|
| `frodo.cost.current_hour.import_cost_eur` | Current hour import cost | EUR |
| `frodo.cost.current_hour.export_income_eur` | Current hour export income | EUR |
| `frodo.cost.current_hour.net_cost_eur` | Current hour net cost | EUR |
| `frodo.cost.today.import_cost_eur` | Today total import cost | EUR |
| `frodo.cost.today.export_income_eur` | Today total export income | EUR |
| `frodo.cost.today.net_cost_eur` | Today net cost | EUR |
| `frodo.cost.month.import_cost_eur` | Month-to-date import cost | EUR |
| `frodo.cost.month.export_income_eur` | Month-to-date export income | EUR |
| `frodo.cost.month.fixed_cost_eur` | Month fixed cost | EUR |
| `frodo.cost.month.net_cost_eur` | Month-to-date net cost | EUR |
| `frodo.energy_price.import_ct_per_kwh` | Current effective import price | ct/kWh |
| `frodo.energy_price.export_ct_per_kwh` | Current effective export price | ct/kWh |

Current effective price = tariff window price if active, else provider price from FroEnergyPrice.
All gauges return `Double.NaN` when data unavailable.

### Counters

| Metric name | Tags | Description |
|-------------|------|-------------|
| `frodo.cost.hourly_calculations_total` | `status=success\|failure` | Hourly cost calc runs |
| `frodo.cost.price_fetch_total` | `direction=IMPORT\|EXPORT provider=... status=success\|failure` | Price fetch attempts |

---

## Phase 7: Retention & Cleanup

**Class:** `at.or.reder.frodo.cost.service.CostRetentionService`

- Runs daily at 03:00 (after existing `MetricsRetentionService` at 02:00)
- Prunes `FroHourlyEnergy`, `FroHourlyCost`, `FroEnergyPrice` older than `hourly-days`
- Prunes `FroMonthlyCost` older than `monthly-years`
- `FroFixedCost`, `FroGridFee`, `FroTariffWindow` — never auto-pruned (infrequent manual config data)

```properties
frodo.cost-control.retention.hourly-days=365
frodo.cost-control.retention.monthly-years=10
frodo.cost-control.retention.cron=0 0 3 * * ?
```

---

## Phase 8: Frontend UI

**Stack:** React 19, MUI, axios (existing `apiClient`), React Query (already present)

### API Client

**File:** `src/main/webui/src/services/costControlApi.js`

Single module wrapping all `/api/cost-control/*` endpoints via the existing `apiClient` instance.
Sections: prices, providers, fixedCosts, gridFees, tariffWindows, hourly, monthly, config.

### React Query Hooks

**File:** `src/main/webui/src/hooks/useCostControl.js`

Per-entity hooks with cache keys and appropriate `refetchInterval`:
- Live data (current hour, current prices): `refetchInterval: 60_000`
- Reference data (fees, tariff windows, fixed costs): `staleTime: Infinity`, manual invalidation

### Pages

| Page | Route | File |
|------|-------|------|
| Dashboard | `/cost-control` | `pages/CostControl/Dashboard.jsx` |
| Energy Prices | `/cost-control/prices` | `pages/CostControl/EnergyPrices.jsx` |
| Tariff Windows | `/cost-control/tariff-windows` | `pages/CostControl/TariffWindows.jsx` |
| Fixed Costs | `/cost-control/fixed-costs` | `pages/CostControl/FixedCosts.jsx` |
| Grid Fees | `/cost-control/grid-fees` | `pages/CostControl/GridFees.jsx` |
| Monthly Reports | `/cost-control/monthly` | `pages/CostControl/MonthlyReports.jsx` |
| Configuration | `/cost-control/config` | `pages/CostControl/Configuration.jsx` |

### Dashboard

Three MUI cards (MUI Grid layout):

**Current Hour card** (refreshed every 60s):
- Import cost (EUR), export income (EUR), net cost (EUR)
- Small sub-text: effective import price (ct/kWh, source label), effective export price

**Today Summary card:**
- Total import cost, export income, net

**This Month card:**
- Import cost, export income, fees, fixed cost, net; hours calculated of total expected

Grafana embed below cards for historical chart (reuse existing `GrafanaEmbed` component).

### Energy Prices Page

- MUI DataGrid: start time, import ct/kWh, import source, export ct/kWh, export source
- Two Refresh buttons: "Refresh Import" / "Refresh Export" (hidden if respective provider is MANUAL)
- Manual entry: two dialogs — "Set Import Price" / "Set Export Price"
- Delete actions per direction per row

### Tariff Windows Page

- MUI DataGrid: direction chip, valid from, valid to, days, time from, time to, price ct/kWh, priority, description
- Add/Edit Dialog:
  - Direction: Select (IMPORT / EXPORT)
  - Valid from: DatePicker
  - Valid to: DatePicker (optional / null = open-ended)
  - Days of week: multi-select checkboxes (Mon–Sun; all checked = null stored)
  - Time from: TimePicker
  - Time to: TimePicker (00:00 = end of day)
  - Price ct/kWh: TextField (number)
  - Priority: TextField (integer)
  - Description: TextField
- Delete with confirmation
- Direction filter tabs (All / Import / Export)

### Fixed Costs Page

- MUI DataGrid: month, monthly cost EUR, description
- Add/Edit Dialog: month picker (year+month only), cost field, description
- Delete with confirmation

### Grid Fees Page

- MUI DataGrid: valid from, fee type, fee value + unit label, applies to, description
- Add/Edit Dialog:
  - DateTimePicker for validFrom
  - Select for feeType (labels: "% of cost", "ct per kWh", "EUR per month")
  - TextField for feeValue
  - Select for appliesTo (EXPORT, IMPORT, BOTH)
  - TextField for description
- Delete with confirmation

### Monthly Reports Page

- MUI DataGrid: month, import kWh, export kWh, import cost EUR, export income EUR, fees EUR, fixed EUR, net EUR, hours
- Export CSV button → downloads hourly data for selected month range
- Recalculate button per row
- Month range selector (from/to pickers)

### Configuration Page

- Two Select fields: "Import Price Provider" / "Export Price Provider"
  - Options populated from `GET /providers` (displays displayName, shows supportedDirections)
  - Warning shown if selected provider does not support the chosen direction
- Optional cron schedule TextFields (shown only if provider supports auto-fetch)
- Retention TextFields: hourly days, monthly years
- Integration TextFields: sample interval, dead-band watts
- Save button with success/error snackbar

### Navigation

**File:** `src/main/webui/src/components/layout/Sidebar.jsx`

New top-level item "Cost Control" (icon: `EuroIcon`), expandable with sub-items:
- Dashboard
- Energy Prices
- Tariff Windows
- Fixed Costs
- Grid Fees
- Monthly Reports
- Configuration

### Routing Changes

**File:** `src/main/webui/src/App.js`

```javascript
<Route path="/cost-control"                element={<Dashboard />} />
<Route path="/cost-control/prices"         element={<EnergyPrices />} />
<Route path="/cost-control/tariff-windows" element={<TariffWindows />} />
<Route path="/cost-control/fixed-costs"    element={<FixedCosts />} />
<Route path="/cost-control/grid-fees"      element={<GridFees />} />
<Route path="/cost-control/monthly"        element={<MonthlyReports />} />
<Route path="/cost-control/config"         element={<Configuration />} />
```

---

## Phase 9: Health Check

**Class:** `CostControlHealthCheck`  
**Package:** `at.or.reder.frodo.health`  
**Qualifier:** `@Readiness`

Reports:
- `import_price_available` — FroEnergyPrice row with non-null import price OR active tariff window exists for current hour
- `export_price_available` — same for export
- `current_hour_energy_available` — FroHourlyEnergy row exists for current hour
- `import_provider` — configured provider id
- `export_provider` — configured provider id

Returns `UP` even if data missing (integration may be starting); `DOWN` only on repository failure.

---

## Phase 10: Documentation

Update `AGENTS.md`:
- Add `cost/` package to package structure section
- Add new REST endpoints to Key Endpoints table
- Add new config properties
- Add new DB tables to DB naming convention section

Create `docs/COST_CONTROL.md`:
- Feature overview and data flow diagram
- Price resolution priority explanation (tariff window > provider > zero)
- Adding a new price provider (SPI guide)
- Configuration guide
- API usage examples
- Prometheus metrics reference
- Troubleshooting (missing prices, energy gaps, tariff window overlap)

---

## Full Configuration Reference

```properties
# Cost Control feature flag
frodo.cost-control.enabled=true

# Import price provider (any registered EnergyPriceProviderSpi.getProviderId())
frodo.cost-control.price.import.provider=MANUAL
frodo.cost-control.price.import.fetch-cron=0 55 * * * ?

# Export price provider
frodo.cost-control.price.export.provider=AWATTAR
frodo.cost-control.price.export.fetch-cron=0 55 * * * ?

# aWATTar provider config
frodo.cost-control.awattar.url=https://api.awattar.at/v1/marketdata

# Energy integration (P_Grid sampling)
frodo.cost-control.integration.sample-interval-seconds=15
frodo.cost-control.integration.dead-band-watts=10.0

# Retention
frodo.cost-control.retention.hourly-days=365
frodo.cost-control.retention.monthly-years=10
frodo.cost-control.retention.cron=0 0 3 * * ?

# Test profile overrides
%test.frodo.cost-control.enabled=false
```

---

## Implementation Order & Estimates

| Phase | Content | Est. Hours |
|-------|---------|-----------|
| 1 | DB schema (7 tables), entities, repositories | 3-4 |
| 2 | EnergyPriceProviderSpi + AwattarPriceProvider + ManualPriceProvider + EnergyPriceSchedulerService | 3-4 |
| 3 | EnergyIntegrationService | 3-4 |
| 4 | CostCalculationService (price resolution + tariff windows + fees + monthly) | 4-5 |
| 5 | REST API (resource + DTOs, all endpoints) | 4-5 |
| 6 | Prometheus metrics (CostMetrics) | 2 |
| 7 | Retention service | 1-2 |
| 8 | Frontend UI (7 pages, hooks, API client, routing, nav) | 8-10 |
| 9 | Health check | 1 |
| 10 | Documentation | 1 |
| **Total** | | **30-40** |

---

## Testing Strategy

### Unit Tests (no DB, no Quarkus)

- Trapezoidal integration with known power profiles
- Tariff window matching logic (day-of-week, time range, priority, overlap)
- Price resolution: window overrides provider, fallback chain
- Fee calculation for all `FeeType` values with all `FeeAppliesTo` combinations
- Multiple simultaneous fees summing correctly
- Monthly aggregation formula

### Integration Tests (`@QuarkusTest`)

- Repository CRUD for all 7 new entities
- `TariffWindowRepository.findMatchingWindow()` with overlapping windows
- Full flow: P_Grid samples → hourly energy → cost calculation → monthly update
- REST API endpoints (RestAssured) for all resources
- SPI provider selection by configured id
- Scheduled price fetch (mock provider)
- Retention cleanup

### Manual QA Checklist

- [ ] Manual import price entry via UI form
- [ ] Manual export price entry via UI form
- [ ] Refresh export button triggers aWATTar fetch
- [ ] Tariff window creation (IMPORT, peak/off-peak)
- [ ] Verify tariff window overrides provider price in FroHourlyCost
- [ ] Fixed cost entry for current month
- [ ] Grid fee creation (each type: PERCENT, ABSOLUTE_ENERGY, ABSOLUTE_TIME)
- [ ] Multiple simultaneous fees summed correctly
- [ ] Dashboard cards show live data, refresh every minute
- [ ] Monthly report shows correct totals
- [ ] CSV download produces correct columns and data
- [ ] Recalculate button triggers re-computation
- [ ] Prometheus metrics visible at `/q/metrics`
- [ ] Health check at `/q/health` reports correctly
- [ ] Config page shows available providers with supported directions
- [ ] Warning shown in config UI when provider does not support direction

---

## Future Enhancements

### Weather Forecast Integration (keep in evidence — not in scope)

A future `WeatherForecastProviderSpi` (same CDI injection pattern as `EnergyPriceProviderSpi`)
could supply hourly forecasts (irradiance, cloud cover, temperature). Use cases:
- Estimate tomorrow's PV production → improve export income forecast
- Correlate weather with actual production metrics
- Feed into dynamic price forecasting

When implementing, mirror the `EnergyPriceProviderSpi` pattern: interface in `cost.spi`,
implementations in `cost.provider`, scheduled service for periodic fetch.

### Additional Price Providers

- Tibber (GraphQL API, supports import prices)
- Nordpool (European spot market)
- CSV file import (batch price upload via REST)

### Cost Forecasting

- Predict month-end net cost from current month trend
- Expose as Prometheus gauge + REST endpoint

### Budget Alerts

- Configurable monthly budget threshold
- Notification when month-to-date net cost exceeds threshold
- Integration with health check (status WARN when over budget)

### Additional Reports

- Native chart library (recharts) for offline rendering without Grafana
- PDF report export
- Month-over-month and year-over-year cost comparison views
