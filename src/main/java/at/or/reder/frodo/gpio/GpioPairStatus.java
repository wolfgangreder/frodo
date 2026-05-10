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

package at.or.reder.frodo.gpio;

/**
 * Status snapshot for a single GPIO pair.
 *
 * <p>Used in REST responses and health checks to report the current state
 * of a configured GPIO pair.</p>
 *
 * @param name                pair name as defined in application.properties
 * @param available           this pair is initialised and its lines are open
 * @param outputPin           BCM pin number of the output line
 * @param outputPinState      current output pin level ({@code null} if unavailable)
 * @param outputManualOverride {@code true} when a manual test override is active
 * @param inputPin            BCM pin number of the input line
 * @param inputBias           configured input bias ("PULL_UP", "PULL_DOWN", or "DISABLE")
 * @param inputPinState       current input pin level ({@code null} if unavailable)
 * @param externalModeActive  derived: input pin is at its active level
 * @param assignedDeviceId    device this pair is currently assigned to ({@code null} = unassigned)
 * @param errorMessage        non-null when {@code available=false}
 */
public record GpioPairStatus(
  String name,
  boolean available,
  int outputPin,
  Boolean outputPinState,
  boolean outputManualOverride,
  int inputPin,
  String inputBias,
  Boolean inputPinState,
  boolean externalModeActive,
  Long assignedDeviceId,
  String errorMessage
) {}
