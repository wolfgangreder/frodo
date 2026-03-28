package at.or.reder.frodo.modbus.sunspec;

import at.or.reder.frodo.modbus.ModbusTcpService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level service for SunSpec Modbus device interaction.
 *
 * <p>Provides model chain discovery, typed model data reading, and
 * write operations (when enabled). Uses {@link ModbusTcpService} for
 * the underlying Modbus TCP communication.</p>
 *
 * <p>Discovery results are cached per unit ID to avoid repeated
 * model chain scans.</p>
 */
@ApplicationScoped
public class SunSpecService {

  private static final Logger LOG = Logger.getLogger(SunSpecService.class);

  @Inject
  ModbusTcpService modbusTcpService;

  @ConfigProperty(name = "frodo.modbus.write-enabled", defaultValue = "false")
  boolean writeEnabled;

  /** Cached discovery results per unit ID. */
  private final Map<Integer, SunSpecDiscoveryResult> discoveryCache = new ConcurrentHashMap<>();

  /**
   * Discovers the SunSpec model chain on a device.
   *
   * <p>Reads the "SunS" signature at the default base address (40000),
   * then walks the model chain by reading model ID and length until
   * an end block (0xFFFF) is encountered.</p>
   *
   * @param unitId Modbus unit ID (1-247)
   * @return Uni resolving to the discovery result
   */
  public Uni<SunSpecDiscoveryResult> discover(int unitId) {
    return discoverAtAddress(unitId, SunSpecConstants.DEFAULT_BASE_ADDRESS)
      .onFailure().recoverWithUni(failure -> {
        LOG.debugf("SunSpec signature not found at default address %d, trying alternates",
          SunSpecConstants.DEFAULT_BASE_ADDRESS);
        return tryAlternateAddresses(unitId, 0);
      })
      .onItem().invoke(result -> {
        discoveryCache.put(unitId, result);
        LOG.infof("Discovered %d SunSpec models on unit %d at base address %d",
          result.modelCount(), unitId, result.baseAddress());
        for (SunSpecModelBlock model : result.models()) {
          LOG.infof("  Model %d (%s) at address %d, length %d",
            model.modelId(), SunSpecConstants.modelName(model.modelId()),
            model.address(), model.length());
        }
      });
  }

  /**
   * Gets the cached discovery result for a unit, or performs discovery if not cached.
   *
   * @param unitId Modbus unit ID
   * @return Uni resolving to the discovery result
   */
  public Uni<SunSpecDiscoveryResult> getOrDiscover(int unitId) {
    SunSpecDiscoveryResult cached = discoveryCache.get(unitId);
    if (cached != null) {
      return Uni.createFrom().item(cached);
    }
    return discover(unitId);
  }

  /**
   * Reads a specific SunSpec model from a device.
   *
   * <p>Performs discovery if needed, finds the model block, reads the
   * registers, and decodes them using the model definition.</p>
   *
   * @param unitId  Modbus unit ID
   * @param modelId SunSpec model ID to read
   * @return Uni resolving to the decoded model data
   */
  public Uni<SunSpecModelData> readModel(int unitId, int modelId) {
    return getOrDiscover(unitId)
      .onItem().transformToUni(discovery -> {
        Optional<SunSpecModelBlock> block = discovery.findModel(modelId);
        if (block.isEmpty()) {
          return Uni.createFrom().failure(
            new IllegalArgumentException(
              String.format("Model %d (%s) not found on unit %d",
                modelId, SunSpecConstants.modelName(modelId), unitId)));
        }
        return readModelBlock(unitId, block.get());
      });
  }

  /**
   * Reads the Common model (1) to get device identification via SunSpec.
   *
   * @param unitId Modbus unit ID
   * @return Uni resolving to common model data (Mn, Md, SN, Vr, etc.)
   */
  public Uni<SunSpecModelData> readCommonModel(int unitId) {
    return readModel(unitId, SunSpecConstants.MODEL_COMMON);
  }

  /**
   * Reads the inverter model (111/113 or 101/103) for real-time data.
   *
   * @param unitId Modbus unit ID
   * @return Uni resolving to inverter model data (current, voltage, power, etc.)
   */
  public Uni<SunSpecModelData> readInverterModel(int unitId) {
    return getOrDiscover(unitId)
      .onItem().transformToUni(discovery -> {
        Optional<SunSpecModelBlock> block = discovery.findInverterModel();
        if (block.isEmpty()) {
          return Uni.createFrom().failure(
            new IllegalArgumentException("No inverter model found on unit " + unitId));
        }
        return readModelBlock(unitId, block.get());
      });
  }

