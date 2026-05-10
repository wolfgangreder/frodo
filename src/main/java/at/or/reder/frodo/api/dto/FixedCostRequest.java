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
 * Request body for creating a fixed/standing charge entry.
 */
@Schema(description = "Fixed cost create request")
public record FixedCostRequest(

  @Schema(description = "Energy flow direction: IMPORT, EXPORT, or BOTH", example = "BOTH", required = true)
  String direction,

  @Schema(description = "Date from which this cost is active (yyyy-MM-dd)", example = "2026-01-01", required = true)
  String validFrom,

  @Schema(description = "Monthly fixed cost in EUR", example = "12.00", required = true)
  BigDecimal monthlyCostEur,

  @Schema(description = "Optional description", example = "Grid connection fee")
  String description
) {
}
