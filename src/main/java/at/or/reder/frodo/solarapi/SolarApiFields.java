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

package at.or.reder.frodo.solarapi;

import java.util.List;

/**
 * Definitions for the Solar API site-level parameters that can be scraped
 * and stored via the metrics pipeline.
 *
 * <p>These parameters correspond to the site-level data from
 * {@code GetPowerFlowRealtimeData} and are exposed alongside SunSpec
 * parameters in the metrics configuration API.</p>
 *
 * <p>Field names match the keys returned by
 * {@link SolarApiMetricsService#getLastSiteValues()}.</p>
 */
public final class SolarApiFields {

  private SolarApiFields() {
    // Utility class
  }

  /**
   * Descriptor for a single Solar API site-level metric parameter.
   *
   * @param fieldName   key used in the values map (matches the Prometheus metric suffix)
   * @param units       measurement unit (e.g. "W"), or empty string if dimensionless
   * @param description human-readable description shown in the metrics config UI
   * @param metricName  Prometheus metric name published by {@link SolarApiMetricsService}
   */
  public record FieldDescriptor(
    String fieldName,
    String units,
    String description,
    String metricName
  ) {
  }

  /** Ordered list of all scrapeable Solar API site-level fields. */
  public static final List<FieldDescriptor> SITE_FIELDS = List.of(
    new FieldDescriptor(
      "grid_power_watts",
      "W",
      "Grid power (positive = import from grid, negative = export to grid)",
      "frodo_solar_site_grid_power_watts"
    ),
    new FieldDescriptor(
      "load_power_watts",
      "W",
      "Site load / consumption power",
      "frodo_solar_site_load_power_watts"
    ),
    new FieldDescriptor(
      "pv_power_watts",
      "W",
      "PV production power",
      "frodo_solar_site_pv_power_watts"
    ),
    new FieldDescriptor(
      "battery_power_watts",
      "W",
      "Battery power (positive = charging, negative = discharging)",
      "frodo_solar_site_battery_power_watts"
    ),
    new FieldDescriptor(
      "autonomy_ratio",
      "",
      "Relative autonomy ratio (0 = fully grid-dependent, 1 = fully self-sufficient)",
      "frodo_solar_site_autonomy_ratio"
    ),
    new FieldDescriptor(
      "self_consumption_ratio",
      "",
      "Relative self-consumption ratio (0 = all PV exported, 1 = all PV consumed locally)",
      "frodo_solar_site_self_consumption_ratio"
    )
  );
}
