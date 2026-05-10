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

package at.or.reder.frodo.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for {@code GET /api/solar-api/status}.
 *
 * <p>Provides the current Solar API scraping status and latest power flow
 * values for display in the frontend dashboard.</p>
 */
public record SolarApiStatusResponse(
  boolean enabled,
  boolean active,
  int scrapeIntervalSeconds,
  int scrapeCount,
  int errorCount,
  Instant lastScrapeTime,
  SiteStatus site,
  List<InverterStatus> inverters,
  List<OhmpilotStatus> ohmpilots
) {

  /**
   * Site-level power flow values.
   */
  public record SiteStatus(
    Double gridPowerWatts,
    Double loadPowerWatts,
    Double pvPowerWatts,
    Double batteryPowerWatts,
    Double autonomyPercent,
    Double selfConsumptionPercent,
    String meterLocation,
    String mode,
    Boolean backupMode,
    Boolean batteryStandby
  ) {}

  /**
   * Per-inverter status values.
   */
  public record InverterStatus(
    String deviceId,
    Double powerWatts,
    Double energyTotalWattHours,
    Double batterySOCPercent,
    String batteryMode
  ) {}

  /**
   * Per-Ohmpilot status values.
   */
  public record OhmpilotStatus(
    String componentId,
    Double powerWatts,
    Double temperatureCelsius,
    String state
  ) {}
}
