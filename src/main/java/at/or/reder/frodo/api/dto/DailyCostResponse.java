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

import java.math.BigDecimal;

/**
 * Pre-calculated daily cost summary.
 */
@Schema(description = "Daily cost summary")
public record DailyCostResponse(

  @Schema(description = "Day key (yyyy-MM-dd)", example = "2026-05-10")
  String day,

  @Schema(description = "Total kWh imported from grid this day", example = "24.8")
  BigDecimal totalImportKwh,

  @Schema(description = "Total kWh exported to grid this day", example = "8.3")
  BigDecimal totalExportKwh,

  @Schema(description = "Total import cost in EUR", example = "7.07")
  BigDecimal totalImportCostEur,

  @Schema(description = "Total export income in EUR", example = "0.60")
  BigDecimal totalExportIncomeEur,

  @Schema(description = "Total grid fees in EUR", example = "0.37")
  BigDecimal totalFeeEur,

  @Schema(description = "Net cost in EUR (importCost + fees, no fixed costs)", example = "7.44")
  BigDecimal netCostEur,

  @Schema(description = "Number of completed hourly cost records summed", example = "24")
  int hoursCalculated,

  @Schema(description = "Last updated timestamp (ISO 8601)", example = "2026-05-10T23:05:00Z")
  String updatedAt
) {
}
