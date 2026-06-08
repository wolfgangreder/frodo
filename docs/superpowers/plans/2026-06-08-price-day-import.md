# Price Day Import Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/cost-control/prices/fetch/{direction}?date=yyyy-MM-dd` that fetches a full day of hourly energy prices from the configured provider, upserts them, and recalculates hourly costs for affected hours.

**Architecture:** New method `fetchAndPersistForDate` in `EnergyPriceSchedulerService` handles provider lookup, date-window fetch, and upsert. `CostControlResource` calls it, then recalculates costs for hours with energy data. Two new `@Inject` fields added to the resource (`HourlyEnergyRepository`, `CostCalculationService`).

**Tech Stack:** Quarkus 3.x, JAX-RS (RESTEasy Reactive), Mockito 5 (unit tests), RestAssured + @QuarkusTest (endpoint tests).

---

## File Map

| File | Action |
|------|--------|
| `src/main/java/at/or/reder/frodo/cost/service/EnergyPriceSchedulerService.java` | Add `fetchAndPersistForDate` + import `java.time.LocalDate` + import `java.util.ArrayList` |
| `src/main/java/at/or/reder/frodo/api/CostControlResource.java` | Add 2 `@Inject` fields + 3 imports + endpoint method |
| `src/test/java/at/or/reder/frodo/cost/service/EnergyPriceSchedulerServiceTest.java` | New unit test class (Mockito) |
| `src/test/java/at/or/reder/frodo/api/CostControlResourcePriceFetchTest.java` | New endpoint test class (@QuarkusTest) |

---

### Task 1: Unit tests for `fetchAndPersistForDate` (failing)

**Files:**
- Create: `src/test/java/at/or/reder/frodo/cost/service/EnergyPriceSchedulerServiceTest.java`

- [ ] **Step 1: Create the test file**

