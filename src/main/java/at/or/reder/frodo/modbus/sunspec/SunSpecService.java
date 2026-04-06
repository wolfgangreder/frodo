package at.or.reder.frodo.modbus.sunspec;

import at.or.reder.frodo.modbus.ModbusTcpService;
import at.or.reder.frodo.modbus.connection.DeviceAddress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * High-level service for SunSpec Modbus device interaction.
 *
 * <p>Provides model chain discovery, typed model data reading, and
 * write operations (when enabled). Uses {@link ModbusTcpService} for
 * the underlying Modbus TCP communication.</p>
 *
 * <p>All public methods accept a {@link DeviceAddress} that identifies
 * the target device by host, port, and unit ID. Discovery results are
 * cached per device address to avoid repeated model chain scans.</p>
 *
 * <p><b>SunSpec Protocol References:</b></p>
 * <ul>
 *   <li>Fronius Gen24 Register Maps: {@code refdoc/gen24-modbus-api-external-docs/}</li>
 *   <li>Float Models: Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx</li>
 *   <li>Int+SF Models: Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_ROW.xlsx</li>
 *   <li>Storage Float: Gen24_Primo_Symo_Inverter_Register_Map_Float_storage_ROW.xlsx</li>
 *   <li>Storage Int+SF: Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_storage_ROW.xlsx</li>
 * </ul>
 */
@ApplicationScoped
public class SunSpecService {

  private static final Logger LOG = Logger.getLogger(SunSpecService.class);

  @Inject
  ModbusTcpService modbusTcpService;

  @ConfigProperty(name = "frodo.modbus.write-enabled", defaultValue = "false")
  boolean writeEnabled;

  @ConfigProperty(name = "frodo.sunspec.model-format", defaultValue = "INT_SF")
  SunSpecModelFormat modelFormat;

  /** Cached discovery results keyed by device address string (host:port/unitId). */
  private final Map<String, SunSpecDiscoveryResult> discoveryCache = new ConcurrentHashMap<>();

