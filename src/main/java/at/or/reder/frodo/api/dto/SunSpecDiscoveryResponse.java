package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelDefinition;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Response DTO for SunSpec model chain discovery.
 *
 * @param deviceId      device ID
 * @param unitId        Modbus unit ID
 * @param baseAddress   Modbus address where the SunSpec signature was found
 * @param models        ordered list of discovered model summaries
 * @param modelCount    total number of discovered models
 * @param discoveryTime timestamp when discovery was performed
 */
public record SunSpecDiscoveryResponse(
  Long deviceId,
  int unitId,
  int baseAddress,
  List<ModelSummary> models,
  int modelCount,
  Instant discoveryTime
) {

  /**
   * Summary of a single discovered SunSpec model block.
   *
   * @param modelId        SunSpec model ID
   * @param name           human-readable model name
   * @param address        Modbus address of the model header
   * @param length         number of data registers (excluding header)
   * @param known          whether a field definition exists for this model
   * @param hasWritableFields whether the model contains writable fields
   */
  public record ModelSummary(
    int modelId,
    String name,
    int address,
    int length,
    boolean known,
    boolean hasWritableFields
  ) {

    /**
     * Creates a model summary from a discovered model block.
     *
     * @param block the discovered model block
     * @return model summary
     */
    public static ModelSummary fromBlock(SunSpecModelBlock block) {
      Optional<SunSpecModelDefinition> definition = SunSpecModelRegistry.get(block.modelId());
      boolean known = definition.isPresent();
      boolean writable = definition
        .map(SunSpecModelDefinition::hasWritableFields)
        .orElse(false);
      return new ModelSummary(
        block.modelId(),
        SunSpecConstants.modelName(block.modelId()),
        block.address(),
        block.length(),
        known,
        writable
      );
    }
  }

  /**
   * Creates a discovery response from a discovery result.
   *
   * @param deviceId device ID
   * @param unitId   Modbus unit ID
   * @param result   the discovery result
   * @return response DTO
   */
  public static SunSpecDiscoveryResponse fromResult(Long deviceId, int unitId,
                                                     SunSpecDiscoveryResult result) {
    List<ModelSummary> summaries = result.models().stream()
      .map(ModelSummary::fromBlock)
      .toList();
    return new SunSpecDiscoveryResponse(
      deviceId,
      unitId,
      result.baseAddress(),
      summaries,
      result.modelCount(),
      result.discoveryTime()
    );
  }
}