```java
/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.cost.service;

import at.or.reder.frodo.cost.entity.CostControlConfigEntity;
import at.or.reder.frodo.cost.entity.EnergyPriceEntity;
import at.or.reder.frodo.cost.repository.EnergyPriceRepository;
import at.or.reder.frodo.cost.spi.EnergyPriceProviderSpi;
import at.or.reder.frodo.cost.spi.PriceDirection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnergyPriceSchedulerService#fetchAndPersistForDate}.
 */
@ExtendWith(MockitoExtension.class)
class EnergyPriceSchedulerServiceTest {

  private EnergyPriceSchedulerService service;

  @Mock
  Instance<EnergyPriceProviderSpi> providers;

  @Mock
  CostControlConfigService configService;

  @Mock
  EnergyPriceRepository energyPriceRepository;

  @Mock
  MeterRegistry meterRegistry;

  @Mock
  EnergyPriceProviderSpi mockProvider;

  @BeforeEach
  void setUp() {
    service = new EnergyPriceSchedulerService();
    service.providers = providers;
    service.configService = configService;
    service.energyPriceRepository = energyPriceRepository;
    service.meterRegistry = meterRegistry;
    service.costControlEnabled = false;   // disable to skip startup fetch
    service.datasourceActive = true;

    Counter mockCounter = mock(Counter.class);
    lenient().when(meterRegistry.counter(anyString(), (String[]) any())).thenReturn(mockCounter);
    service.onStart(new StartupEvent());  // initialises counters only (fetch skipped)

    service.costControlEnabled = true;    // re-enable for test execution
  }

  @Test
  void fetchAndPersistForDate_returnsUpsertedEntities() {
    LocalDate date = LocalDate.of(2026, 6, 7);
    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.plusDays(1).atStartOfDay();

    CostControlConfigEntity cfg = new CostControlConfigEntity();
    cfg.importProviderId = "AWATTAR";
    when(configService.load()).thenReturn(cfg);
    when(providers.spliterator()).thenReturn(List.of(mockProvider).spliterator());
    when(mockProvider.getProviderId()).thenReturn("AWATTAR");
    when(mockProvider.isAutoFetchSupported()).thenReturn(true);
    when(mockProvider.getSupportedDirections()).thenReturn(Set.of(PriceDirection.IMPORT, PriceDirection.EXPORT));

    List<EnergyPriceProviderSpi.HourlyPrice> prices = List.of(
      new EnergyPriceProviderSpi.HourlyPrice(from, from.plusHours(1), new BigDecimal("28.5")),
      new EnergyPriceProviderSpi.HourlyPrice(from.plusHours(1), from.plusHours(2), new BigDecimal("29.0"))
    );
    when(mockProvider.fetchPrices(PriceDirection.IMPORT, from, to)).thenReturn(prices);

    EnergyPriceEntity e1 = new EnergyPriceEntity();
    e1.startTime = from;
    EnergyPriceEntity e2 = new EnergyPriceEntity();
    e2.startTime = from.plusHours(1);
    when(energyPriceRepository.upsertImport(eq(from), eq(from.plusHours(1)), any(), eq("AWATTAR"))).thenReturn(e1);
    when(energyPriceRepository.upsertImport(eq(from.plusHours(1)), eq(from.plusHours(2)), any(), eq("AWATTAR"))).thenReturn(e2);

    List<EnergyPriceEntity> result = service.fetchAndPersistForDate(PriceDirection.IMPORT, date);

    assertEquals(2, result.size());
    verify(energyPriceRepository, times(2)).upsertImport(any(), any(), any(), eq("AWATTAR"));
    verify(mockProvider).fetchPrices(PriceDirection.IMPORT, from, to);
  }

  @Test
  void fetchAndPersistForDate_throwsWhenDisabled() {
    service.costControlEnabled = false;
    assertThrows(IllegalStateException.class,
      () -> service.fetchAndPersistForDate(PriceDirection.IMPORT, LocalDate.of(2026, 6, 7)));
  }

  @Test
  void fetchAndPersistForDate_throwsWhenAutoFetchNotSupported() {
    LocalDate date = LocalDate.of(2026, 6, 7);
    CostControlConfigEntity cfg = new CostControlConfigEntity();
    cfg.importProviderId = "MANUAL";
    when(configService.load()).thenReturn(cfg);
    when(providers.spliterator()).thenReturn(List.of(mockProvider).spliterator());
    when(mockProvider.getProviderId()).thenReturn("MANUAL");
    when(mockProvider.isAutoFetchSupported()).thenReturn(false);

    assertThrows(UnsupportedOperationException.class,
      () -> service.fetchAndPersistForDate(PriceDirection.IMPORT, date));
  }

  @Test
  void fetchAndPersistForDate_passesCorrectDateWindow() {
    LocalDate date = LocalDate.of(2026, 6, 7);
    LocalDateTime expectedFrom = LocalDateTime.of(2026, 6, 7, 0, 0, 0);
    LocalDateTime expectedTo   = LocalDateTime.of(2026, 6, 8, 0, 0, 0);

    CostControlConfigEntity cfg = new CostControlConfigEntity();
    cfg.exportProviderId = "AWATTAR";
    when(configService.load()).thenReturn(cfg);
    when(providers.spliterator()).thenReturn(List.of(mockProvider).spliterator());
    when(mockProvider.getProviderId()).thenReturn("AWATTAR");
    when(mockProvider.isAutoFetchSupported()).thenReturn(true);
    when(mockProvider.getSupportedDirections()).thenReturn(Set.of(PriceDirection.EXPORT));
    when(mockProvider.fetchPrices(any(), any(), any())).thenReturn(List.of());

    service.fetchAndPersistForDate(PriceDirection.EXPORT, date);

    verify(mockProvider).fetchPrices(PriceDirection.EXPORT, expectedFrom, expectedTo);
  }

  @Test
  void fetchAndPersistForDate_usesUpsertExportForExportDirection() {
    LocalDate date = LocalDate.of(2026, 6, 7);
    LocalDateTime from = date.atStartOfDay();

    CostControlConfigEntity cfg = new CostControlConfigEntity();
    cfg.exportProviderId = "AWATTAR";
    when(configService.load()).thenReturn(cfg);
    when(providers.spliterator()).thenReturn(List.of(mockProvider).spliterator());
    when(mockProvider.getProviderId()).thenReturn("AWATTAR");
    when(mockProvider.isAutoFetchSupported()).thenReturn(true);
    when(mockProvider.getSupportedDirections()).thenReturn(Set.of(PriceDirection.EXPORT));
    when(mockProvider.fetchPrices(any(), any(), any())).thenReturn(List.of(
      new EnergyPriceProviderSpi.HourlyPrice(from, from.plusHours(1), new BigDecimal("5.0"))
    ));
    EnergyPriceEntity e = new EnergyPriceEntity();
    e.startTime = from;
    when(energyPriceRepository.upsertExport(any(), any(), any(), any())).thenReturn(e);

    service.fetchAndPersistForDate(PriceDirection.EXPORT, date);

    verify(energyPriceRepository).upsertExport(eq(from), eq(from.plusHours(1)), eq(new BigDecimal("5.0")), eq("AWATTAR"));
    verify(energyPriceRepository, never()).upsertImport(any(), any(), any(), any());
  }
}
```

