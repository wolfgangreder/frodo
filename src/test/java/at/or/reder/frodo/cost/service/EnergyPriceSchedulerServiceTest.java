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
import org.mockito.ArgumentMatchers;
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
    lenient().when(meterRegistry.counter(anyString(), ArgumentMatchers.<String[]>any())).thenReturn(mockCounter);
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
    when(energyPriceRepository.upsertImport(eq(from), eq(from.plusHours(1)), eq(new BigDecimal("28.5")), eq("AWATTAR"))).thenReturn(e1);
    when(energyPriceRepository.upsertImport(eq(from.plusHours(1)), eq(from.plusHours(2)), eq(new BigDecimal("29.0")), eq("AWATTAR"))).thenReturn(e2);

    List<EnergyPriceEntity> result = service.fetchAndPersistForDate(PriceDirection.IMPORT, date);

    assertEquals(2, result.size());
    verify(energyPriceRepository).upsertImport(eq(from), eq(from.plusHours(1)), eq(new BigDecimal("28.5")), eq("AWATTAR"));
    verify(energyPriceRepository).upsertImport(eq(from.plusHours(1)), eq(from.plusHours(2)), eq(new BigDecimal("29.0")), eq("AWATTAR"));
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
    when(mockProvider.fetchPrices(any(PriceDirection.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());

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
    when(mockProvider.fetchPrices(any(PriceDirection.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
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
