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
 * Request body for {@code PUT /api/price-control}.
 */
@Schema(description = "Global price-controlled export setting")
public record PriceControlRequest(

  @Schema(description = "Whether global price-controlled export limiting is enabled",
          example = "true")
  boolean enabled,

  @Schema(description = "Allowed grid export above load + battery demand when price is negative, in Watts. "
          + "0 = strict zero-export. Default: 50 W.",
          example = "50")
  Integer exportToleranceWatts

) {}
