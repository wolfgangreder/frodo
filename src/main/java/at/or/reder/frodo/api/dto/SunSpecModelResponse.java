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

import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelDefinition;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelRegistry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for SunSpec model data read from a device.
 *
 * <p>Contains the decoded field values as a map of field name to value,
 * plus metadata about the model itself.</p>
 *
 * @param deviceId      device ID
 * @param unitId        Modbus unit ID
 * @param modelId       SunSpec model ID
 * @param modelName     human-readable model name
 * @param address       Modbus address of this model instance
 * @param fields        ordered map of field name to decoded value (null for not-implemented fields)
 * @param fieldCount    total number of fields in this model
 * @param hasWritableFields whether the model contains writable fields
 * @param readTime      timestamp when the data was read
 */
public record SunSpecModelResponse(
  Long deviceId,
  int unitId,
  int modelId,
  String modelName,
  int address,
  Map<String, Object> fields,
  int fieldCount,
  boolean hasWritableFields,
  Instant readTime
) {

  /**
   * Creates a model response from decoded model data.
   *
   * @param deviceId device ID
   * @param unitId   Modbus unit ID
   * @param data     the decoded model data
   * @return response DTO
   */
  public static SunSpecModelResponse fromModelData(Long deviceId, int unitId,
                                                    SunSpecModelData data) {
    boolean writable = SunSpecModelRegistry.get(data.modelId())
      .map(SunSpecModelDefinition::hasWritableFields)
      .orElse(false);

    // Preserve insertion order from the model data values
    Map<String, Object> fields = new LinkedHashMap<>(data.values());

    return new SunSpecModelResponse(
      deviceId,
      unitId,
      data.modelId(),
      data.modelName(),
      data.address(),
      fields,
      fields.size(),
      writable,
      data.readTime()
    );
  }

  /**
   * Creates a list of model responses from multiple decoded model data instances.
   *
   * @param deviceId device ID
   * @param unitId   Modbus unit ID
   * @param dataList list of decoded model data
   * @return list of response DTOs
   */
  public static List<SunSpecModelResponse> fromModelDataList(Long deviceId, int unitId,
                                                              List<SunSpecModelData> dataList) {
    return dataList.stream()
      .map(data -> fromModelData(deviceId, unitId, data))
      .toList();
  }
}
