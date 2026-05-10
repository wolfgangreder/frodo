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

import java.util.List;

/**
 * Response DTO listing all available SunSpec parameters for metrics collection.
 *
 * @param deviceId       device ID
 * @param parameters     list of available parameters grouped by model
 * @param discoveryBased true if the parameter list was obtained from live SunSpec
 *                       discovery (only models actually on the device), false if
 *                       built from the static registry (all known models listed)
 */
public record AvailableParametersResponse(
  Long deviceId,
  List<AvailableParameter> parameters,
  boolean discoveryBased
) {
}
