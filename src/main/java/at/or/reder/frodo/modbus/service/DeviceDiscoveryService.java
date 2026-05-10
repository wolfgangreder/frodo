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

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.ModbusTcpService;
import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.DeviceType;
import at.or.reder.frodo.modbus.model.ReadDeviceIdCode;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import at.or.reder.frodo.health.ModbusMetrics;
import at.or.reder.frodo.solarapi.SolarApiClient;
import at.or.reder.frodo.solarapi.model.OhmpilotData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import at.or.reder.frodo.solarapi.model.SolarApiResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for discovering Modbus and Solar API devices on a Fronius gateway.
 *
 * <p>Combines two discovery strategies:</p>
 * <ol>
 *   <li><b>Modbus/SunSpec discovery</b>: Scans configurable Unit ID ranges for
 *       SunSpec-capable devices (inverters, smart meters). Falls back to
 *       FC 0x2B (Read Device Identification) for non-SunSpec devices.</li>
 *   <li><b>Solar API discovery</b>: Queries the Fronius Solar API
 *       {@code GetPowerFlowRealtimeData} endpoint to discover Ohmpilot
 *       devices by ComponentId.</li>
 * </ol>
 *
 * <p>Discovery is triggered manually via REST API. Results can optionally
 * be saved as {@link ModbusDeviceEntity} records with parent-child
 * relationships.</p>
 *
 * @see SunSpecService
 * @see SolarApiClient
 */
@ApplicationScoped
public class DeviceDiscoveryService {

  private static final Logger LOG = Logger.getLogger(DeviceDiscoveryService.class);

  @Inject
  SunSpecService sunSpecService;

  @Inject
  ModbusTcpService modbusTcpService;

  @Inject
  SolarApiClient solarApiClient;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @Inject
  ModbusMetrics modbusMetrics;

  @ConfigProperty(name = "frodo.discovery.enabled", defaultValue = "true")
  boolean discoveryEnabled;

  @ConfigProperty(name = "frodo.discovery.unit-id-ranges", defaultValue = "1,200-203")
  String unitIdRanges;

  @ConfigProperty(name = "frodo.discovery.timeout-seconds", defaultValue = "3")
  int timeoutSeconds;

  @ConfigProperty(name = "frodo.discovery.max-concurrent-scans", defaultValue = "5")
  int maxConcurrentScans;

  /**
   * Discovers all devices on a Modbus TCP gateway at the given host:port.
   *
   * <p>Combines Modbus/SunSpec scanning of configured unit ID ranges with
   * Solar API discovery (if enabled). Non-responding unit IDs are silently
   * skipped.</p>
   *
   * @param host Modbus TCP host (also used for Solar API if on same device)
   * @param port Modbus TCP port
   * @return list of discovered devices (may be empty, never null)
   */
  public List<DiscoveredDevice> discoverDevices(String host, int port) {
    if (!discoveryEnabled) {
      LOG.info("Device discovery is disabled");
      return List.of();
    }

    try {
      LOG.infof("Starting device discovery on %s:%d", host, port);
      List<DiscoveredDevice> allDevices = new ArrayList<>();

      // Phase 1: Modbus/SunSpec discovery
      List<Integer> unitIds = parseUnitIdRanges(unitIdRanges);
      LOG.infof("Scanning %d unit IDs: %s", unitIds.size(), unitIds);

      for (int unitId : unitIds) {
        try {
          Optional<DiscoveredDevice> device = scanUnitId(host, port, unitId);
          device.ifPresent(d -> {
            allDevices.add(d);
            LOG.infof("Discovered %s at %s (type: %s, source: %s)",
              d.suggestedName(), d.connectionString(), d.deviceType(), d.source());
          });
        } catch (Exception ex) {
          LOG.debugf("Unit ID %d not responding on %s:%d: %s", unitId, host, port, ex.getMessage());
        }
      }

      // Phase 2: Solar API discovery (Ohmpilots)
      List<DiscoveredDevice> solarApiDevices = discoverViaSolarApi(host);
      for (DiscoveredDevice solarDevice : solarApiDevices) {
        // Avoid duplicates if Modbus also discovered the device
        boolean isDuplicate = allDevices.stream().anyMatch(d ->
          d.deviceType() == solarDevice.deviceType()
            && d.serialNumber() != null
            && d.serialNumber().equals(solarDevice.serialNumber()));
        if (!isDuplicate) {
          allDevices.add(solarDevice);
          LOG.infof("Discovered %s via Solar API (type: %s, source: %s)",
            solarDevice.suggestedName(), solarDevice.deviceType(), solarDevice.source());
        }
      }

      LOG.infof("Discovery complete on %s:%d: found %d device(s)", host, port, allDevices.size());
      modbusMetrics.recordDiscoveryScanSuccess(allDevices.size());
      return Collections.unmodifiableList(allDevices);
    } catch (Exception ex) {
      modbusMetrics.recordDiscoveryScanFailure();
      throw ex;
    }
  }

