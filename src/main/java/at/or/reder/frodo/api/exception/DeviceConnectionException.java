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
 * Exception thrown when device connection fails.
 */
public class DeviceConnectionException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message the error message
   */
  public DeviceConnectionException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given message and cause.
   *
   * @param message the error message
   * @param cause   the underlying cause
   */
  public DeviceConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
