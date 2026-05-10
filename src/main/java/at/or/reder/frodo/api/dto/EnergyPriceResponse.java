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
 * Hourly raw energy price from a provider.
 */
@Schema(description = "Hourly energy price from provider")
public record EnergyPriceResponse(

  @Schema(description = "Start of the hour (ISO 8601 UTC)", example = "2026-05-10T13:00:00")
  String startTime,

  @Schema(description = "End of the hour (ISO 8601 UTC)", example = "2026-05-10T14:00:00")
  String endTime,

  @Schema(description = "Import price in ct/kWh; null if not yet fetched")
  BigDecimal priceImportCt,

  @Schema(description = "Export price in ct/kWh; null if not yet fetched")
  BigDecimal priceExportCt,

  @Schema(description = "Provider that delivered import price", example = "MANUAL")
  String importSource,

  @Schema(description = "Provider that delivered export price", example = "AWATTAR")
  String exportSource,

  @Schema(description = "Record created at (ISO 8601)", example = "2026-05-10T12:55:00Z")
  String createdAt,

  @Schema(description = "Record last updated at (ISO 8601)", example = "2026-05-10T12:55:01Z")
  String updatedAt
) {
}
