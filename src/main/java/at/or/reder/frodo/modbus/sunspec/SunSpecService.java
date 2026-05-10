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
   * Cached WMax (nameplate rated power in Watts) keyed by device address string.
   *
   * <p>WRtg from Model 120 (Nameplate) rarely changes; caching avoids a Modbus
   * round-trip on every scheduler tick. The cache is cleared together with the
   * discovery cache (e.g. on {@link #clearDiscoveryCache()}).</p>
   */
  private final Map<String, Double> wMaxCache = new ConcurrentHashMap<>();

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
   * Reads the meter model (201-204 or 211-214) for real-time data.
   *
   * <p>Uses the configured {@code frodo.sunspec.model-format} to prefer
   * the matching meter model variant. Falls back to any meter model if the
   * preferred format is not present in the model chain.</p>
   *
   * <p>Meter models:</p>
   * <ul>
   *   <li>201/211 - Single Phase (A-N or A-B)</li>
   *   <li>202/212 - Split Single Phase (A-B-N)</li>
   *   <li>203/213 - Three Phase WYE (A-B-C-N)</li>
   *   <li>204/214 - Three Phase Delta (A-B-C)</li>
   * </ul>
   *
   * @param address target device address (host, port, unitId)
   * @return meter model data (current, voltage, power, energy, etc.)
   * @throws IOException      if communication fails
   * @throws TimeoutException if the request times out
   */
  public SunSpecModelData readMeterModel(DeviceAddress address) throws IOException, TimeoutException {
    SunSpecDiscoveryResult discovery = getOrDiscover(address);
    Optional<SunSpecModelBlock> block = discovery.findMeterModel(modelFormat);
    if (block.isEmpty()) {
      throw new IllegalArgumentException("No meter model found on " + address);
    }
    LOG.debugf("Using meter model %d (%s) for %s (configured format: %s)",
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
   * Sets the inverter power output limit using SunSpec Model 123 (Immediate Controls).
   *
   * <p>When {@code enable} is {@code true}, reads the device's {@code WMaxLimPct_SF}
   * scale factor, converts the percent value to the raw register value, writes
   * {@code WMaxLimPct} (and optionally ramp/revert times), then sets
   * {@code WMaxLim_Ena = 1} to activate the limit.</p>
   *
   * <p>When {@code enable} is {@code false}, writes {@code WMaxLim_Ena = 0} to
   * deactivate the limit and restore normal inverter operation.</p>
   *
   * <p>Register offsets in Model 123 data block:</p>
   * <ul>
   *   <li>Offset 3: {@code WMaxLimPct} – power limit percentage (scaled)</li>
   *   <li>Offset 5: {@code WMaxLimPct_RvrtTms} – auto-revert timeout</li>
   *   <li>Offset 6: {@code WMaxLimPct_RmpTms} – ramp time</li>
   *   <li>Offset 7: {@code WMaxLim_Ena} – throttle enable/disable</li>
   *   <li>Offset 21: {@code WMaxLimPct_SF} – read-only scale factor</li>
   * </ul>
   *
   * <p>Scale factor formula: {@code rawValue = round(limitPercent × 10^(−SF))}</p>
   * <p>Example: SF = −2, limit = 50 % → rawValue = 50 × 100 = 5000</p>
   *
   * <p>Requires {@code frodo.modbus.write-enabled=true}.</p>
   *
   * @param address       target device address
   * @param limitPercent  power output limit in % of WMax (0–100);
   *                      use 0 to block all grid export (Nulleinspeisung)
   * @param enable        {@code true} to activate the limit, {@code false} to deactivate
   * @param rampSeconds   ramp time in seconds for smooth transitions (0 = immediate)
   * @param revertSeconds auto-revert timeout in seconds (0 = no auto-revert)
   * @throws IllegalStateException    if write operations are disabled
   * @throws IllegalArgumentException if Model 123 (Immediate Controls) is not found on the device
   * @throws IOException              if communication fails
   * @throws TimeoutException         if the request times out
   */
  public void setPowerLimit(DeviceAddress address, int limitPercent, boolean enable,
    int rampSeconds, int revertSeconds) throws IOException, TimeoutException {

    if (!writeEnabled) {
      throw new IllegalStateException(
        "Write operations are disabled. Set frodo.modbus.write-enabled=true to allow writes.");
    }

    SunSpecDiscoveryResult discovery = getOrDiscover(address);
    SunSpecModelBlock block = discovery.findModel(SunSpecConstants.MODEL_CONTROLS)
      .orElseThrow(() -> new IllegalArgumentException(
        "Model 123 (Immediate Controls) not found on " + address));

    int dataStart = block.dataAddress(); // block.address() + 2

    // Register addresses derived from Model 123 field offsets (SunSpecModelRegistry)
    int wMaxLimPctAddr    = dataStart + 3;
    int wMaxLimRvrtAddr   = dataStart + 5;
    int wMaxLimRmpAddr    = dataStart + 6;
    int wMaxLimEnaAddr    = dataStart + 7;
    int wMaxLimPctSfAddr  = dataStart + 21;

    LOG.infof(
      "Model 123 register map: device=%s, block.address=%d, dataStart=%d,"
        + " WMaxLimPct=%d, WMaxLimRvrt=%d, WMaxLimRmp=%d, WMaxLim_Ena=%d, WMaxLimPct_SF=%d",
      address,
      Integer.valueOf(block.address()),
      Integer.valueOf(dataStart),
      Integer.valueOf(wMaxLimPctAddr),
      Integer.valueOf(wMaxLimRvrtAddr),
      Integer.valueOf(wMaxLimRmpAddr),
      Integer.valueOf(wMaxLimEnaAddr),
      Integer.valueOf(wMaxLimPctSfAddr));

    if (enable) {
      // Read the device's scale factor for WMaxLimPct (signed int16 / sunssf type)
      int[] sfReg = modbusTcpService.readHoldingRegisters(address, wMaxLimPctSfAddr, 1);
      int sfRaw = sfReg[0] & 0xFFFF;

      // SunSpec NOT_IMPLEMENTED sentinel for sunssf type is 0x8000.
      // If the device signals the field is not implemented, fall back to SF=-2
      // (the most common Fronius Gen24 scale factor for WMaxLimPct).
      final int sf;
      if (sfRaw == 0x8000) {
        LOG.warnf("WMaxLimPct_SF returned NOT_IMPLEMENTED (0x8000) for %s; defaulting to SF=-2", address);
        sf = -2;
      } else {
        sf = (short) sfRaw; // sign-extend to get negative exponents (e.g. -2)
      }

      // rawValue = limitPercent × 10^(−SF)
      // e.g. SF=-2, limit=50% → rawValue = 50 × 10^2 = 5000
      double scaled = limitPercent * Math.pow(10, -sf);
      if (!Double.isFinite(scaled) || scaled < 0 || scaled > 65535) {
        throw new IllegalArgumentException(
          String.format("Computed WMaxLimPct raw value %.0f is out of uint16 range"
            + " (limitPercent=%d, SF=%d). Check device scale factor.", scaled, limitPercent, sf));
      }
      int rawValue = (int) Math.round(scaled);

      LOG.infof("Setting power limit: device=%s, limitPct=%d%%, SF=%d, rawValue=%d, ramp=%ds, revert=%ds",
        address, Integer.valueOf(limitPercent), Integer.valueOf(sf), Integer.valueOf(rawValue),
        Integer.valueOf(rampSeconds), Integer.valueOf(revertSeconds));

      if (rampSeconds > 0) {
        modbusTcpService.writeSingleRegister(address, wMaxLimRmpAddr, rampSeconds);
      }
      if (revertSeconds > 0) {
        modbusTcpService.writeSingleRegister(address, wMaxLimRvrtAddr, revertSeconds);
      }
      modbusTcpService.writeSingleRegister(address, wMaxLimPctAddr, rawValue);
      modbusTcpService.writeSingleRegister(address, wMaxLimEnaAddr, 1);

    } else {
      LOG.infof("Disabling power limit: device=%s", address);
      modbusTcpService.writeSingleRegister(address, wMaxLimEnaAddr, 0);
    }
  }

  /**
   * Convenience overload with no ramp or revert time (immediate change, no auto-revert).
   *
   * @param address      target device address
   * @param limitPercent power output limit in % of WMax (0–100)
   * @param enable       {@code true} to activate the limit, {@code false} to deactivate
   * @throws IllegalStateException    if write operations are disabled
   * @throws IllegalArgumentException if Model 123 is not found on the device
   * @throws IOException              if communication fails
   * @throws TimeoutException         if the request times out
   */
  public void setPowerLimit(DeviceAddress address, int limitPercent, boolean enable)
    throws IOException, TimeoutException {
    setPowerLimit(address, limitPercent, enable, 0, 0);
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
   * Invalidates the cached discovery result and WMax for a device address.
   *
   * @param address target device address
   */
  public void invalidateDiscovery(DeviceAddress address) {
    String cacheKey = address.toString();
    discoveryCache.remove(cacheKey);
    wMaxCache.remove(cacheKey);
    LOG.debugf("Invalidated SunSpec discovery and WMax caches for %s", address);
  }

  /**
   * Clears all cached discovery results and WMax values.
   */
  public void clearDiscoveryCache() {
    discoveryCache.clear();
    wMaxCache.clear();
    LOG.info("Cleared SunSpec discovery and WMax caches");
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

  // ---- Zero-export (Nulleinspeisung) closed-loop helpers ----

  /**
   * Returns the nameplate-rated peak power (WRtg) in Watts from Model 120.
   *
   * <p>The value is read once and cached indefinitely per device address. The cache
   * is cleared when {@link #invalidateDiscovery} or {@link #clearDiscoveryCache}
   * is called.</p>
   *
   * @param address target device address (inverter with Model 120)
   * @return rated power in Watts (always &gt; 0)
   * @throws IllegalStateException if WRtg is not available or ≤ 0
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public double readWMax(DeviceAddress address) throws IOException, TimeoutException {
    String cacheKey = address.toString();
    Double cached = wMaxCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    SunSpecModelData nameplate = readModel(address, SunSpecConstants.MODEL_NAMEPLATE);
    Double wRtg = nameplate.getDouble("WRtg");
    if (wRtg == null || wRtg <= 0) {
      throw new IllegalStateException(
        "WRtg not available or invalid on device " + address + " (got: " + wRtg + ")");
    }
    wMaxCache.put(cacheKey, wRtg);
    LOG.infof("Cached WMax=%.1f W for device %s", wRtg, address);
    return wRtg;
  }

  /**
   * Reads the current AC power output (W field) from the inverter model.
   *
   * @param address target inverter device address
   * @return AC power in Watts (positive = producing)
   * @throws IllegalStateException if the W field is absent in the inverter model
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public double readInverterWatts(DeviceAddress address) throws IOException, TimeoutException {
    SunSpecModelData inverter = readInverterModel(address);
    Double w = inverter.getDouble("W");
    if (w == null) {
      throw new IllegalStateException("Inverter W field not available on device " + address);
    }
    return w;
  }

  /**
   * Reads the current grid power flow (W field) from the meter model.
   *
   * <p>Per the SunSpec spec: positive values mean power is absorbed <em>from</em>
   * the grid (importing); negative values mean power is delivered <em>to</em>
   * the grid (exporting).</p>
   *
   * @param address target meter device address
   * @return grid power in Watts (positive = importing, negative = exporting)
   * @throws IllegalStateException if the W field is absent in the meter model
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public double readMeterWatts(DeviceAddress address) throws IOException, TimeoutException {
    SunSpecModelData meter = readMeterModel(address);
    Double w = meter.getDouble("W");
    if (w == null) {
      throw new IllegalStateException("Meter W field not available on device " + address);
    }
    return w;
  }

  /**
   * Computes the fixed power-cap percentage for the {@code FIXED_LIMIT} strategy.
   *
   * <p>Converts an absolute watt cap to a percentage of the inverter's rated power
   * ({@code WRtg} from Model 120), clamped to [0, 100]:</p>
   *
   * <pre>
   *   limitPct = clamp(round(limitWatts / WMax × 100), 0, 100)
   * </pre>
   *
   * @param address    target inverter device address (must have Model 120)
   * @param limitWatts maximum allowed output in Watts (&gt; 0)
   * @return power limit in % of WMax, clamped to [0, 100]
   * @throws IllegalArgumentException if {@code limitWatts} is not positive
   * @throws IllegalStateException    if WRtg is not available on the device
   * @throws IOException              if communication fails
   * @throws TimeoutException         if the request times out
   */
  public int computeFixedLimitPct(DeviceAddress address, int limitWatts)
    throws IOException, TimeoutException {
    if (limitWatts <= 0) {
      throw new IllegalArgumentException("limitWatts must be > 0, got: " + limitWatts);
    }
    double wMax    = readWMax(address);
    double limitPct = (limitWatts * 100.0) / wMax;
    limitPct = Math.max(0.0, Math.min(100.0, limitPct));
    int result = (int) Math.round(limitPct);

    LOG.infof(
      "Fixed-limit pct: limitWatts=%d W, WMax=%.1f W → limitPct=%d%%",
      Integer.valueOf(limitWatts), wMax, Integer.valueOf(result));

    return result;
  }

  /**
   * Computes the dynamic zero-export power limit percentage from externally
   * supplied power readings.
   *
   * <p>Accepts grid power and inverter/PV output measured by any data source
   * (Fronius Solar API site data, Smart Meter Modbus readings, etc.) and
   * converts them to a percentage of the inverter's rated power ({@code WRtg}
   * from Model 120):</p>
   *
   * <pre>
   *   houseLoad = pvWatts + gridWatts     (gridWatts positive = import, negative = export)
   *   limitPct  = clamp(houseLoad / WMax × 100, 0, 100)
   * </pre>
   *
   * <p>This value should be written to {@code WMaxLimPct} every control cycle
   * (typically every minute), because house load changes continuously.</p>
   *
   * @param inverterAddr address of the inverter device (must have Model 120 for WMax)
   * @param gridWatts    current grid power in Watts (positive = importing, negative = exporting)
   * @param pvWatts      current inverter/PV AC output in Watts
   * @return power limit in % of WMax, clamped to [0, 100]
   * @throws IllegalStateException if WRtg is not available on the device
   * @throws IOException           if Modbus communication fails reading WMax
   * @throws TimeoutException      if the Modbus request times out
   */
  public int computeZeroExportLimitPct(DeviceAddress inverterAddr, double gridWatts, double pvWatts)
    throws IOException, TimeoutException {

    double wMax     = readWMax(inverterAddr);
    double houseLoad = pvWatts + gridWatts;
    double limitPct  = Math.max(0.0, Math.min(100.0, (houseLoad / wMax) * 100.0));
    int result = (int) Math.round(limitPct);

    LOG.infof(
      "Zero-export limit: pvW=%.1f W, gridW=%.1f W, houseLoad=%.1f W, WMax=%.1f W → %d%%",
      pvWatts, gridWatts, houseLoad, wMax, Integer.valueOf(result));

    return result;
  }

  /**
   * Converts an absolute target inverter output in Watts to a percentage of the
   * device's nameplate rated power ({@code WRtg} from Model 120), clamped to
   * [0, 100].
   *
   * <pre>
   *   limitPct = clamp(round(targetWatts / WMax × 100), 0, 100)
   * </pre>
   *
   * <p>Negative {@code targetWatts} values are clamped to 0 (inverter produces
   * nothing beyond its own losses).</p>
   *
   * <p>Typical use: caller computes {@code targetWatts} from Solar API fields
   * ({@code -P_Load + P_Battery}) and passes it here to get the corresponding
   * {@code WMaxLimPct} value to write to Model 123.</p>
   *
   * @param address     target inverter device address (must have Model 120)
   * @param targetWatts desired inverter AC output in Watts
   * @return power limit in % of WMax, clamped to [0, 100]
   * @throws IllegalStateException if WRtg is not available on the device
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public int computeLimitPctFromWatts(DeviceAddress address, double targetWatts)
    throws IOException, TimeoutException {

    double wMax     = readWMax(address);
    double limitPct = Math.max(0.0, Math.min(100.0, (targetWatts / wMax) * 100.0));
    int result = (int) Math.round(limitPct);

    LOG.infof(
      "Limit from watts: targetWatts=%.1f W, WMax=%.1f W → %d%%",
      targetWatts, wMax, Integer.valueOf(result));

    return result;
  }

  /**
   * Computes the dynamic zero-export power limit percentage using Smart Meter
   * Modbus readings.
   *
   * <p>Reads both inverter and meter watts via Modbus, then delegates to
   * {@link #computeZeroExportLimitPct(DeviceAddress, double, double)}.</p>
   *
   * @param inverterAddr address of the inverter device (has Model 120 and inverter model)
   * @param meterAddr    address of the Smart Meter device (has meter model)
   * @return power limit in % of WMax, clamped to [0, 100]
   * @throws IllegalStateException if WRtg, inverter W, or meter W are unavailable
   * @throws IOException           if communication fails
   * @throws TimeoutException      if the request times out
   */
  public int computeZeroExportLimitPctViaMeter(DeviceAddress inverterAddr, DeviceAddress meterAddr)
    throws IOException, TimeoutException {

    double inverterW = readInverterWatts(inverterAddr);
    double meterW    = readMeterWatts(meterAddr);
    return computeZeroExportLimitPct(inverterAddr, meterW, inverterW);
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
