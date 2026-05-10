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

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for updating cost control configuration.
 */
@Schema(description = "Cost control runtime configuration update")
public record CostControlConfigRequest(

  @Schema(description = "Provider ID for import prices (e.g. MANUAL)", example = "MANUAL")
  String importProviderId,

  @Schema(description = "Provider ID for export prices (e.g. AWATTAR)", example = "AWATTAR")
  String exportProviderId,

  @Schema(description = "Quartz cron for import price fetch", example = "0 55 * * * ?")
  String importFetchCron,

  @Schema(description = "Quartz cron for export price fetch", example = "0 55 * * * ?")
  String exportFetchCron,

  @Schema(description = "P_Grid sampling interval in seconds (1–300)", example = "15")
  int sampleIntervalSeconds,

  @Schema(description = "Dead-band threshold in watts", example = "10.0")
  double deadBandWatts,

  @Schema(description = "Retention period for hourly records in days (1–3650)", example = "365")
  int retentionHourlyDays,

  @Schema(description = "Retention period for monthly summaries in years (1–50)", example = "10")
  int retentionMonthlyYears
) {
}