- [ ] **Step 2: Run the tests — expect compile failure (method does not exist yet)**

```bash
./gradlew test --tests "at.or.reder.frodo.cost.service.EnergyPriceSchedulerServiceTest"
```

Expected: compilation error mentioning `fetchAndPersistForDate` not found.

---

### Task 2: Implement `fetchAndPersistForDate` in `EnergyPriceSchedulerService`

**Files:**
- Modify: `src/main/java/at/or/reder/frodo/cost/service/EnergyPriceSchedulerService.java`

- [ ] **Step 1: Add missing imports at line 38 (after `import java.time.LocalDateTime;`)**

Add these two lines:

```java
import java.time.LocalDate;
import java.util.ArrayList;
```

- [ ] **Step 2: Add the `fetchAndPersistForDate` method after `refreshNow` (after line 142)**

Insert the following block starting at line 143 (before the `// ---- internals` comment):

```java
  /**
   * Fetches and persists hourly prices for a specific calendar day from the configured provider.
   *
   * <p>The fetch window is exactly {@code [date T00:00, date+1 T00:00)} UTC (24 hours).
   * Existing prices for any overlapping hour are overwritten via upsert.</p>
   *
   * @param direction IMPORT or EXPORT
   * @param date      the calendar day to fetch
   * @return list of upserted {@link at.or.reder.frodo.cost.entity.EnergyPriceEntity} rows
   * @throws IllegalStateException      if cost control is disabled or datasource inactive
   * @throws UnsupportedOperationException if provider does not support auto-fetch or the direction
   * @throws RuntimeException           if the provider fetch fails
   */
  public List<at.or.reder.frodo.cost.entity.EnergyPriceEntity> fetchAndPersistForDate(
      PriceDirection direction, LocalDate date) {
    if (!costControlEnabled || !datasourceActive) {
      throw new IllegalStateException("Cost control is disabled or datasource inactive");
    }

    CostControlConfigEntity cfg = configService.load();
    String providerId = direction == PriceDirection.IMPORT
      ? cfg.importProviderId
      : cfg.exportProviderId;

    EnergyPriceProviderSpi provider = resolveProvider(providerId);

    if (!provider.isAutoFetchSupported()) {
      throw new UnsupportedOperationException(
        "Provider '" + providerId + "' does not support auto-fetch");
    }

    if (!provider.getSupportedDirections().contains(direction)) {
      throw new UnsupportedOperationException(
        "Provider '" + providerId + "' does not support direction " + direction);
    }

    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.plusDays(1).atStartOfDay();

    LOG.debugf("Fetching %s prices for date %s from provider '%s' (%s – %s)",
      direction, date, providerId, from, to);

    try {
      List<EnergyPriceProviderSpi.HourlyPrice> prices = provider.fetchPrices(direction, from, to);
      List<at.or.reder.frodo.cost.entity.EnergyPriceEntity> result = new ArrayList<>(prices.size());
      for (EnergyPriceProviderSpi.HourlyPrice p : prices) {
        at.or.reder.frodo.cost.entity.EnergyPriceEntity entity = (direction == PriceDirection.IMPORT)
          ? energyPriceRepository.upsertImport(p.startTime(), p.endTime(), p.priceCt(), providerId)
          : energyPriceRepository.upsertExport(p.startTime(), p.endTime(), p.priceCt(), providerId);
        result.add(entity);
      }
      if (direction == PriceDirection.IMPORT) {
        importFetchSuccess.increment();
      } else {
        exportFetchSuccess.increment();
      }
      LOG.debugf("Persisted %d %s prices for date %s from '%s'",
        result.size(), direction, date, providerId);
      return result;
    } catch (UnsupportedOperationException | IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to fetch %s prices for date %s from provider '%s'",
        direction, date, providerId);
      if (direction == PriceDirection.IMPORT) {
        importFetchFailure.increment();
      } else {
        exportFetchFailure.increment();
      }
      throw ex;
    }
  }
```

