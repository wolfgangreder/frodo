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

import java.math.BigDecimal;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Market price entry response.
 */
@Schema(description = "Market price entry from aWATTar AT")
public record MarketPriceResponse(

  @Schema(description = "Start time of the price hour (ISO 8601)", example = "2024-01-15T14:00:00")
  String startTime,

  @Schema(description = "End time of the price hour (ISO 8601)", example = "2024-01-15T15:00:00")
  String endTime,

  @Schema(description = "Market price in ct/kWh (euro-cents per kilowatt-hour)", example = "4.25")
  BigDecimal priceCt,

  @Schema(description = "When this price was fetched", example = "2024-01-14T14:00:00Z")
  String fetchedAt
) {
}