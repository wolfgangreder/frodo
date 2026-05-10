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

package at.or.reder.frodo.api.exception;

/**
 * Exception thrown when a requested device is not found.
 */
public class DeviceNotFoundException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message the error message
   */
  public DeviceNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates a new exception for a device ID.
   *
   * @param deviceId the device ID that was not found
   * @return exception instance
   */
  public static DeviceNotFoundException forId(Long deviceId) {
    return new DeviceNotFoundException("Device not found: id=" + deviceId);
  }
}
