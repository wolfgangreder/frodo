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

import at.or.reder.frodo.TimeUtil;
import at.or.reder.frodo.cost.repository.HourlyEnergyRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integrates P_Grid samples into hourly kWh totals using trapezoidal method.
 *
 * <p>Called by {@link at.or.reder.frodo.solarapi.SolarApiMetricsService} at each
 * scrape interval. Exposes current-hour accumulated import/export as Prometheus
 * gauges; flushes to DB on hour boundary.</p>
 *
 * <h3>Prometheus metrics (virtual, Solar API group)</h3>
 * <ul>
 *   <li>{@code frodo_solar_site_grid_energy_import_kwh} — current hour grid import</li>
 *   <li>{@code frodo_solar_site_grid_energy_export_kwh} — current hour grid export</li>
 * </ul>
 *
 * <p>Positive P_Grid = import from grid; negative = export to grid.</p>
 */
@ApplicationScoped
public class EnergyIntegrationService {

  private static final Logger LOG = Logger.getLogger(EnergyIntegrationService.class);

  /** Dead-band fallback used when config cannot be loaded. */
  private static final double DEFAULT_DEAD_BAND_WATTS = 10.0;

  @Inject
  MeterRegistry meterRegistry;

  @Inject
  HourlyEnergyRepository hourlyEnergyRepository;

  @Inject
  CostCalculationService costCalculationService;

  @Inject
  CostControlConfigService configService;

