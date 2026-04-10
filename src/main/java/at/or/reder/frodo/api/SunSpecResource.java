package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.ErrorResponse;
import at.or.reder.frodo.api.dto.SunSpecDiscoveryResponse;
import at.or.reder.frodo.api.dto.SunSpecModelResponse;
import at.or.reder.frodo.api.exception.DeviceConnectionException;
import at.or.reder.frodo.api.exception.DeviceNotFoundException;
import at.or.reder.frodo.modbus.ModbusException;
import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * REST API for SunSpec Modbus device interaction.
 *
 * <p>Provides endpoints for SunSpec model chain discovery and reading
 * individual model data from Fronius Gen24 PV inverters and Smart Meters.</p>
 *
 * <p><b>Protocol References:</b></p>
 * <ul>
 *   <li>Fronius Gen24 Register Maps: {@code refdoc/gen24-modbus-api-external-docs/}</li>
 *   <li>Float Models: Gen24_Primo_Symo_Inverter_Register_Map_Float_ROW.xlsx</li>
 *   <li>Int+SF Models: Gen24_Primo_Symo_Inverter_Register_Map_Int&SF_ROW.xlsx</li>
 *   <li>Supported models: Common (1), Inverter (101-103, 111-113), Meter (201-204, 211-214),
 *       Nameplate (120), Settings (121), Status (122), Controls (123), Storage (124), MPPT (160)</li>
 * </ul>
 */
@Path("/devices/{id}/sunspec")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "SunSpec", description = "SunSpec Modbus protocol endpoints for PV device data")
public class SunSpecResource {

  private static final Logger LOG = Logger.getLogger(SunSpecResource.class);

  @Inject
  ModbusDeviceRepository deviceRepository;

  @Inject
  SunSpecService sunSpecService;

  // ========== Discovery ==========