  /**
   * Discovers devices using a specific set of unit ID ranges (overriding config).
   *
   * @param host         Modbus TCP host
   * @param port         Modbus TCP port
   * @param unitIdRanges unit ID range string (e.g. "1,200-203")
   * @return list of discovered devices
   */
  public List<DiscoveredDevice> discoverDevices(String host, int port, String unitIdRanges) {
    if (!discoveryEnabled) {
      LOG.info("Device discovery is disabled");
      return List.of();
    }

    try {
      LOG.infof("Starting device discovery on %s:%d with custom ranges: %s", host, port, unitIdRanges);
      List<DiscoveredDevice> allDevices = new ArrayList<>();

      List<Integer> unitIds = parseUnitIdRanges(unitIdRanges);
      LOG.infof("Scanning %d unit IDs: %s", unitIds.size(), unitIds);

      for (int unitId : unitIds) {
        try {
          Optional<DiscoveredDevice> device = scanUnitId(host, port, unitId);
          device.ifPresent(allDevices::add);
        } catch (Exception ex) {
          LOG.debugf("Unit ID %d not responding on %s:%d: %s", unitId, host, port, ex.getMessage());
        }
      }

      // Also try Solar API
      List<DiscoveredDevice> solarApiDevices = discoverViaSolarApi(host);
      allDevices.addAll(solarApiDevices);

      LOG.infof("Discovery complete on %s:%d: found %d device(s)", host, port, allDevices.size());
      modbusMetrics.recordDiscoveryScanSuccess(allDevices.size());
      return Collections.unmodifiableList(allDevices);
    } catch (Exception ex) {
      modbusMetrics.recordDiscoveryScanFailure();
      throw ex;
    }
  }

