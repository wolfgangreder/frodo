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

package at.or.reder.frodo.health;

import at.or.reder.frodo.cost.repository.EnergyPriceRepository;
import at.or.reder.frodo.cost.repository.MonthlyCostRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registers Micrometer/Prometheus metrics for energy cost control.
 *
 * <p><b>Registered Gauges:</b></p>
 * <ul>
 *   <li>{@code frodo.cost.import_price_ct} — current import price in ct/kWh</li>
 *   <li>{@code frodo.cost.export_price_ct} — current export price in ct/kWh</li>
 *   <li>{@code frodo.cost.monthly_net_cost_eur} — net cost in EUR for current month</li>
 *   <li>{@code frodo.cost.monthly_import_kwh} — total kWh imported in current month</li>
 *   <li>{@code frodo.cost.monthly_export_kwh} — total kWh exported in current month</li>
 * </ul>
 */
@ApplicationScoped
public class CostMetrics {

  private static final Logger LOG = Logger.getLogger(CostMetrics.class);
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

  @Inject
  MeterRegistry registry;

  @Inject
  EnergyPriceRepository energyPriceRepository;

  @Inject
  MonthlyCostRepository monthlyCostRepository;

  @ConfigProperty(name = "frodo.cost-control.enabled", defaultValue = "true")
  boolean costControlEnabled;

  @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "true")
  boolean datasourceActive;

  void onStart(@Observes StartupEvent event) {
    if (!costControlEnabled || !datasourceActive) {
      LOG.debug("Cost metrics not registered: cost-control disabled or datasource inactive");
      return;
    }

    Gauge.builder("frodo.cost.import_price_ct", this, CostMetrics::currentImportPrice)
      .description("Current import price in ct/kWh")
      .register(registry);

    Gauge.builder("frodo.cost.export_price_ct", this, CostMetrics::currentExportPrice)
      .description("Current export price in ct/kWh")
      .register(registry);

    Gauge.builder("frodo.cost.monthly_net_cost_eur", this, CostMetrics::monthlyNetCost)
      .description("Net cost in EUR for the current calendar month")
      .register(registry);

    Gauge.builder("frodo.cost.monthly_import_kwh", this, CostMetrics::monthlyImportKwh)
      .description("Total kWh imported from grid in the current calendar month")
      .register(registry);

    Gauge.builder("frodo.cost.monthly_export_kwh", this, CostMetrics::monthlyExportKwh)
      .description("Total kWh exported to grid in the current calendar month")
      .register(registry);

    LOG.info("Cost control metrics registered");
  }

  // ---- Gauge value suppliers ---------------------------------------------

  private double currentImportPrice() {
    try {
      return energyPriceRepository.findForTime(LocalDateTime.now())
        .map(e -> e.priceImportCt != null ? e.priceImportCt.doubleValue() : Double.NaN)
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch current import price: %s", ex.getMessage());
      return Double.NaN;
    }
  }

  private double currentExportPrice() {
    try {
      return energyPriceRepository.findForTime(LocalDateTime.now())
        .map(e -> e.priceExportCt != null ? e.priceExportCt.doubleValue() : Double.NaN)
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch current export price: %s", ex.getMessage());
      return Double.NaN;
    }
  }

  private double monthlyNetCost() {
    try {
      String yearMonth = LocalDateTime.now().format(YEAR_MONTH_FMT);
      return monthlyCostRepository.findByYearMonth(yearMonth)
        .map(e -> e.netCostEur.doubleValue())
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch monthly net cost: %s", ex.getMessage());
      return Double.NaN;
    }
  }

  private double monthlyImportKwh() {
    try {
      String yearMonth = LocalDateTime.now().format(YEAR_MONTH_FMT);
      return monthlyCostRepository.findByYearMonth(yearMonth)
        .map(e -> e.totalImportKwh.doubleValue())
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch monthly import kWh: %s", ex.getMessage());
      return Double.NaN;
    }
  }

  private double monthlyExportKwh() {
    try {
      String yearMonth = LocalDateTime.now().format(YEAR_MONTH_FMT);
      return monthlyCostRepository.findByYearMonth(yearMonth)
        .map(e -> e.totalExportKwh.doubleValue())
        .orElse(Double.NaN);
    } catch (Exception ex) {
      LOG.debugf("Could not fetch monthly export kWh: %s", ex.getMessage());
      return Double.NaN;
    }
  }
}
