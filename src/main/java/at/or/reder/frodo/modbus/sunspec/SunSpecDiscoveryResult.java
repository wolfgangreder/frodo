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
   * Checks whether a specific model is present.
   *
   * @param modelId SunSpec model ID
   * @return true if the model was discovered
   */
  public boolean hasModel(int modelId) {
    return findModel(modelId).isPresent();
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