  /**
   * Scans a specific unit ID for a SunSpec or Modbus device.
   *
   * <p>Strategy:</p>
   * <ol>
   *   <li>Attempt SunSpec discovery (read "SunS" signature at register 40000)</li>
   *   <li>If SunSpec found: read Common model for device info, determine type from models</li>
   *   <li>If no SunSpec: attempt FC 0x2B for device identification</li>
   *   <li>Return empty if the unit ID does not respond</li>
   * </ol>
   *
   * @param host   Modbus TCP host
   * @param port   Modbus TCP port
   * @param unitId Modbus unit ID to probe
   * @return discovered device, or empty if the unit ID is not responding
   */
  public Optional<DiscoveredDevice> scanUnitId(String host, int port, int unitId) {
    DeviceAddress address = new DeviceAddress(host, port, unitId);

    // Try SunSpec discovery first
    try {
      SunSpecDiscoveryResult discovery = sunSpecService.discover(address);
      return Optional.of(buildFromSunSpec(host, port, unitId, address, discovery));
    } catch (Exception sunspecEx) {
      LOG.debugf("No SunSpec signature on %s: %s", address, sunspecEx.getMessage());
    }

    // Fallback: try FC 0x2B (Read Device Identification)
    try {
      DeviceIdentification identification =
        modbusTcpService.readDeviceIdentification(address, ReadDeviceIdCode.BASIC);
      return Optional.of(buildFromDeviceId(host, port, unitId, identification));
    } catch (Exception fc2bEx) {
      LOG.debugf("FC 0x2B failed on %s: %s", address, fc2bEx.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Determines the device type from a SunSpec discovery result.
   *
   * <p>Logic:</p>
   * <ul>
   *   <li>Inverter models (101-103, 111-113) → {@link DeviceType#INVERTER}</li>
   *   <li>Storage model (124) → {@link DeviceType#STORAGE}</li>
   *   <li>Meter models (201-204, 211-214) → {@link DeviceType#SMART_METER}</li>
   *   <li>MPPT model (160) without inverter → still {@link DeviceType#INVERTER}</li>
   *   <li>Otherwise → {@link DeviceType#UNKNOWN}</li>
   * </ul>
   *
   * @param discovery SunSpec discovery result
   * @return detected device type
   */
  public DeviceType determineDeviceType(SunSpecDiscoveryResult discovery) {
    // Check for inverter models
    if (discovery.hasAnyModel(
      SunSpecConstants.MODEL_INVERTER_SINGLE_PHASE,
      SunSpecConstants.MODEL_INVERTER_SPLIT_PHASE,
      SunSpecConstants.MODEL_INVERTER_THREE_PHASE,
      SunSpecConstants.MODEL_INVERTER_SINGLE_PHASE_FLOAT,
      SunSpecConstants.MODEL_INVERTER_SPLIT_PHASE_FLOAT,
      SunSpecConstants.MODEL_INVERTER_THREE_PHASE_FLOAT)) {
      return DeviceType.INVERTER;
    }

    // Check for meter models
    if (discovery.hasAnyModel(
      SunSpecConstants.MODEL_METER_SINGLE_PHASE,
      SunSpecConstants.MODEL_METER_SPLIT_PHASE,
      SunSpecConstants.MODEL_METER_THREE_PHASE_WYE,
      SunSpecConstants.MODEL_METER_THREE_PHASE_DELTA,
      SunSpecConstants.MODEL_METER_SINGLE_PHASE_FLOAT,
      SunSpecConstants.MODEL_METER_SPLIT_PHASE_FLOAT,
      SunSpecConstants.MODEL_METER_THREE_PHASE_WYE_FLOAT,
      SunSpecConstants.MODEL_METER_THREE_PHASE_DELTA_FLOAT)) {
      return DeviceType.SMART_METER;
    }

    // Check for storage model
    if (discovery.hasModel(SunSpecConstants.MODEL_STORAGE)) {
      return DeviceType.STORAGE;
    }

    // MPPT typically indicates an inverter
    if (discovery.hasModel(SunSpecConstants.MODEL_MPPT)) {
      return DeviceType.INVERTER;
    }

    return DeviceType.UNKNOWN;
  }

  /**
   * Determines the device type from FC 0x2B device identification strings.
   *
   * <p>Checks manufacturer and model strings for known device keywords.</p>
   *
   * @param identification device identification from FC 0x2B
   * @return detected device type
   */
  public DeviceType determineDeviceType(DeviceIdentification identification) {
    String vendor = identification.vendorName() != null
      ? identification.vendorName().toLowerCase() : "";
    String product = identification.productCode() != null
      ? identification.productCode().toLowerCase() : "";
    String productName = identification.productName() != null
      ? identification.productName().toLowerCase() : "";

    boolean isFronius = vendor.contains("fronius");

    if (isFronius) {
      if (product.contains("ohmpilot") || productName.contains("ohmpilot")
        || product.contains("smartload") || productName.contains("smartload")) {
        return DeviceType.OHMPILOT;
      }
      if (product.contains("meter") || productName.contains("meter")) {
        return DeviceType.SMART_METER;
      }
      if (product.contains("gen24") || product.contains("primo") || product.contains("symo")
        || productName.contains("inverter")) {
        return DeviceType.INVERTER;
      }
    }

    return DeviceType.UNKNOWN;
  }

  /**
   * Saves discovered devices to the database, setting parent-child relationships.
   *
   * <p>For each discovered device:</p>
   * <ul>
   *   <li>If it already exists (same host+port+unitId): updates deviceType if null</li>
   *   <li>If new: creates entity with {@code autoDiscovered=true} and parent reference</li>
   * </ul>
   *
   * @param parentDeviceId ID of the parent device (the gateway/inverter), or null
   * @param discovered     list of discovered devices to save
   * @return list of saved/updated device entities
   */
  @Transactional
  public List<ModbusDeviceEntity> saveDiscoveredDevices(Long parentDeviceId,
                                                        List<DiscoveredDevice> discovered) {
    ModbusDeviceEntity parentDevice = null;
    if (parentDeviceId != null) {
      parentDevice = deviceRepository.findById(parentDeviceId);
      if (parentDevice == null) {
        throw new IllegalArgumentException("Parent device not found: " + parentDeviceId);
      }
    }

    List<ModbusDeviceEntity> savedDevices = new ArrayList<>();

    for (DiscoveredDevice device : discovered) {
      if (!device.hasUnitId()) {
        LOG.debugf("Skipping device without unit ID: %s (source: %s)",
          device.suggestedName(), device.source());
        continue;
      }

      Optional<ModbusDeviceEntity> existing =
        deviceRepository.findByConnection(device.host(), device.port(), device.unitId());

      if (existing.isPresent()) {
        ModbusDeviceEntity entity = existing.get();
        // Update device type if not set
        if (entity.deviceType == null && device.deviceType() != DeviceType.UNKNOWN) {
          entity.deviceType = device.deviceType();
          deviceRepository.save(entity);
          LOG.infof("Updated device type for %s to %s", entity.getConnectionString(), device.deviceType());
        }
        savedDevices.add(entity);
      } else {
        ModbusDeviceEntity entity = new ModbusDeviceEntity();
        entity.name = device.suggestedName();
        entity.host = device.host();
        entity.port = device.port();
        entity.unitId = device.unitId();
        entity.enabled = true;
        entity.deviceType = device.deviceType();
        entity.autoDiscovered = true;
        entity.parentDevice = parentDevice;
        if (device.manufacturer() != null && device.model() != null) {
          entity.description = String.format("Auto-discovered %s %s (S/N: %s)",
            device.manufacturer(), device.model(),
            device.serialNumber() != null ? device.serialNumber() : "unknown");
        }
        deviceRepository.save(entity);
        LOG.infof("Created device: %s (type: %s, autoDiscovered: true)",
          entity.getConnectionString(), entity.deviceType);
        savedDevices.add(entity);
      }
    }

    return savedDevices;
  }

  /**
   * Parses a unit ID range string into a list of individual unit IDs.
   *
   * <p>Supports comma-separated values and dash-separated ranges.</p>
   * <p>Examples:</p>
   * <ul>
   *   <li>"1" → [1]</li>
   *   <li>"1,200-203" → [1, 200, 201, 202, 203]</li>
   *   <li>"1-3,10,200-202" → [1, 2, 3, 10, 200, 201, 202]</li>
   * </ul>
   *
   * @param ranges unit ID range string
   * @return sorted list of unique unit IDs
   * @throws IllegalArgumentException if the input is invalid
   */
  public List<Integer> parseUnitIdRanges(String ranges) {
    if (ranges == null || ranges.isBlank()) {
      return List.of();
    }

    List<Integer> unitIds = new ArrayList<>();
    String[] parts = ranges.split(",");

    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      if (trimmed.contains("-")) {
        String[] range = trimmed.split("-", 2);
        int start = parseUnitId(range[0].trim());
        int end = parseUnitId(range[1].trim());
        if (start > end) {
          throw new IllegalArgumentException(
            String.format("Invalid range: %d-%d (start must be <= end)", start, end));
        }
        for (int i = start; i <= end; i++) {
          if (!unitIds.contains(i)) {
            unitIds.add(i);
          }
        }
      } else {
        int id = parseUnitId(trimmed);
        if (!unitIds.contains(id)) {
          unitIds.add(id);
        }
      }
    }

    Collections.sort(unitIds);
    return Collections.unmodifiableList(unitIds);
  }

  /**
   * Checks whether discovery is enabled.
   *
   * @return true if device discovery is enabled
   */
  public boolean isEnabled() {
    return discoveryEnabled;
  }

  /**
   * Returns the configured unit ID ranges string.
   *
   * @return configured ranges (e.g. "1,200-203")
   */
  public String getConfiguredRanges() {
    return unitIdRanges;
  }

  // ---- Internal methods ----

  /**
   * Builds a DiscoveredDevice from SunSpec discovery and Common model data.
   */
  private DiscoveredDevice buildFromSunSpec(String host, int port, int unitId,
                                            DeviceAddress address,
                                            SunSpecDiscoveryResult discovery) {
    DeviceType deviceType = determineDeviceType(discovery);
    List<Integer> modelIds = discovery.modelIds();

    // Try to read Common model for identification info
    String manufacturer = null;
    String model = null;
    String serialNumber = null;
    String version = null;

    try {
      SunSpecModelData common = sunSpecService.readCommonModel(address);
      manufacturer = common.getString("Mn");
      model = common.getString("Md");
      serialNumber = common.getString("SN");
      version = common.getString("Vr");
    } catch (Exception ex) {
      LOG.debugf("Could not read SunSpec Common model on %s: %s", address, ex.getMessage());
    }

    return new DiscoveredDevice(
      host, port, unitId, deviceType,
      manufacturer, model, serialNumber, version,
      modelIds, DiscoveredDevice.SOURCE_SUNSPEC
    );
  }

  /**
   * Builds a DiscoveredDevice from FC 0x2B device identification.
   */
  private DiscoveredDevice buildFromDeviceId(String host, int port, int unitId,
                                             DeviceIdentification identification) {
    DeviceType deviceType = determineDeviceType(identification);

    return new DiscoveredDevice(
      host, port, unitId, deviceType,
      identification.vendorName(),
      identification.productCode(),
      null, // serial number not available from FC 0x2B
      identification.majorMinorRevision(),
      List.of(), // no SunSpec models
      DiscoveredDevice.SOURCE_MODBUS_FC2B
    );
  }

  /**
   * Discovers Ohmpilot devices via the Fronius Solar API.
   */
  private List<DiscoveredDevice> discoverViaSolarApi(String host) {
    if (!solarApiClient.isEnabled()) {
      LOG.debugf("Solar API disabled, skipping Ohmpilot discovery");
      return List.of();
    }

    try {
      SolarApiResponse<PowerFlowRealtimeData> response =
        solarApiClient.getPowerFlowRealtimeData().await().indefinitely();

      if (response == null || !response.isSuccess() || response.getData() == null) {
        LOG.debugf("Solar API returned no data or error");
        return List.of();
      }

      PowerFlowRealtimeData data = response.getData();
      if (!data.hasOhmpilots()) {
        LOG.debugf("No Ohmpilots found in Solar API response");
        return List.of();
      }

      List<DiscoveredDevice> devices = new ArrayList<>();
      Map<String, OhmpilotData> ohmpilots = data.getSmartloads().getOhmpilots();

      for (Map.Entry<String, OhmpilotData> entry : ohmpilots.entrySet()) {
        String componentId = entry.getKey();
        OhmpilotData ohmpilot = entry.getValue();

        DiscoveredDevice device = new DiscoveredDevice(
          host,
          80, // Solar API port
          -1, // Unit ID unknown for Solar API devices
          DeviceType.OHMPILOT,
          "Fronius",
          "Ohmpilot",
          componentId, // Use ComponentId as serial/identifier
          null,
          List.of(), // No SunSpec models
          DiscoveredDevice.SOURCE_SOLAR_API
        );

        devices.add(device);
        LOG.infof("Solar API: found Ohmpilot (componentId=%s, state=%s, temp=%.1f°C, power=%.1fW)",
          componentId,
          ohmpilot.getState() != null ? ohmpilot.getState() : "unknown",
          ohmpilot.getTemperatureCelsius() != null ? ohmpilot.getTemperatureCelsius() : 0.0,
          ohmpilot.getPowerWatts() != null ? ohmpilot.getPowerWatts() : 0.0);
      }

      return devices;

    } catch (Exception ex) {
      LOG.warnf("Solar API discovery failed: %s", ex.getMessage());
      return List.of();
    }
  }

  /**
   * Parses and validates a single unit ID value.
   */
  private int parseUnitId(String value) {
    try {
      int id = Integer.parseInt(value);
      if (id < 1 || id > 247) {
        throw new IllegalArgumentException(
          String.format("Unit ID %d out of range (valid: 1-247)", id));
      }
      return id;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid unit ID: " + value);
    }
  }
}