  /**
   * Checked only at flush time (hour boundary), not at init or scrape.
   * Allows Prometheus gauges to register and update even when DB disabled
   * (e.g. in tests). Gauges show NaN when no data; DB flush skipped.
   */
  @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "true")
  boolean datasourceActive;

  // Current hour tracking
  private int currentHour = -1; // -1 = startup, discard first partial hour
  private Instant lastSampleTime;
  private double lastSamplePowerW;
  private int sampleCount;

  // Running totals for current hour (Wh)
  private double importWh;
  private double exportWh;

  // Prometheus gauge values (package-private for testing)
  final AtomicReference<Double> importKwhGauge = new AtomicReference<>(Double.NaN);
  final AtomicReference<Double> exportKwhGauge = new AtomicReference<>(Double.NaN);

  @PostConstruct
  void init() {
    Gauge.builder("frodo_solar_site_grid_energy_import_kwh", importKwhGauge, AtomicReference::get)
      .description("Current hour grid energy import in kWh (trapezoidal integration)")
      .register(meterRegistry);

    Gauge.builder("frodo_solar_site_grid_energy_export_kwh", exportKwhGauge, AtomicReference::get)
      .description("Current hour grid energy export in kWh (trapezoidal integration)")
      .register(meterRegistry);

    LOG.info("Energy integration gauges registered");
  }

  /**
   * Called by SolarApiMetricsService at each scrape with current P_Grid value.
   *
   * <p>Performs trapezoidal integration step, updates Prometheus gauges,
   * flushes to DB on hour boundary.</p>
   *
   * @param powerW grid power in watts (positive = import, negative = export), may be null
   */
  public void onSolarScrape(Double powerW) {
    if (powerW == null) {
      return;
    }

    double deadBand = getDeadBandWatts();
    double filteredPowerW = Math.abs(powerW) <= deadBand ? 0.0 : powerW;

    Instant now = Instant.now();
    int thisHour = TimeUtil.toUtcLdt(now).getHour();

    synchronized (this) {
      if (currentHour == -1) {
        // Startup: record hour, store first sample, discard partial hour
        currentHour = thisHour;
        lastSampleTime = now;
        lastSamplePowerW = filteredPowerW;
        sampleCount = 1;
        LOG.debug("Energy integration: startup, discarding first partial hour");
        return;
      }

      if (thisHour != currentHour) {
        // Hour boundary: integrate to boundary, flush, reset
        flushCurrentHour(now, filteredPowerW);
        currentHour = thisHour;
        lastSampleTime = now;
        lastSamplePowerW = filteredPowerW;
        sampleCount = 1;
        importWh = 0.0;
        exportWh = 0.0;
        return;
      }

      // Same hour: trapezoidal integration step
      if (lastSampleTime != null) {
        double avgPowerW = (lastSamplePowerW + filteredPowerW) / 2.0;
        double dtSeconds = (now.toEpochMilli() - lastSampleTime.toEpochMilli()) / 1000.0;
        double energyWh = avgPowerW * dtSeconds / 3600.0;

        if (energyWh > 0) {
          importWh += energyWh;
        } else {
          exportWh += Math.abs(energyWh);
        }
      }

      lastSampleTime = now;
      lastSamplePowerW = filteredPowerW;
      sampleCount++;

      // Update Prometheus gauges
      importKwhGauge.set(importWh / 1000.0);
      exportKwhGauge.set(exportWh / 1000.0);
    }
  }

  /**
   * Flushes accumulated energy to DB at hour boundary.
   * Called while holding lock.
   *
   * @param boundaryInstant      the instant of the first sample in the new hour
   * @param firstNewSamplePowerW grid power of the first sample in the new hour (W)
   */
  private void flushCurrentHour(Instant boundaryInstant, double firstNewSamplePowerW) {
    // Final trapezoidal step to boundary
    if (lastSampleTime != null) {
      double avgPowerW = (lastSamplePowerW + firstNewSamplePowerW) / 2.0;
      double dtSeconds = (boundaryInstant.toEpochMilli() - lastSampleTime.toEpochMilli()) / 1000.0;
      double energyWh = avgPowerW * dtSeconds / 3600.0;

      if (energyWh > 0) {
        importWh += energyWh;
      } else {
        exportWh += Math.abs(energyWh);
      }
    }

    if (sampleCount < 2) {
      LOG.debugf("Hour %d: too few samples (%d), skipping flush", currentHour, sampleCount);
      return;
    }

    if (!datasourceActive) {
      LOG.debug("Datasource inactive, skipping DB flush");
      return;
    }

    // Compute hour boundaries
    LocalDateTime hourStartLdt = TimeUtil.toUtcLdt(lastSampleTime)
      .withMinute(0).withSecond(0).withNano(0);
    LocalDateTime hourEndLdt = hourStartLdt.plusHours(1);

    BigDecimal importKwhBD = BigDecimal.valueOf(importWh / 1000.0).setScale(6, RoundingMode.HALF_UP);
    BigDecimal exportKwhBD = BigDecimal.valueOf(exportWh / 1000.0).setScale(6, RoundingMode.HALF_UP);

    LOG.debugf("Energy flush: %s import=%.4f kWh export=%.4f kWh samples=%d",
      hourStartLdt, importKwhBD.doubleValue(), exportKwhBD.doubleValue(), sampleCount);

    try {
      hourlyEnergyRepository.upsert(hourStartLdt, hourEndLdt, importKwhBD, exportKwhBD, sampleCount);
      costCalculationService.calculateHourlyCost(hourStartLdt);
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to persist hourly energy for %s", hourStartLdt);
      return;
    }
    try {
      costCalculationService.updateDailyCost(hourStartLdt);
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to update daily cost for %s", hourStartLdt);
    }
    try {
      costCalculationService.updateMonthlyCost(hourStartLdt);
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to update monthly cost for %s", hourStartLdt);
    }
  }

  /**
   * Returns the dead-band threshold in watts from config.
   *
   * <p>Power readings with absolute value at or below this threshold are treated as
   * zero to avoid integrating sensor noise. Falls back to
   * {@link #DEFAULT_DEAD_BAND_WATTS} if the config cannot be loaded.</p>
   *
   * @return dead-band threshold in watts; always &gt;= 0
   */
  private double getDeadBandWatts() {
    try {
      return configService.load().deadBandWatts;
    } catch (Exception ex) {
      return DEFAULT_DEAD_BAND_WATTS;
    }
  }
}
