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

import java.util.List;

/**
 * Metadata about a registered energy price provider.
 */
@Schema(description = "Energy price provider info")
public record ProviderInfoResponse(

  @Schema(description = "Unique provider ID used in configuration", example = "AWATTAR")
  String providerId,

  @Schema(description = "Human-readable display name", example = "aWATTar AT (spot market)")
  String displayName,

  @Schema(description = "Whether this provider supports automatic scheduled price fetch",
    example = "true")
  boolean autoFetchSupported,

  @Schema(description = "Directions this provider supports: IMPORT and/or EXPORT",
    example = "[\"EXPORT\"]")
  List<String> supportedDirections
) {
}
