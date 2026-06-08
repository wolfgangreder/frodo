# Design: Price Day Import Endpoint

**Date:** 2026-06-08  
**Status:** Approved

## Summary

New REST endpoint to fetch and persist all hourly energy prices for a specific calendar day from the configured cloud provider (aWATTar). Overwrites existing prices for any affected hour and recalculates hourly costs for hours that have energy data.

## API

```
POST /api/cost-control/prices/fetch/{direction}?date=2026-06-07
```

### Parameters

| Name | Kind | Type | Required | Notes |
|------|------|------|----------|-------|
| `direction` | path | string | yes | `IMPORT` or `EXPORT`, case-insensitive |
| `date` | query | string | yes | ISO-8601 date `YYYY-MM-DD` |

### Response

| Status | Body | Condition |
|--------|------|-----------|
| `200 OK` | `List<EnergyPriceResponse>` | All rows upserted for the day (0–24 items) |
| `400 Bad Request` | error message | Invalid/missing `direction` or `date` |
| `409 Conflict` | error message | Cost control disabled, or provider not configured / direction unsupported |
| `503 Service Unavailable` | error message | Provider fetch threw exception |

An empty list (`[]`) is a valid `200` response when the provider returns no prices.

## Components Changed

### `EnergyPriceSchedulerService`

New public method:

```java
public List<EnergyPriceEntity> fetchAndPersistForDate(PriceDirection direction, LocalDate date)
```

**Flow:**
1. Load config via `configService.load()`.
2. Check `costControlEnabled` + `datasourceActive`; throw `IllegalStateException` if disabled.
3. Resolve provider via existing `resolveProvider(providerId)`.
4. Check `provider.isAutoFetchSupported()` and `provider.getSupportedDirections().contains(direction)`; throw `UnsupportedOperationException` if not supported.
5. Build fetch window: `from = date.atStartOfDay()`, `to = date.plusDays(1).atStartOfDay()` (UTC, 24 hours).
6. Call `provider.fetchPrices(direction, from, to)`.
7. For each `HourlyPrice`: call `energyPriceRepository.upsertImport` or `upsertExport`; collect returned entities.
8. Return list of upserted `EnergyPriceEntity`.

Metrics counters (`fetchSuccess` / `fetchError`) incremented same as in `fetchForDirection`.

### `CostControlResource`

New endpoint method. After calling `fetchAndPersistForDate`:

```java
for (EnergyPriceEntity price : upserted) {
    LocalDateTime hourStart = price.startTime;
    if (hourlyEnergyRepository.findByHourStart(hourStart).isPresent()) {
        costCalculationService.calculateHourlyCost(hourStart);
        costCalculationService.updateDailyCost(hourStart);
        costCalculationService.updateMonthlyCost(hourStart);
    }
}
return upserted.stream().map(this::toPriceResponse).toList();
```

`@Transactional` not on the resource method itself — each repo/service call manages its own transaction (matching existing pattern).

Reuses existing helpers: `parsePriceDirection(String)`, `toPriceResponse(EnergyPriceEntity)`.

## Edge Cases

| Case | Behaviour |
|------|-----------|
| Provider returns < 24 hours | Upsert what was returned; recalc only those hours |
| Provider returns 0 hours | Return `200 []` |
| Hour has no energy data | Skip `calculateHourlyCost` for that hour |
| `updateDailyCost` / `updateMonthlyCost` called multiple times for same day/month | Safe — both are idempotent upserts |
| Provider unavailable | Propagate exception → `503` |

## Files Affected

| File | Change |
|------|--------|
| `cost/service/EnergyPriceSchedulerService.java` | Add `fetchAndPersistForDate` method |
| `api/CostControlResource.java` | Add `POST /prices/fetch/{direction}` endpoint; inject `HourlyEnergyRepository` + `CostCalculationService` (not yet present) |

No new files, no DB changes, no Liquibase changesets needed.

## Out of Scope

- Bulk manual price import (no provider involved)
- Async / background execution
- Progress reporting
