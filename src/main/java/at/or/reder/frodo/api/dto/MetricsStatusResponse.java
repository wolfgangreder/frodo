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

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;

import java.time.Instant;

/**
 * Response DTO for the current metrics scraping status of a device.
 *
 * @param deviceId              device ID
 * @param configured            whether metrics config exists
 * @param enabled               whether scraping is enabled
 * @param scrapeIntervalSeconds scrape interval
 * @param lastScrapeTime        timestamp of last scrape
 * @param lastScrapeStatus      status of last scrape
 * @param lastErrorMessage      error message from last failure
 * @param enabledParameterCount number of enabled parameters
 */
public record MetricsStatusResponse(
  Long deviceId,
  Boolean configured,
  Boolean enabled,
  Integer scrapeIntervalSeconds,
  Instant lastScrapeTime,
  String lastScrapeStatus,
  String lastErrorMessage,
  Integer enabledParameterCount
) {

  /**
   * Creates a response for a device that has no metrics config.
   *
   * @param deviceId device ID
   * @return status response indicating not configured
   */
  public static MetricsStatusResponse notConfigured(Long deviceId) {
    return new MetricsStatusResponse(deviceId, false, false, null, null, null, null, 0);
  }

  /**
   * Creates a response from a config entity.
   *
   * @param entity metrics config entity
   * @return status response
   */
  public static MetricsStatusResponse from(MetricsConfigEntity entity) {
    int enabledCount = (int) entity.parameters.stream()
      .filter(p -> p.enabled)
      .count();
    return new MetricsStatusResponse(
      entity.device.id,
      true,
      entity.enabled,
      entity.scrapeIntervalSeconds,
      entity.lastScrapeTime,
      entity.lastScrapeStatus != null ? entity.lastScrapeStatus.name() : null,
      entity.lastErrorMessage,
      enabledCount
    );
  }
}