  /**
   * Reads all discovered models from a device.
   *
   * @param unitId Modbus unit ID
   * @return Uni resolving to a list of all decoded model data
   */
  public Uni<List<SunSpecModelData>> readAllModels(int unitId) {
    return getOrDiscover(unitId)
      .onItem().transformToUni(discovery -> {
        List<Uni<SunSpecModelData>> reads = new ArrayList<>();
        for (SunSpecModelBlock block : discovery.models()) {
          if (SunSpecModelRegistry.isKnown(block.modelId())) {
            reads.add(readModelBlock(unitId, block));
          } else {
            LOG.debugf("Skipping unknown model %d at address %d", block.modelId(), block.address());
          }
        }
        if (reads.isEmpty()) {
          return Uni.createFrom().item(List.of());
        }
        return Uni.join().all(reads).andFailFast()
          .onItem().transform(List::copyOf);
      });
  }

  /**
   * Writes a register value to a device. Only works if write operations are enabled.
   *
   * @param unitId  Modbus unit ID
   * @param address Modbus holding register address
   * @param value   value to write
   * @return Uni that completes when the write is done
   * @throws IllegalStateException if write operations are disabled
   */
  public Uni<Void> writeSingleRegister(int unitId, int address, int value) {
    if (!writeEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Write operations are disabled. Set frodo.modbus.write-enabled=true to enable."));
    }
    return modbusTcpService.writeSingleRegister(unitId, address, value);
  }

  /**
   * Writes multiple register values to a device. Only works if write operations are enabled.
   *
   * @param unitId    Modbus unit ID
   * @param startAddr starting register address
   * @param values    values to write
   * @return Uni that completes when the write is done
   * @throws IllegalStateException if write operations are disabled
   */
  public Uni<Void> writeMultipleRegisters(int unitId, int startAddr, int[] values) {
    if (!writeEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Write operations are disabled. Set frodo.modbus.write-enabled=true to enable."));
    }
    return modbusTcpService.writeMultipleRegisters(unitId, startAddr, values);
  }

  /**
   * Checks whether write operations are enabled.
   *
   * @return true if writes are allowed
   */
  public boolean isWriteEnabled() {
    return writeEnabled;
  }

  /**
   * Invalidates the cached discovery result for a unit.
   *
   * @param unitId Modbus unit ID
   */
  public void invalidateDiscovery(int unitId) {
    discoveryCache.remove(unitId);
    LOG.debugf("Invalidated SunSpec discovery cache for unit %d", unitId);
  }

  /**
   * Clears all cached discovery results.
   */
  public void clearDiscoveryCache() {
    discoveryCache.clear();
    LOG.info("Cleared SunSpec discovery cache");
  }

  // ---- Internal methods ----

  /**
   * Attempts discovery at the default base address.
   */
  private Uni<SunSpecDiscoveryResult> discoverAtAddress(int unitId, int baseAddress) {
    // Read signature registers (2 registers at base address)
    return modbusTcpService.readHoldingRegisters(unitId, baseAddress, 2)
      .onItem().transformToUni(signatureRegs -> {
        if (signatureRegs.length < 2) {
          return Uni.createFrom().failure(
            new IllegalStateException("Could not read SunSpec signature at address " + baseAddress));
        }
        long signature = ((signatureRegs[0] & 0xFFFFL) << 16) | (signatureRegs[1] & 0xFFFFL);
        if (signature != SunSpecConstants.SUNSPEC_SIGNATURE) {
          return Uni.createFrom().failure(
            new IllegalStateException(
              String.format("Invalid SunSpec signature at address %d: 0x%08X (expected 0x%08X)",
                baseAddress, signature, SunSpecConstants.SUNSPEC_SIGNATURE)));
        }

        LOG.debugf("Found SunSpec signature at address %d", baseAddress);
        // Walk the model chain starting after the signature
        return walkModelChain(unitId, baseAddress + 2, new ArrayList<>(), 0)
          .onItem().transform(models -> SunSpecDiscoveryResult.of(baseAddress, models));
      });
  }

