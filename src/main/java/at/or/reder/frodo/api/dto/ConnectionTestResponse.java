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

/**
 * Response DTO for connection test results.
 *
 * @param success      whether the connection test succeeded
 * @param message      descriptive message about the test result
 * @param manufacturer device manufacturer (if identified, from Modbus FC 0x2B)
 * @param productCode  device product code (if identified)
 * @param modelName    device model name (if identified)
 * @param revision     firmware/software revision (if identified)
 * @param responseTimeMs connection response time in milliseconds
 * @param detectionMethod method used to detect device ("Device ID", "SunSpec signature", etc.)
 */
public record ConnectionTestResponse(
  boolean success,
  String message,
  String manufacturer,
  String productCode,
  String modelName,
  String revision,
  Long responseTimeMs,
  String detectionMethod
) {
  /**
   * Create a successful connection test response with device identification.
   */
  public static ConnectionTestResponse success(
    String manufacturer,
    String productCode,
    String modelName,
    String revision,
    long responseTimeMs,
    String detectionMethod
  ) {
    return new ConnectionTestResponse(
      true,
      "Connection successful",
      manufacturer,
      productCode,
      modelName,
      revision,
      responseTimeMs,
      detectionMethod
    );
  }

  /**
   * Create a successful connection test response without device identification.
   */
  public static ConnectionTestResponse successWithoutIdentification(long responseTimeMs, String detectionMethod) {
    return new ConnectionTestResponse(
      true,
      "Connection successful",
      null,
      null,
      null,
      null,
      responseTimeMs,
      detectionMethod
    );
  }

  /**
   * Create a failed connection test response.
   */
  public static ConnectionTestResponse failure(String message, long responseTimeMs) {
    return new ConnectionTestResponse(
      false,
      message,
      null,
      null,
      null,
      null,
      responseTimeMs,
      null
    );
  }
}
