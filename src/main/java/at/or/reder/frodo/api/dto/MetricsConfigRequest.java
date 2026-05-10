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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for creating or updating a metrics scraping configuration.
 *
 * @param scrapeIntervalSeconds  scrape interval in seconds (1-300)
 * @param enabled                whether scraping is enabled
 * @param storeToDatabase        whether to persist scraped values to DB
 * @param retentionDays          data retention period in days (1-3650)
 * @param parameters             list of parameter configurations
 */
public record MetricsConfigRequest(
  @NotNull(message = "Scrape interval is required")
  @Min(value = 1, message = "Scrape interval must be at least 1 second")
  @Max(value = 300, message = "Scrape interval cannot exceed 300 seconds")
  Integer scrapeIntervalSeconds,

  @NotNull(message = "Enabled status is required")
  Boolean enabled,

  Boolean storeToDatabase,

  @Min(value = 1, message = "Retention must be at least 1 day")
  @Max(value = 3650, message = "Retention cannot exceed 3650 days")
  Integer retentionDays,

  List<ParameterConfigRequest> parameters
) {
}