  /**
   * Tries alternate base addresses sequentially.
   */
  private Uni<SunSpecDiscoveryResult> tryAlternateAddresses(int unitId, int index) {
    if (index >= SunSpecConstants.ALTERNATE_BASE_ADDRESSES.length) {
      return Uni.createFrom().failure(
        new IllegalStateException("SunSpec device not found at any known base address"));
    }
    int addr = SunSpecConstants.ALTERNATE_BASE_ADDRESSES[index];
    return discoverAtAddress(unitId, addr)
      .onFailure().recoverWithUni(f -> tryAlternateAddresses(unitId, index + 1));
  }

  /**
   * Recursively walks the SunSpec model chain by reading model ID + length headers.
   */
  private Uni<List<SunSpecModelBlock>> walkModelChain(int unitId, int address,
                                                       List<SunSpecModelBlock> accumulated,
                                                       int depth) {
    if (depth >= SunSpecConstants.MAX_MODEL_SCAN_DEPTH) {
      LOG.warnf("Exceeded maximum model scan depth (%d) at address %d",
        SunSpecConstants.MAX_MODEL_SCAN_DEPTH, address);
      return Uni.createFrom().item(accumulated);
    }

    // Read 2 registers: Model ID + Model Length
    return modbusTcpService.readHoldingRegisters(unitId, address, 2)
      .onItem().transformToUni(headerRegs -> {
        if (headerRegs.length < 2) {
          LOG.warnf("Could not read model header at address %d", address);
          return Uni.createFrom().item(accumulated);
        }

        int modelId = headerRegs[0] & 0xFFFF;
        int length = headerRegs[1] & 0xFFFF;

        // End block marker
        if (modelId == SunSpecConstants.END_MODEL_ID) {
          LOG.debugf("Found SunSpec end block at address %d", address);
          return Uni.createFrom().item(accumulated);
        }

        LOG.debugf("Found model %d (%s) at address %d, length %d",
          modelId, SunSpecConstants.modelName(modelId), address, length);

        accumulated.add(new SunSpecModelBlock(modelId, address, length));

        // Move to next model: address + 2 (header) + length (data)
        int nextAddress = address + 2 + length;
        return walkModelChain(unitId, nextAddress, accumulated, depth + 1);
      });
  }

  /**
   * Reads register data for a model block and decodes it.
   */
  private Uni<SunSpecModelData> readModelBlock(int unitId, SunSpecModelBlock block) {
    Optional<SunSpecModelDefinition> defOpt = SunSpecModelRegistry.get(block.modelId());
    if (defOpt.isEmpty()) {
      return Uni.createFrom().failure(
        new IllegalArgumentException("No definition for model " + block.modelId()));
    }

    SunSpecModelDefinition definition = defOpt.get();
    int dataAddress = block.dataAddress();
    int length = block.length();

    LOG.debugf("Reading model %d data: %d registers at address %d", block.modelId(), length, dataAddress);

    // Handle large models that exceed max registers per read
    if (length <= SunSpecConstants.MAX_REGISTERS_PER_READ) {
      return modbusTcpService.readHoldingRegisters(unitId, dataAddress, length)
        .onItem().transform(registers ->
          SunSpecModelDataDecoder.decode(definition, registers, block.address()));
    }

    // Split into multiple reads for large models
    return readLargeModel(unitId, dataAddress, length)
      .onItem().transform(registers ->
        SunSpecModelDataDecoder.decode(definition, registers, block.address()));
  }

  /**
   * Reads a large model by splitting into multiple FC 0x03 requests.
   */
  private Uni<int[]> readLargeModel(int unitId, int startAddress, int totalRegisters) {
    int[] combined = new int[totalRegisters];
    return readLargeModelChunk(unitId, startAddress, totalRegisters, combined, 0);
  }

  /**
   * Reads a chunk of a large model and recurses for the remaining registers.
   */
  private Uni<int[]> readLargeModelChunk(int unitId, int address, int remaining,
                                          int[] combined, int destOffset) {
    if (remaining <= 0) {
      return Uni.createFrom().item(combined);
    }

    int chunkSize = Math.min(remaining, SunSpecConstants.MAX_REGISTERS_PER_READ);
    return modbusTcpService.readHoldingRegisters(unitId, address, chunkSize)
      .onItem().transformToUni(chunk -> {
        System.arraycopy(chunk, 0, combined, destOffset, chunk.length);
        return readLargeModelChunk(unitId, address + chunkSize, remaining - chunkSize,
          combined, destOffset + chunkSize);
      });
  }
}
