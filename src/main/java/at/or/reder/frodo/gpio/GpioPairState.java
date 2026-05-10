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
 * Runtime state for one initialised GPIO pair.
 *
 * <p>Created during startup when both output and input lines are successfully
 * opened. Immutable after construction; concurrent reads are safe.</p>
 *
 * @param name             pair name from configuration
 * @param outputPin        BCM pin number of the output line
 * @param outputBlockLevel pin level when export is blocked ("HIGH" or "LOW")
 * @param inputPin         BCM pin number of the input line
 * @param inputActiveLevel pin level when external mode is active ("HIGH" or "LOW")
 * @param outputLineFd     kernel file descriptor for the output line
 * @param inputLineFd      kernel file descriptor for the input line
 */
record GpioPairState(
  String name,
  int outputPin,
  String outputBlockLevel,
  int inputPin,
  String inputActiveLevel,
  int outputLineFd,
  int inputLineFd
) {}
