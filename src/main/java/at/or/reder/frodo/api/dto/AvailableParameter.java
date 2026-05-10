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

/**
 * DTO describing a single SunSpec parameter available for metrics collection.
 *
 * @param modelId     SunSpec model ID (e.g. 113, 124)
 * @param modelName   human-readable model name (e.g. "Inverter (Three Phase, Float)")
 * @param fieldName   field name within the model (e.g. "W", "ChaState")
 * @param units       measurement units (e.g. "W", "V", "A"), may be null
 * @param description human-readable description of the field
 * @param metricName  Prometheus metric name that will be used for this parameter
 *                    (e.g. "frodo_sunspec_ac_power_watts"), resolved from the
 *                    semantic mapping or generated as a fallback
 */
public record AvailableParameter(
  Integer modelId,
  String modelName,
  String fieldName,
  String units,
  String description,
  String metricName
) {
}
