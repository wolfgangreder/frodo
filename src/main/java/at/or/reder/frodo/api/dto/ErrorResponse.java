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

import java.time.Instant;

/**
 * Standard error response DTO.
 *
 * @param status    HTTP status code
 * @param error     error type/name
 * @param message   error message
 * @param timestamp when the error occurred
 * @param path      request path that caused the error
 */
public record ErrorResponse(
  int status,
  String error,
  String message,
  Instant timestamp,
  String path
) {
}
