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

package at.or.reder.frodo.modbus.metrics;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a single semantic metric, loaded from the
 * {@code metrics-semantic-mapping.json} resource.
 *
 * <p>Each metric definition maps one or more SunSpec model fields
 * to a single Prometheus metric name with ISO base unit suffix.
 * Fields may carry additional tags (e.g. {@code phase=A},
 * {@code channel=1}) to distinguish multi-value series.</p>
 *
 * @param metricName   Prometheus metric name (e.g. {@code frodo_sunspec_ac_power_watts})
 * @param semanticName short semantic identifier (e.g. {@code ac_power})
 * @param description  human-readable description for Prometheus HELP text
 * @param unit         SunSpec unit string (e.g. "W", "A"), or null for unitless
 * @param baseUnit     ISO base unit suffix (e.g. "watts", "amperes"), or null
 * @param type         Prometheus metric type (always "gauge" for SunSpec data)
 * @param category     logical grouping (power, current, voltage, energy, etc.)
 * @param fields       list of SunSpec field mappings that contribute to this metric
 */
public record MetricMetadata(
  String metricName,
  String semanticName,
  String description,
  String unit,
  String baseUnit,
  String type,
  String category,
  List<FieldMapping> fields
) {

  /**
   * Maps a SunSpec model field to this metric, optionally with extra tags.
   *
   * @param modelIds SunSpec model IDs this mapping applies to (e.g. [101, 102, 103])
   * @param field    field name in the SunSpec model (e.g. "AphA", "module/1/DCA")
   * @param tags     additional Prometheus tags (e.g. {"phase": "A", "channel": "1"}),
   *                 empty map if none
   */
  public record FieldMapping(
    List<Integer> modelIds,
    String field,
    Map<String, String> tags
  ) {

    /**
     * Returns an empty map if tags is null (defensive for JSON deserialization).
     */
    public Map<String, String> tags() {
      return tags != null ? tags : Map.of();
    }
  }

  /**
   * Result of resolving a (modelId, fieldName) pair against the semantic mapping.
   *
   * @param metricName  Prometheus metric name
   * @param description HELP text for the metric
   * @param tags        additional tags to apply (phase, channel, line, quadrant, etc.)
   */
  public record ResolvedMetric(
    String metricName,
    String description,
    Map<String, String> tags
  ) {}
}
