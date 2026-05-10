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

import at.or.reder.frodo.modbus.entity.MetricsDataEntity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Response DTO for the latest metrics values for a device.
 *
 * <p>Groups the latest value per field key ({@code modelId_fieldName}).</p>
 *
 * @param deviceId  device ID
 * @param timestamp timestamp of the most recent data point
 * @param values    map of field key to latest value
 */
public record LatestMetricsResponse(
  Long deviceId,
  Instant timestamp,
  Map<String, Object> values
) {

  /**
   * Creates a response from a list of data entities.
   *
   * @param deviceId device ID
   * @param data     list of data entities (most recent first)
   * @return latest metrics response
   */
  public static LatestMetricsResponse from(Long deviceId, List<MetricsDataEntity> data) {
    Map<String, Object> values = new LinkedHashMap<>();
    Instant latest = null;

    // Group by field and get latest value for each
    Map<String, MetricsDataEntity> latestByField = data.stream()
      .collect(Collectors.toMap(
        e -> e.sunspecModelId + "_" + e.fieldName,
        e -> e,
        (e1, e2) -> e1.recordedAt.isAfter(e2.recordedAt) ? e1 : e2
      ));

    for (MetricsDataEntity e : latestByField.values()) {
      String key = e.sunspecModelId + "_" + e.fieldName;
      values.put(key, e.valueNumeric != null ? e.valueNumeric : e.valueString);
      if (latest == null || e.recordedAt.isAfter(latest)) {
        latest = e.recordedAt;
      }
    }

    return new LatestMetricsResponse(deviceId, latest, values);
  }
}