Note: `EnergyPriceEntity` is used with its full qualified name in the method signature to avoid adding a new import that clashes with other code. Alternatively, add `import at.or.reder.frodo.cost.entity.EnergyPriceEntity;` at the top of the file (after `import at.or.reder.frodo.cost.entity.CostControlConfigEntity;`) and use `EnergyPriceEntity` directly — which is cleaner. Do the latter:

Add import at line 21 (after `import at.or.reder.frodo.cost.entity.CostControlConfigEntity;`):
```java
import at.or.reder.frodo.cost.entity.EnergyPriceEntity;
```

Then use `EnergyPriceEntity` (not the FQN) throughout the method.

- [ ] **Step 3: Run the unit tests — expect all 5 to pass**

```bash
./gradlew test --tests "at.or.reder.frodo.cost.service.EnergyPriceSchedulerServiceTest"
```

Expected output:
```
EnergyPriceSchedulerServiceTest > fetchAndPersistForDate_returnsUpsertedEntities() PASSED
EnergyPriceSchedulerServiceTest > fetchAndPersistForDate_throwsWhenDisabled() PASSED
EnergyPriceSchedulerServiceTest > fetchAndPersistForDate_throwsWhenAutoFetchNotSupported() PASSED
EnergyPriceSchedulerServiceTest > fetchAndPersistForDate_passesCorrectDateWindow() PASSED
EnergyPriceSchedulerServiceTest > fetchAndPersistForDate_usesUpsertExportForExportDirection() PASSED
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/at/or/reder/frodo/cost/service/EnergyPriceSchedulerService.java \
        src/test/java/at/or/reder/frodo/cost/service/EnergyPriceSchedulerServiceTest.java
git commit -m "feat(cost): add fetchAndPersistForDate to EnergyPriceSchedulerService

Fetches hourly prices for a specific calendar day from the configured
provider (window: date T00:00 to date+1 T00:00). Upserts each returned
HourlyPrice row. Throws IllegalStateException when disabled, and
UnsupportedOperationException when provider does not support auto-fetch
or the requested direction."
```

---

### Task 3: REST endpoint validation tests (failing)

**Files:**
- Create: `src/test/java/at/or/reder/frodo/api/CostControlResourcePriceFetchTest.java`

- [ ] **Step 1: Create the test file**

```java
/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Endpoint tests for POST /api/cost-control/prices/fetch/{direction}.
 *
 * <p>These tests use the test profile (datasource inactive) and therefore
 * cover validation (400) and disabled-state (409) responses without a real DB.</p>
 */
@QuarkusTest
class CostControlResourcePriceFetchTest {

  @Test
  void fetchPricesForDay_missingDate_returns400() {
    given()
      .when().post("/api/cost-control/prices/fetch/IMPORT")
      .then()
      .statusCode(400);
  }

  @Test
  void fetchPricesForDay_invalidDate_returns400() {
    given()
      .queryParam("date", "not-a-date")
      .when().post("/api/cost-control/prices/fetch/IMPORT")
      .then()
      .statusCode(400);
  }

  @Test
  void fetchPricesForDay_invalidDirection_returns400() {
    given()
      .queryParam("date", "2026-06-07")
      .when().post("/api/cost-control/prices/fetch/BADDIR")
      .then()
      .statusCode(400);
  }

  @Test
  void fetchPricesForDay_datasourceInactive_returns409() {
    // In test profile quarkus.datasource.active=false, so the service
    // throws IllegalStateException which is mapped to 409 Conflict.
    given()
      .queryParam("date", "2026-06-07")
      .when().post("/api/cost-control/prices/fetch/IMPORT")
      .then()
      .statusCode(409);
  }
}
```

- [ ] **Step 2: Run the tests — expect compile failure (endpoint does not exist)**

```bash
./gradlew test --tests "at.or.reder.frodo.api.CostControlResourcePriceFetchTest"
```

Expected: test class compiles (no references to new code) but all 4 tests fail with `404 Not Found` (endpoint not yet registered).

