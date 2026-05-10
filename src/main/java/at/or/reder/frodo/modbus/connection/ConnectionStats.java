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

package at.or.reder.frodo.modbus.connection;

import java.time.Instant;

/**
 * Statistics for monitoring a Modbus connection.
 *
 * @param state            current connection state
 * @param queueSize        number of requests waiting in queue
 * @param lastSuccessTime  timestamp of last successful request (null if none)
 * @param totalRequests    total number of requests executed
 * @param failedRequests   total number of failed requests
 */
public record ConnectionStats(
  ConnectionState state,
  int queueSize,
  Instant lastSuccessTime,
  long totalRequests,
  long failedRequests
) {}