  /**
   * Discovers the SunSpec model chain on a device.
   *
   * <p>Reads the "SunS" signature at the default base address (40000),
   * then walks the model chain by reading model ID and length until
   * an end block (0xFFFF) is encountered.</p>
   *
   * @param address target device address (host, port, unitId)
   * @return the discovery result
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public SunSpecDiscoveryResult discover(DeviceAddress address) throws IOException, TimeoutException {
    // Try default base address first
    SunSpecDiscoveryResult result = null;
    try {
      result = discoverAtAddress(address, SunSpecConstants.DEFAULT_BASE_ADDRESS);
    } catch (Exception e) {
      LOG.debugf("SunSpec signature not found at default address %d, trying alternates",
        SunSpecConstants.DEFAULT_BASE_ADDRESS);

      // Try alternate addresses
      for (int addr : SunSpecConstants.ALTERNATE_BASE_ADDRESSES) {
        try {
          result = discoverAtAddress(address, addr);
          break;
        } catch (Exception ignored) {
          // Try next address
        }
      }
    }

    if (result == null) {
      throw new IllegalStateException("SunSpec device not found at any known base address");
    }

    String cacheKey = address.toString();
    discoveryCache.put(cacheKey, result);
    LOG.infof("Discovered %d SunSpec models on %s at base address %d",
      result.modelCount(), address, result.baseAddress());
    for (SunSpecModelBlock model : result.models()) {
      LOG.infof("  Model %d (%s) at address %d, length %d",
        model.modelId(), SunSpecConstants.modelName(model.modelId()),
        model.address(), model.length());
    }

    return result;
  }

  /**
   * Gets the cached discovery result for a device, or performs discovery if not cached.
   *
   * @param address target device address (host, port, unitId)
   * @return the discovery result
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public SunSpecDiscoveryResult getOrDiscover(DeviceAddress address) throws IOException, TimeoutException {
    String cacheKey = address.toString();
    SunSpecDiscoveryResult cached = discoveryCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    return discover(address);
  }

  /**
   * Reads a specific SunSpec model from a device.
   *
   * <p>Performs discovery if needed, finds the model block, reads the
   * registers, and decodes them using the model definition.</p>
   *
   * @param address target device address (host, port, unitId)
   * @param modelId SunSpec model ID to read
   * @return the decoded model data
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public SunSpecModelData readModel(DeviceAddress address, int modelId) throws IOException, TimeoutException {
    SunSpecDiscoveryResult discovery = getOrDiscover(address);
    Optional<SunSpecModelBlock> block = discovery.findModel(modelId);
    if (block.isEmpty()) {
      throw new IllegalArgumentException(
        String.format("Model %d (%s) not found on %s",
          modelId, SunSpecConstants.modelName(modelId), address));
    }
    return readModelBlock(address, block.get());
  }

  /**
   * Reads the Common model (1) to get device identification via SunSpec.
   *
   * @param address target device address (host, port, unitId)
   * @return common model data (Mn, Md, SN, Vr, etc.)
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public SunSpecModelData readCommonModel(DeviceAddress address) throws IOException, TimeoutException {
    return readModel(address, SunSpecConstants.MODEL_COMMON);
  }

  /**
   * Reads the inverter model (111/113 or 101/103) for real-time data.
   *
   * <p>Uses the configured {@code frodo.sunspec.model-format} to prefer
   * the matching inverter model variant. Falls back to any inverter
   * model if the preferred format is not present in the model chain.</p>
   *
   * @param address target device address (host, port, unitId)
   * @return inverter model data (current, voltage, power, etc.)
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public SunSpecModelData readInverterModel(DeviceAddress address) throws IOException, TimeoutException {
    SunSpecDiscoveryResult discovery = getOrDiscover(address);
    Optional<SunSpecModelBlock> block = discovery.findInverterModel(modelFormat);
    if (block.isEmpty()) {
      throw new IllegalArgumentException("No inverter model found on " + address);
    }
    LOG.debugf("Using inverter model %d (%s) for %s (configured format: %s)",
      block.get().modelId(), SunSpecConstants.modelName(block.get().modelId()),
      address, modelFormat);
    return readModelBlock(address, block.get());
  }

  /**
   * Reads all discovered models from a device.
   *
   * @param address target device address (host, port, unitId)
   * @return list of all decoded model data
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public List<SunSpecModelData> readAllModels(DeviceAddress address) throws IOException, TimeoutException {
    SunSpecDiscoveryResult discovery = getOrDiscover(address);
    List<SunSpecModelData> results = new ArrayList<>();

    for (SunSpecModelBlock block : discovery.models()) {
      if (SunSpecModelRegistry.isKnown(block.modelId())) {
        results.add(readModelBlock(address, block));
      } else {
        LOG.debugf("Skipping unknown model %d at address %d", block.modelId(), block.address());
      }
    }

    return List.copyOf(results);
  }

  /**
   * Writes a register value to a device. Only works if write operations are enabled.
   *
   * @param address target device address (host, port, unitId)
   * @param regAddr Modbus holding register address
   * @param value   value to write
   * @throws IllegalStateException if write operations are disabled
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public void writeSingleRegister(DeviceAddress address, int regAddr, int value)
    throws IOException, TimeoutException {

    modbusTcpService.writeSingleRegister(address, regAddr, value);
  }

  /**
   * Writes multiple register values to a device. Only works if write operations are enabled.
   *
   * @param address   target device address (host, port, unitId)
   * @param startAddr starting register address
   * @param values    values to write
   * @throws IllegalStateException if write operations are disabled
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public void writeMultipleRegisters(DeviceAddress address, int startAddr, int[] values)
    throws IOException, TimeoutException {

    modbusTcpService.writeMultipleRegisters(address, startAddr, values);
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
   * Returns the configured SunSpec model format preference.
   *
   * @return the model format (INT_SF or FLOAT)
   */
  public SunSpecModelFormat getModelFormat() {
    return modelFormat;
  }

  /**
   * Invalidates the cached discovery result for a device address.
   *
   * @param address target device address
   */
  public void invalidateDiscovery(DeviceAddress address) {
    String cacheKey = address.toString();
    discoveryCache.remove(cacheKey);
    LOG.debugf("Invalidated SunSpec discovery cache for %s", address);
  }

  /**
   * Clears all cached discovery results.
   */
  public void clearDiscoveryCache() {
    discoveryCache.clear();
    LOG.info("Cleared SunSpec discovery cache");
  }

  /**
   * Returns the number of cached discovery results.
   *
   * @return cache size
   */
  public int getDiscoveryCacheSize() {
    return discoveryCache.size();
  }

  /**
   * Returns the set of cache keys that have cached discovery results.
   *
   * @return unmodifiable set of cached device address keys (host:port/unitId)
   */
  public Set<String> getCachedDeviceKeys() {
    return Set.copyOf(discoveryCache.keySet());
  }

  /**
   * Returns the cached discovery result for a specific device, without triggering discovery.
   *
   * @param address target device address
   * @return Optional containing the cached result, or empty if not cached
   */
  public Optional<SunSpecDiscoveryResult> getCachedDiscovery(DeviceAddress address) {
    return Optional.ofNullable(discoveryCache.get(address.toString()));
  }

