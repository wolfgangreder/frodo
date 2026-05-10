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
 * Response DTO for device discovery results.
 *
 * @param host           hostname or IP that was scanned
 * @param port           port that was scanned
 * @param devicesFound   total number of devices discovered
 * @param devices        list of discovered devices
 * @param savedDeviceIds IDs of saved devices (empty if autoSave was false)
 */
public record DeviceDiscoveryResponse(
  String host,
  int port,
  int devicesFound,
  List<DiscoveredDeviceDto> devices,
  List<Long> savedDeviceIds
) {

  /**
   * Creates a response without saved device IDs (discovery only, no auto-save).
   *
   * @param host    scanned host
   * @param port    scanned port
   * @param devices discovered devices
   * @return response without saved IDs
   */
  public static DeviceDiscoveryResponse discoveryOnly(String host, int port,
                                                       List<DiscoveredDeviceDto> devices) {
    return new DeviceDiscoveryResponse(host, port, devices.size(), devices, List.of());
  }

  /**
   * Creates a response with saved device IDs (auto-save enabled).
   *
   * @param host           scanned host
   * @param port           scanned port
   * @param devices        discovered devices
   * @param savedDeviceIds IDs of saved/updated device entities
   * @return response with saved IDs
   */
  public static DeviceDiscoveryResponse withSavedDevices(String host, int port,
                                                          List<DiscoveredDeviceDto> devices,
                                                          List<Long> savedDeviceIds) {
    return new DeviceDiscoveryResponse(host, port, devices.size(), devices, savedDeviceIds);
  }
}
