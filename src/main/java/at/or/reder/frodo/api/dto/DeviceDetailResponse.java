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

import java.time.Instant;

/**
 * Detailed information for a single Modbus device including device identification.
 *
 * @param id                  device ID
 * @param name                device name
 * @param host                Modbus TCP host
 * @param port                Modbus TCP port
 * @param unitId              Modbus unit ID
 * @param enabled             whether device is enabled
 * @param description         device description (optional)
 * @param deviceType          device type (null for legacy devices)
 * @param autoDiscovered      whether device was auto-discovered
 * @param parentDeviceId      parent device ID (null for top-level devices)
 * @param identification      device identification data (null if not available)
 * @param lastUpdated         timestamp when identification was last updated
 * @param cached              whether identification data is from cache
 * @param connectionStatus    connection status (UNKNOWN, CONNECTED, FAILED)
 */
public record DeviceDetailResponse(
  Long id,
  String name,
  String host,
  int port,
  int unitId,
  boolean enabled,
  String description,
  DeviceType deviceType,
  boolean autoDiscovered,
  Long parentDeviceId,
  DeviceIdentificationDto identification,
  Instant lastUpdated,
  boolean cached,
  ConnectionStatus connectionStatus
) {
}
