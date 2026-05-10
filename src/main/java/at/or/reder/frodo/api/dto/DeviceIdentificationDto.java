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

import at.or.reder.frodo.modbus.model.DeviceIdentification;

import java.time.Instant;

/**
 * Device identification information (Modbus FC 0x2B/0x0E MEI Type 14).
 *
 * @param vendorName          vendor name
 * @param productCode         product code
 * @param revision            major/minor revision
 * @param vendorUrl           vendor URL (optional)
 * @param productName         product name (optional)
 * @param modelName           model name (optional)
 * @param userApplicationName user application name (optional)
 * @param readTime            timestamp when this data was read
 */
public record DeviceIdentificationDto(
  String vendorName,
  String productCode,
  String revision,
  String vendorUrl,
  String productName,
  String modelName,
  String userApplicationName,
  Instant readTime
) {

  /**
   * Creates a DTO from a DeviceIdentification model object.
   *
   * @param deviceId  the device identification model
   * @param readTime  when this data was read
   * @return DTO for API response
   */
  public static DeviceIdentificationDto fromModel(DeviceIdentification deviceId, Instant readTime) {
    return new DeviceIdentificationDto(
      deviceId.vendorName(),
      deviceId.productCode(),
      deviceId.majorMinorRevision(),
      deviceId.vendorUrl(),
      deviceId.productName(),
      deviceId.modelName(),
      deviceId.userApplicationName(),
      readTime
    );
  }
}
