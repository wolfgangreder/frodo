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
import at.or.reder.frodo.cost.repository.HourlyCostRepository;
import at.or.reder.frodo.cost.entity.HourlyCostEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Readiness health check for the cost control subsystem.
 *
 * <p><b>Health Criteria:</b></p>
 * <ul>
 *   <li>UP (with note) if cost control is disabled — nothing to check</li>
 *   <li>DOWN if latest hourly cost record is older than
 *       {@code frodo.cost-control.health.max-age-hours} (default: 3)</li>
 *   <li>UP otherwise, reporting current price and data availability</li>
 * </ul>
 */
@Readiness
@ApplicationScoped
public class CostControlHealthCheck implements HealthCheck {

  private static final Logger LOG = Logger.getLogger(CostControlHealthCheck.class);
  private static final String HEALTH_CHECK_NAME = "cost-control";

  @Inject
  HourlyCostRepository hourlyCostRepository;

  @Inject
  EnergyPriceRepository energyPriceRepository;

  @ConfigProperty(name = "frodo.cost-control.enabled", defaultValue = "true")
  boolean costControlEnabled;

  @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "true")
  boolean datasourceActive;

  @ConfigProperty(name = "frodo.cost-control.health.max-age-hours", defaultValue = "3")
  int maxAgeHours;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named(HEALTH_CHECK_NAME);

    if (!costControlEnabled) {
      return builder.up()
        .withData("cost_control.enabled", false)
        .withData("reason", "Cost control is disabled")
        .build();
    }

    if (!datasourceActive) {
      return builder.down()
        .withData("cost_control.enabled", true)
        .withData("reason", "Datasource inactive")
        .build();
    }

    builder.withData("cost_control.enabled", true);

    LocalDateTime now = LocalDateTime.now();
    boolean stale = false;

    // Check latest hourly cost age
    try {
      Optional<HourlyCostEntity> latest = hourlyCostRepository.findLatest();
      if (latest.isPresent()) {
        long ageHours = Duration.between(latest.get().hourEnd, now).toHours();
        builder.withData("hourly_cost.last_hour_end", latest.get().hourEnd.toString())
          .withData("hourly_cost.age_hours", ageHours);
        stale = ageHours > maxAgeHours;
      } else {
        // No data yet — treat as OK (system may have just started)
        builder.withData("hourly_cost.age_hours", -1L);
      }
    } catch (Exception ex) {
      LOG.debugf("Could not read hourly cost for health check: %s", ex.getMessage());
      builder.withData("hourly_cost.error", ex.getMessage());
    }

    // Check current energy prices
    try {
      boolean hasImport = energyPriceRepository.findForTime(now)
        .map(e -> e.priceImportCt != null)
        .orElse(false);
      boolean hasExport = energyPriceRepository.findForTime(now)
        .map(e -> e.priceExportCt != null)
        .orElse(false);
      builder.withData("prices.import_available", hasImport)
        .withData("prices.export_available", hasExport);
    } catch (Exception ex) {
      LOG.debugf("Could not read energy prices for health check: %s", ex.getMessage());
      builder.withData("prices.error", ex.getMessage());
    }

    if (stale) {
      return builder.down()
        .withData("reason", "Hourly cost data is stale (>" + maxAgeHours + " hours old)")
        .build();
    }

    return builder.up().build();
  }
}
