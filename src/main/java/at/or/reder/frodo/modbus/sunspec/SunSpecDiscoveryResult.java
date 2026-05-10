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

package at.or.reder.frodo.modbus.sunspec;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of a SunSpec model chain discovery scan.
 *
 * <p>Contains the base address where the "SunS" signature was found
 * and a list of all discovered model blocks in chain order.</p>
 *
 * @param baseAddress   Modbus address where the SunSpec signature was found
 * @param models        ordered list of discovered model blocks
 * @param discoveryTime timestamp when discovery was performed
 */
public record SunSpecDiscoveryResult(
  int baseAddress,
  List<SunSpecModelBlock> models,
  Instant discoveryTime
) {

  /**
   * Creates a discovery result with the current timestamp.
   *
   * @param baseAddress base address
   * @param models      model blocks
   * @return discovery result
   */
  public static SunSpecDiscoveryResult of(int baseAddress, List<SunSpecModelBlock> models) {
    return new SunSpecDiscoveryResult(
      baseAddress,
      Collections.unmodifiableList(models),
      Instant.now()
    );
  }

  /**
   * Finds a model block by model ID.
   *
   * @param modelId SunSpec model ID to find
   * @return Optional containing the model block, or empty if not present
   */
  public Optional<SunSpecModelBlock> findModel(int modelId) {
    return models.stream()
      .filter(m -> m.modelId() == modelId)
      .findFirst();
  }

  /**
   * Finds any inverter model block (101-103 or 111-113).
   *
   * @return Optional containing the inverter model block
   */
  public Optional<SunSpecModelBlock> findInverterModel() {
    return models.stream()
      .filter(m -> SunSpecConstants.isInverterModel(m.modelId()))
      .findFirst();
  }

  /**
   * Finds an inverter model block matching the preferred format.
   *
   * <p>First searches for an inverter model matching the given format
   * (e.g. Int&amp;SF models 101-103, or Float models 111-113). If no
   * match is found for the preferred format, falls back to any
   * inverter model present in the chain.</p>
   *
   * @param preferredFormat the preferred model format (INT_SF or FLOAT)
   * @return Optional containing the matching inverter model block
   */
  public Optional<SunSpecModelBlock> findInverterModel(SunSpecModelFormat preferredFormat) {
    Optional<SunSpecModelBlock> preferred = models.stream()
      .filter(m -> preferredFormat.matchesInverterModel(m.modelId()))
      .findFirst();
    if (preferred.isPresent()) {
      return preferred;
    }
    // Fall back to any inverter model
    return findInverterModel();
  }

  /**
   * Finds any meter model block (201-204 or 211-214).
   *
   * @return Optional containing the meter model block
   */
  public Optional<SunSpecModelBlock> findMeterModel() {
    return models.stream()
      .filter(m -> SunSpecConstants.isMeterModel(m.modelId()))
      .findFirst();
  }

  /**
   * Finds a meter model block matching the preferred format.
   *
   * <p>First searches for a meter model matching the given format
   * (e.g. Int&amp;SF models 201-204, or Float models 211-214). If no
   * match is found for the preferred format, falls back to any
   * meter model present in the chain.</p>
   *
   * @param preferredFormat the preferred model format (INT_SF or FLOAT)
   * @return Optional containing the matching meter model block
   */
  public Optional<SunSpecModelBlock> findMeterModel(SunSpecModelFormat preferredFormat) {
    Optional<SunSpecModelBlock> preferred = models.stream()
      .filter(m -> preferredFormat.matchesMeterModel(m.modelId()))
      .findFirst();
    if (preferred.isPresent()) {
      return preferred;
    }
    // Fall back to any meter model
    return findMeterModel();
  }

  /**
   * Checks whether a specific model is present.
   *
   * @param modelId SunSpec model ID
   * @return true if the model was discovered
   */
  public boolean hasModel(int modelId) {
    return findModel(modelId).isPresent();
  }

  /**
   * Checks whether any of the given model IDs is present.
   *
   * @param modelIds SunSpec model IDs to check
   * @return true if at least one of the given models was discovered
   */
  public boolean hasAnyModel(int... modelIds) {
    for (int modelId : modelIds) {
      if (hasModel(modelId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a list of all discovered model IDs.
   *
   * @return unmodifiable list of model IDs in chain order
   */
  public List<Integer> modelIds() {
    return models.stream()
      .map(SunSpecModelBlock::modelId)
      .toList();
  }

  /**
   * Returns the number of discovered models (excluding the end block).
   *
   * @return model count
   */
  public int modelCount() {
    return models.size();
  }
}
