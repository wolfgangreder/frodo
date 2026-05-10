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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for triggering device discovery on a Modbus TCP gateway.
 *
 * @param host         Modbus TCP host to scan
 * @param port         Modbus TCP port (1-65535, default 502)
 * @param unitIdRanges unit ID ranges to scan (e.g. "1,200-203"), null for configured defaults
 * @param autoSave     whether to automatically save discovered devices to the database
 */
public record DeviceDiscoveryRequest(
  @NotBlank(message = "Host is required")
  String host,

  @Min(value = 1, message = "Port must be between 1 and 65535")
  @Max(value = 65535, message = "Port must be between 1 and 65535")
  Integer port,

  String unitIdRanges,

  Boolean autoSave
) {

  /**
   * Returns the port to use, defaulting to 502 if not specified.
   *
   * @return the port number
   */
  public int effectivePort() {
    return port != null ? port : 502;
  }

  /**
   * Returns whether auto-save is enabled, defaulting to false if not specified.
   *
   * @return true if discovered devices should be automatically saved
   */
  public boolean effectiveAutoSave() {
    return autoSave != null && autoSave;
  }
}
