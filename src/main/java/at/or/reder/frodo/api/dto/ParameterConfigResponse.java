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

import at.or.reder.frodo.modbus.entity.AggregationMode;
import at.or.reder.frodo.modbus.entity.MetricsParameterEntity;

/**
 * Response DTO for a single metrics parameter configuration.
 *
 * @param id               parameter entity ID
 * @param sunspecModelId   SunSpec model ID
 * @param fieldName        field name within the model
 * @param enabled          whether this parameter is enabled
 * @param customMetricName custom Prometheus metric name (may be null)
 * @param aggregationMode  how scraped values are reduced before DB writes
 */
public record ParameterConfigResponse(
  Long id,
  Integer sunspecModelId,
  String fieldName,
  Boolean enabled,
  String customMetricName,
  AggregationMode aggregationMode
) {

  /**
   * Creates a response from a parameter entity.
   *
   * @param entity parameter entity
   * @return response DTO
   */
  public static ParameterConfigResponse from(MetricsParameterEntity entity) {
    return new ParameterConfigResponse(
      entity.id,
      entity.sunspecModelId,
      entity.fieldName,
      entity.enabled,
      entity.customMetricName,
      entity.aggregationMode
    );
  }
}
