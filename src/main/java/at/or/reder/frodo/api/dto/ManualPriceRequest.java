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
 * Request body for manually setting a price for one direction and one hour.
 */
@Schema(description = "Manual price entry request")
public record ManualPriceRequest(

  @Schema(description = "Hour start (ISO 8601 local, no zone, e.g. 2026-05-10T13:00:00)",
    example = "2026-05-10T13:00:00", required = true)
  String hourStart,

  @Schema(description = "Price in ct/kWh", example = "28.5", required = true)
  BigDecimal priceCt
) {
}
