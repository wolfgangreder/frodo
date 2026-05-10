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
 * Response for a tariff window (fixed-price time slot).
 */
@Schema(description = "Fixed-price tariff window")
public record TariffWindowResponse(

  @Schema(description = "Database ID", example = "1")
  long id,

  @Schema(description = "Price direction: IMPORT or EXPORT", example = "IMPORT")
  String direction,

  @Schema(description = "Tariff valid from date (inclusive, ISO 8601)", example = "2026-01-01")
  String validFrom,

  @Schema(description = "Tariff valid to date (exclusive, ISO 8601); null = still active")
  String validTo,

  @Schema(description = "Days of week this window applies to (comma-separated MON/TUE/…); null = all days",
    example = "MON,TUE,WED,THU,FRI")
  String daysOfWeek,

  @Schema(description = "Window start time within day (HH:mm:ss)", example = "07:00:00")
  String timeFrom,

  @Schema(description = "Window end time within day; 00:00:00 = end-of-day", example = "22:00:00")
  String timeTo,

  @Schema(description = "Fixed price in ct/kWh", example = "32.5")
  BigDecimal priceCt,

  @Schema(description = "Priority; highest wins when multiple windows match", example = "10")
  int priority,

  @Schema(description = "Optional description", example = "Peak import tariff weekdays")
  String description
) {
}
