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
 * Status snapshot for a single GPIO pair.
 */
public record GpioPairStatusDto(
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
