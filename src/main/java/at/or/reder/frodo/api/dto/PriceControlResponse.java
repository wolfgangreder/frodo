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
 * Response for {@code GET /api/price-control} and {@code PUT /api/price-control}.
 */
@Schema(description = "Global price-controlled export setting with current market price state")
public record PriceControlResponse(

  @Schema(description = "Whether global price-controlled export limiting is enabled",
          example = "true")
  boolean enabled,

  @Schema(description = "Allowed grid export above load + battery demand when price is negative, in Watts",
          example = "50")
  int exportToleranceWatts,

  @Schema(description = "Current aWATTar AT market price in ct/kWh, or null if not yet available",
          example = "-1.25")
  BigDecimal currentPriceCt,

  @Schema(description = "True when price control is enabled AND the current price is negative "
          + "(i.e. the scheduler is actively limiting export right now)",
          example = "false")
  boolean currentlyBlocking

) {}
