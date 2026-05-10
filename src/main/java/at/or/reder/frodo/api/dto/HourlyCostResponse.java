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
 * Hourly cost and income calculation result.
 */
@Schema(description = "Hourly cost and energy data")
public record HourlyCostResponse(

  @Schema(description = "Start of the hour (ISO 8601 UTC)", example = "2026-05-10T13:00:00")
  String hourStart,

  @Schema(description = "End of the hour (ISO 8601 UTC)", example = "2026-05-10T14:00:00")
  String hourEnd,

  @Schema(description = "kWh imported from grid this hour", example = "1.234")
  BigDecimal importKwh,

  @Schema(description = "kWh exported to grid this hour", example = "0.567")
  BigDecimal exportKwh,

  @Schema(description = "Effective import price in ct/kWh", example = "28.5")
  BigDecimal priceImportCt,

  @Schema(description = "Effective export price in ct/kWh", example = "7.2")
  BigDecimal priceExportCt,

  @Schema(description = "Source of effective import price (TARIFF_WINDOW or provider ID)",
    example = "MANUAL")
  String importPriceSource,

  @Schema(description = "Source of effective export price (TARIFF_WINDOW or provider ID)",
    example = "AWATTAR")
  String exportPriceSource,

  @Schema(description = "Import cost in EUR", example = "0.352")
  BigDecimal importCostEur,

  @Schema(description = "Export income in EUR", example = "0.041")
  BigDecimal exportIncomeEur,

  @Schema(description = "Grid fee amount in EUR for this hour", example = "0.012")
  BigDecimal feeEur,

  @Schema(description = "Net cost in EUR (importCostEur - exportIncomeEur + feeEur)", example = "0.323")
  BigDecimal netCostEur
) {
}