  /**
   * Discovers SunSpec models on a device.
   *
   * <p>Scans the SunSpec model chain starting from the "SunS" signature,
   * returning all discovered model blocks with metadata.</p>
   *
   * @param id      device ID
   * @param refresh whether to force a fresh discovery (invalidates cache)
   * @return discovery result with model list
   */
  @GET
  @Path("/discovery")
  @Operation(
    summary = "Discover SunSpec models",
    description = "Scans the SunSpec model chain on the device, returning all available models. "
      + "Results are cached; use ?refresh=true to force a new scan."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Discovery result",
      content = @Content(schema = @Schema(implementation = SunSpecDiscoveryResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecDiscoveryResponse discover(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id,
    @Parameter(description = "Force fresh discovery (invalidate cache)")
    @QueryParam("refresh") @DefaultValue("false") boolean refresh
  ) {
    ModbusDeviceEntity device = requireDevice(id);
    DeviceAddress address = DeviceAddress.fromEntity(device);
    LOG.debugf("SunSpec discovery: device=%d, address=%s, refresh=%b",
      id, address, Boolean.valueOf(refresh));

    if (refresh) {
      sunSpecService.invalidateDiscovery(address);
    }

    try {
      SunSpecDiscoveryResult result = sunSpecService.getOrDiscover(address);
      return SunSpecDiscoveryResponse.fromResult(id, device.unitId, result);
    } catch (ModbusException ex) {
      throw new DeviceConnectionException("SunSpec discovery failed: " + ex.getMessage(), ex);
    } catch (IllegalStateException ex) {
      throw new DeviceConnectionException("SunSpec not supported: " + ex.getMessage(), ex);
    } catch (IOException | TimeoutException ex) {
      throw new DeviceConnectionException("SunSpec discovery failed: " + ex.getMessage(), ex);
    }
  }

  // ========== Model Readers ==========

  /**
   * Reads the SunSpec Common model (1) for device identification.
   *
   * @param id device ID
   * @return common model data (manufacturer, model, serial number, firmware version)
   */
  @GET
  @Path("/common")
  @Operation(
    summary = "Read Common model",
    description = "Reads the SunSpec Common model (ID 1) containing device identification: "
      + "manufacturer, model, serial number, and firmware version."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Common model data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readCommon(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_COMMON, "Common");
  }

  /**
   * Reads the inverter model (111-113 or 101-103) for real-time data.
   *
   * @param id device ID
   * @return inverter model data (current, voltage, power, energy, status)
   */
  @GET
  @Path("/inverter")
  @Operation(
    summary = "Read inverter model",
    description = "Reads the inverter model (auto-detects Float 111-113 or Int+SF 101-103) "
      + "containing real-time data: AC current, voltage, power, energy, frequency, and status."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Inverter model data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device or inverter model not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readInverter(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    ModbusDeviceEntity device = requireDevice(id);
    DeviceAddress address = DeviceAddress.fromEntity(device);
    LOG.debugf("Reading SunSpec inverter model: device=%d, address=%s", id, address);

    try {
      SunSpecModelData data = sunSpecService.readInverterModel(address);
      return SunSpecModelResponse.fromModelData(id, device.unitId, data);
    } catch (ModbusException ex) {
      throw new DeviceConnectionException("Failed to read inverter model: " + ex.getMessage(), ex);
    } catch (IllegalStateException ex) {
      throw new DeviceConnectionException("SunSpec not available on device " + id + ": " + ex.getMessage(), ex);
    } catch (IllegalArgumentException ex) {
      throw new DeviceNotFoundException("Inverter model not found on device " + id);
    } catch (IOException | TimeoutException ex) {
      throw new DeviceConnectionException("Failed to read inverter model: " + ex.getMessage(), ex);
    }
  }

  /**
   * Reads the meter model (211-214 or 201-204) for real-time data.
   *
   * @param id device ID
   * @return meter model data (voltage, current, power, energy)
   */
  @GET
  @Path("/meter")
  @Operation(
    summary = "Read meter model",
    description = "Reads the meter model (auto-detects Float 211-214 or Int+SF 201-204) "
      + "containing real-time data: AC voltage, current, power, energy, frequency, and power factor."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Meter model data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device or meter model not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readMeter(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    ModbusDeviceEntity device = requireDevice(id);
    DeviceAddress address = DeviceAddress.fromEntity(device);
    LOG.debugf("Reading SunSpec meter model: device=%d, address=%s", id, address);

    try {
      SunSpecModelData data = sunSpecService.readMeterModel(address);
      return SunSpecModelResponse.fromModelData(id, device.unitId, data);
    } catch (ModbusException ex) {
      throw new DeviceConnectionException("Failed to read meter model: " + ex.getMessage(), ex);
    } catch (IllegalStateException ex) {
      throw new DeviceConnectionException("SunSpec not available on device " + id + ": " + ex.getMessage(), ex);
    } catch (IllegalArgumentException ex) {
      throw new DeviceNotFoundException("Meter model not found on device " + id);
    } catch (IOException | TimeoutException ex) {
      throw new DeviceConnectionException("Failed to read meter model: " + ex.getMessage(), ex);
    }
  }

  /**
   * Reads the Nameplate Ratings model (120).
   *
   * @param id device ID
   * @return nameplate ratings (max power, voltage, current ratings)
   */
  @GET
  @Path("/nameplate")
  @Operation(
    summary = "Read Nameplate Ratings model",
    description = "Reads the Nameplate Ratings model (ID 120) containing maximum ratings: "
      + "VA, W, VAr, PF, current, voltage, and energy ratings."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Nameplate ratings data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readNameplate(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_NAMEPLATE, "Nameplate Ratings");
  }

  /**
   * Reads the Basic Settings model (121).
   *
   * @param id device ID
   * @return basic settings data
   */
  @GET
  @Path("/settings")
  @Operation(
    summary = "Read Basic Settings model",
    description = "Reads the Basic Settings model (ID 121) containing configured limits: "
      + "max power, PF, VAr settings."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Settings data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readSettings(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_SETTINGS, "Basic Settings");
  }

  /**
   * Reads the Extended Measurements and Status model (122).
   *
   * @param id device ID
   * @return status data (operating state, event flags, counters)
   */
  @GET
  @Path("/status")
  @Operation(
    summary = "Read Extended Measurements & Status model",
    description = "Reads the Extended Measurements & Status model (ID 122) containing "
      + "PV voltage/current, operating state, and accumulated energy counters."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Status data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readStatus(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_STATUS, "Extended Measurements & Status");
  }

  /**
   * Reads the Immediate Controls model (123).
   *
   * @param id device ID
   * @return controls data (power limit, PF control settings)
   */
  @GET
  @Path("/controls")
  @Operation(
    summary = "Read Immediate Controls model",
    description = "Reads the Immediate Controls model (ID 123) containing power limit "
      + "and power factor control settings. Note: writing to this model requires "
      + "frodo.modbus.write-enabled=true."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Controls data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readControls(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_CONTROLS, "Immediate Controls");
  }

  /**
   * Reads the Basic Storage Controls model (124).
   *
   * @param id device ID
   * @return storage data (charge/discharge control, state of charge)
   */
  @GET
  @Path("/storage")
  @Operation(
    summary = "Read Basic Storage Controls model",
    description = "Reads the Basic Storage Controls model (ID 124), available on devices "
      + "with battery storage. Contains charge/discharge control and state of charge. "
      + "Note: writing to this model requires frodo.modbus.write-enabled=true."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Storage data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readStorage(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_STORAGE, "Basic Storage Controls");
  }

  /**
   * Reads the MPPT Inverter Extension model (160).
   *
   * @param id device ID
   * @return MPPT data (per-tracker DC voltage, current, power, energy)
   */
  @GET
  @Path("/mppt")
  @Operation(
    summary = "Read MPPT model",
    description = "Reads the Multiple MPPT Inverter Extension model (ID 160) containing "
      + "per-tracker DC voltage, current, power, and energy data."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "MPPT data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readMppt(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    return readModelEndpoint(id, SunSpecConstants.MODEL_MPPT, "MPPT");
  }

  /**
   * Reads a SunSpec model by its numeric model ID.
   *
   * <p>This is a generic endpoint that can read any known SunSpec model.
   * Use the named endpoints (e.g. /common, /inverter) for convenience.</p>
   *
   * @param id      device ID
   * @param modelId SunSpec model ID to read
   * @return model data
   */
  @GET
  @Path("/model/{modelId}")
  @Operation(
    summary = "Read model by ID",
    description = "Reads any SunSpec model by its numeric model ID. The model must be "
      + "present on the device and have a known field definition."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Model data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or model not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public SunSpecModelResponse readModel(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id,
    @Parameter(description = "SunSpec model ID (e.g. 1, 113, 120)", required = true)
    @PathParam("modelId") int modelId
  ) {
    return readModelEndpoint(id, modelId, SunSpecConstants.modelName(modelId));
  }

  /**
   * Reads all discovered models from a device in a single call.
   *
   * @param id device ID
   * @return list of all readable model data
   */
  @GET
  @Path("/models")
  @Operation(
    summary = "Read all models",
    description = "Reads all discovered and known SunSpec models from the device. "
      + "Unknown models (no field definition) are skipped."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "List of model data",
      content = @Content(schema = @Schema(implementation = SunSpecModelResponse[].class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public List<SunSpecModelResponse> readAllModels(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    ModbusDeviceEntity device = requireDevice(id);
    DeviceAddress address = DeviceAddress.fromEntity(device);
    LOG.debugf("Reading all SunSpec models: device=%d, address=%s", id, address);

    try {
      List<SunSpecModelData> dataList = sunSpecService.readAllModels(address);
      return SunSpecModelResponse.fromModelDataList(id, device.unitId, dataList);
    } catch (ModbusException ex) {
      throw new DeviceConnectionException("Failed to read models: " + ex.getMessage(), ex);
    } catch (IllegalStateException ex) {
      throw new DeviceConnectionException("SunSpec not available on device " + id + ": " + ex.getMessage(), ex);
    } catch (IOException | TimeoutException ex) {
      throw new DeviceConnectionException("Failed to read models: " + ex.getMessage(), ex);
    }
  }

  // ========== Private Helper Methods ==========

  /**
   * Loads and validates a device entity, throwing DeviceNotFoundException if not found.
   *
   * @param id device ID
   * @return the device entity
   * @throws DeviceNotFoundException if the device does not exist
   */
  private ModbusDeviceEntity requireDevice(Long id) {
    return deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));
  }

  /**
   * Common implementation for reading a specific SunSpec model by ID.
   *
   * @param deviceId  device ID
   * @param modelId   SunSpec model ID
   * @param modelName model name for logging
   * @return the model response
   */
  private SunSpecModelResponse readModelEndpoint(Long deviceId, int modelId, String modelName) {
    ModbusDeviceEntity device = requireDevice(deviceId);
    DeviceAddress address = DeviceAddress.fromEntity(device);
    LOG.debugf("Reading SunSpec %s model (ID %d): device=%d, address=%s",
      modelName, Integer.valueOf(modelId), deviceId, address);

    try {
      SunSpecModelData data = sunSpecService.readModel(address, modelId);
      return SunSpecModelResponse.fromModelData(deviceId, device.unitId, data);
    } catch (ModbusException ex) {
      throw new DeviceConnectionException(
        String.format("Failed to read %s model: %s", modelName, ex.getMessage()), ex);
    } catch (IllegalStateException ex) {
      throw new DeviceConnectionException(
        String.format("SunSpec not available on device %d: %s", deviceId, ex.getMessage()), ex);
    } catch (IllegalArgumentException ex) {
      throw new DeviceNotFoundException(
        String.format("Model %d (%s) not found on device %d", modelId, modelName, deviceId));
    } catch (IOException | TimeoutException ex) {
      throw new DeviceConnectionException(
        String.format("Failed to read %s model: %s", modelName, ex.getMessage()), ex);
    }
  }
}