  /**
   * Returns the cached discovery result for a specific cache key, without triggering discovery.
   *
   * <p>This overload is intended for health checks and monitoring that iterate over
   * {@link #getCachedDeviceKeys()} without needing to reconstruct a {@link DeviceAddress}.</p>
   *
   * @param cacheKey device address key (host:port/unitId) as returned by {@link #getCachedDeviceKeys()}
   * @return Optional containing the cached result, or empty if not cached
   */
  public Optional<SunSpecDiscoveryResult> getCachedDiscovery(String cacheKey) {
    return Optional.ofNullable(discoveryCache.get(cacheKey));
  }

  // ---- Internal methods ----

  /**
   * Attempts discovery at a specific base address.
   */
  private SunSpecDiscoveryResult discoverAtAddress(DeviceAddress address, int baseAddress)
    throws IOException, TimeoutException {

    // Read signature registers (2 registers at base address)
    int[] signatureRegs = modbusTcpService.readHoldingRegisters(address, baseAddress, 2);
    if (signatureRegs.length < 2) {
      throw new IllegalStateException("Could not read SunSpec signature at address " + baseAddress);
    }

    long signature = ((signatureRegs[0] & 0xFFFFL) << 16) | (signatureRegs[1] & 0xFFFFL);
    if (signature != SunSpecConstants.SUNSPEC_SIGNATURE) {
      throw new IllegalStateException(
        String.format("Invalid SunSpec signature at address %d: 0x%08X (expected 0x%08X)",
          baseAddress, signature, SunSpecConstants.SUNSPEC_SIGNATURE));
    }

    LOG.debugf("Found SunSpec signature at address %d", baseAddress);

    // Walk the model chain starting after the signature
    List<SunSpecModelBlock> models = walkModelChain(address, baseAddress + 2);
    return SunSpecDiscoveryResult.of(baseAddress, models);
  }

  /**
   * Walks the SunSpec model chain by reading model ID + length headers iteratively.
   */
  private List<SunSpecModelBlock> walkModelChain(DeviceAddress address, int startAddress)
    throws IOException, TimeoutException {

    List<SunSpecModelBlock> models = new ArrayList<>();
    int currentAddress = startAddress;

    for (int depth = 0; depth < SunSpecConstants.MAX_MODEL_SCAN_DEPTH; depth++) {
      // Read 2 registers: Model ID + Model Length
      int[] headerRegs = modbusTcpService.readHoldingRegisters(address, currentAddress, 2);
      if (headerRegs.length < 2) {
        LOG.warnf("Could not read model header at address %d", currentAddress);
        break;
      }

      int modelId = headerRegs[0] & 0xFFFF;
      int length = headerRegs[1] & 0xFFFF;

      // End block marker
      if (modelId == SunSpecConstants.END_MODEL_ID) {
        LOG.debugf("Found SunSpec end block at address %d", currentAddress);
        break;
      }

      LOG.debugf("Found model %d (%s) at address %d, length %d",
        modelId, SunSpecConstants.modelName(modelId), currentAddress, length);

      models.add(new SunSpecModelBlock(modelId, currentAddress, length));

      // Move to next model: address + 2 (header) + length (data)
      currentAddress = currentAddress + 2 + length;
    }

    return models;
  }

  /**
   * Reads register data for a model block and decodes it.
   */
  private SunSpecModelData readModelBlock(DeviceAddress address, SunSpecModelBlock block)
    throws IOException, TimeoutException {

    Optional<SunSpecModelDefinition> defOpt = SunSpecModelRegistry.get(block.modelId());
    if (defOpt.isEmpty()) {
      throw new IllegalArgumentException("No definition for model " + block.modelId());
    }

    SunSpecModelDefinition definition = defOpt.get();
    int dataAddress = block.dataAddress();
    int length = block.length();

    LOG.debugf("Reading model %d data: %d registers at address %d", block.modelId(), length, dataAddress);

    int[] registers;

    // Handle large models that exceed max registers per read
    if (length <= SunSpecConstants.MAX_REGISTERS_PER_READ) {
      registers = modbusTcpService.readHoldingRegisters(address, dataAddress, length);
    } else {
      registers = readLargeModel(address, dataAddress, length);
    }

    return SunSpecModelDataDecoder.decode(definition, registers, block.address());
  }

  /**
   * Reads a large model by splitting into multiple FC 0x03 requests.
   */
  private int[] readLargeModel(DeviceAddress address, int startAddress, int totalRegisters)
    throws IOException, TimeoutException {

    int[] combined = new int[totalRegisters];
    int offset = 0;
    int regAddress = startAddress;
    int remaining = totalRegisters;

    while (remaining > 0) {
      int chunkSize = Math.min(remaining, SunSpecConstants.MAX_REGISTERS_PER_READ);
      int[] chunk = modbusTcpService.readHoldingRegisters(address, regAddress, chunkSize);
      System.arraycopy(chunk, 0, combined, offset, chunk.length);
      offset += chunk.length;
      regAddress += chunkSize;
      remaining -= chunkSize;
    }

    return combined;
  }
}
