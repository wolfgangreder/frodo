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
 * Response for a grid surcharge fee rule.
 */
@Schema(description = "Grid fee rule")
public record GridFeeResponse(

  @Schema(description = "Database ID", example = "1")
  long id,

  @Schema(description = "Timestamp when this fee became active (ISO 8601)", example = "2026-01-01T00:00:00")
  String validFrom,

  @Schema(description = "Fee calculation type: PERCENT, ABSOLUTE_ENERGY, or ABSOLUTE_TIME",
    example = "ABSOLUTE_ENERGY")
  String feeType,

  @Schema(description = "Fee value; PERCENT=%, ABSOLUTE_ENERGY=ct/kWh, ABSOLUTE_TIME=EUR/month",
    example = "1.5")
  BigDecimal feeValue,

  @Schema(description = "Which direction fee applies to: IMPORT, EXPORT, or BOTH", example = "BOTH")
  String appliesTo,

  @Schema(description = "Optional description", example = "Network surcharge")
  String description
) {
}
