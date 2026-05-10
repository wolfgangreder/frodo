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

import at.or.reder.frodo.modbus.model.DeviceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating or updating a Modbus device.
 *
 * @param name                      device name
 * @param host                      Modbus TCP host
 * @param port                      Modbus TCP port (1-65535)
 * @param unitId                    Modbus unit ID (0-247)
 * @param enabled                   whether device is enabled
 * @param description               device description (optional)
 * @param deviceType                device type (optional, auto-detected if null)
 * @param parentDeviceId            parent device ID (optional, for sub-devices)
 * @param connectionTimeoutSeconds  connection timeout in seconds (1-300)
 * @param requestTimeoutSeconds     request timeout in seconds (1-300)
 */
public record DeviceRequest(
  @NotBlank(message = "Device name is required")
  String name,

  @NotBlank(message = "Host is required")
  String host,

  @NotNull(message = "Port is required")
  @Min(value = 1, message = "Port must be between 1 and 65535")
  @Max(value = 65535, message = "Port must be between 1 and 65535")
  Integer port,

  @NotNull(message = "Unit ID is required")
  @Min(value = 0, message = "Unit ID must be between 0 and 247")
  @Max(value = 247, message = "Unit ID must be between 0 and 247")
  Integer unitId,

  @NotNull(message = "Enabled status is required")
  Boolean enabled,

  String description,

  DeviceType deviceType,

  Long parentDeviceId,

  @Min(value = 1, message = "Connection timeout must be at least 1 second")
  @Max(value = 300, message = "Connection timeout cannot exceed 300 seconds")
  Integer connectionTimeoutSeconds,

  @Min(value = 1, message = "Request timeout must be at least 1 second")
  @Max(value = 300, message = "Request timeout cannot exceed 300 seconds")
  Integer requestTimeoutSeconds
) {
}