---

### Task 4: Add endpoint to `CostControlResource`

**Files:**
- Modify: `src/main/java/at/or/reder/frodo/api/CostControlResource.java`

- [ ] **Step 1: Add three new imports**

After the existing import block (before `import io.quarkus.panache.common.Sort;` at line 54), add:

```java
import at.or.reder.frodo.cost.repository.HourlyEnergyRepository;
import at.or.reder.frodo.cost.service.CostCalculationService;
import jakarta.ws.rs.WebApplicationException;
```

- [ ] **Step 2: Add two new `@Inject` fields**

After the existing `@Inject FixedCostRepository fixedCostRepository;` at line 116, add:

```java
  @Inject
  HourlyEnergyRepository hourlyEnergyRepository;

  @Inject
  CostCalculationService costCalculationService;
```

- [ ] **Step 3: Add the endpoint method**

Insert the following method after `setExportPrice` (after the closing `}` at line 248, before the `// ---- Hourly cost` comment at line 250):

```java
  /**
   * Fetches and persists hourly energy prices for a specific calendar day from
   * the configured cloud provider. Overwrites existing rows for any returned
   * hour and recalculates hourly costs where energy data is available.
   *
   * @param direction IMPORT or EXPORT (case-insensitive)
   * @param date      calendar day in {@code yyyy-MM-dd} format (required)
   * @return list of upserted price rows for the day (0–24 items)
   */
  @POST
  @Path("/prices/fetch/{direction}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Fetch and persist prices for a specific day from the configured provider")
  public List<EnergyPriceResponse> fetchPricesForDay(
      @PathParam("direction") String direction,
      @QueryParam("date") String date) {

    if (date == null || date.isBlank()) {
      throw new BadRequestException("Query parameter 'date' is required (format: yyyy-MM-dd)");
    }
    LocalDate localDate = parseDate(date);
    PriceDirection dir = parsePriceDirection(direction);

    List<EnergyPriceEntity> upserted;
    try {
      upserted = priceSchedulerService.fetchAndPersistForDate(dir, localDate);
    } catch (IllegalStateException | UnsupportedOperationException ex) {
      throw new WebApplicationException(
        Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build());
    } catch (Exception ex) {
      LOG.errorf(ex, "fetchPricesForDay failed for %s %s", direction, date);
      throw new WebApplicationException(
        Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(ex.getMessage()).build());
    }

    for (EnergyPriceEntity price : upserted) {
      LocalDateTime hourStart = price.startTime;
      if (hourlyEnergyRepository.findByHourStart(hourStart).isPresent()) {
        try {
          costCalculationService.calculateHourlyCost(hourStart);
          costCalculationService.updateDailyCost(hourStart);
          costCalculationService.updateMonthlyCost(hourStart);
        } catch (Exception ex) {
          LOG.warnf(ex, "Failed to recalculate costs for hour %s", hourStart);
        }
      }
    }

    return upserted.stream().map(this::toPriceResponse).toList();
  }
```

- [ ] **Step 4: Run the endpoint tests — expect all 4 to pass**

```bash
./gradlew test --tests "at.or.reder.frodo.api.CostControlResourcePriceFetchTest"
```

Expected:
```
CostControlResourcePriceFetchTest > fetchPricesForDay_missingDate_returns400() PASSED
CostControlResourcePriceFetchTest > fetchPricesForDay_invalidDate_returns400() PASSED
CostControlResourcePriceFetchTest > fetchPricesForDay_invalidDirection_returns400() PASSED
CostControlResourcePriceFetchTest > fetchPricesForDay_datasourceInactive_returns409() PASSED
```

- [ ] **Step 5: Run the full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/at/or/reder/frodo/api/CostControlResource.java \
        src/test/java/at/or/reder/frodo/api/CostControlResourcePriceFetchTest.java
git commit -m "feat(api): add POST /cost-control/prices/fetch/{direction} endpoint

Fetches a full day of hourly energy prices from the configured provider
for a given date (yyyy-MM-dd). Upserts all returned rows and recalculates
hourly, daily, and monthly costs for hours with existing energy data.

Returns List<EnergyPriceResponse> (0-24 items). Validation errors -> 400,
disabled state -> 409, provider failures -> 503."
```
