package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.ErrorResponse;
import at.or.reder.frodo.api.dto.ExportScheduleRequest;
import at.or.reder.frodo.api.dto.ExportScheduleResponse;
import at.or.reder.frodo.api.dto.PowerLimitRequest;
import at.or.reder.frodo.api.dto.SunSpecDiscoveryResponse;
import at.or.reder.frodo.api.dto.SunSpecModelResponse;
import at.or.reder.frodo.api.exception.DeviceConnectionException;
import at.or.reder.frodo.api.exception.DeviceNotFoundException;
import at.or.reder.frodo.modbus.ModbusException;
import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import at.or.reder.frodo.modbus.entity.ExportScheduleEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ExportScheduleRepository;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.service.ExportSchedulerService;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import at.or.reder.frodo.solarapi.SolarApiMetricsService;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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

  @Inject
  ExportScheduleRepository scheduleRepository;

  @Inject
  ExportSchedulerService exportSchedulerService;

  @Inject
  SolarApiMetricsService solarApiMetricsService;

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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
   * Sets the inverter power output limit via SunSpec Model 123 (Immediate Controls).
   *
   * <p>When {@code enable} is {@code true} the behaviour depends on
   * {@code request.limitWatts()}:</p>
   * <ul>
   *   <li>If {@code limitWatts} is present (≥ 1 W): applies a
   *       <em>fixed cap</em> — reads {@code WMax} from Model 120 and sets
   *       {@code WMaxLimPct = round(limitWatts / WMax × 100)}.
   *       No Solar API or Smart Meter needed.</li>
   *   <li>If {@code limitWatts} is absent: reads the current grid power from the
   *       Fronius Solar API snapshot ({@code P_Grid} and {@code P_PV}) and computes
   *       a closed-loop zero-export (Nulleinspeisung) limit.
   *       Requires {@code frodo.solar-api.enabled=true} and a recent scrape.</li>
   * </ul>
   *
   * <p>After the write, the scheduler is notified so it respects a manual re-enable
   * until the block window ends.</p>
   *
   * <p>When {@code enable} is {@code false} {@code WMaxLim_Ena} is set to 0,
   * restoring normal inverter operation.</p>
   *
   * <p>Requires {@code frodo.modbus.write-enabled=true}.
   * Returns HTTP 409 if write operations are disabled.</p>
   *
   * @param id      device ID (the inverter)
   * @param request power limit parameters
   * @return 204 No Content on success
   */
  @POST
  @Path("/controls/power-limit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "Set inverter power output limit",
    description = "When enable=true: if limitWatts is present (≥ 1 W) a fixed cap is applied "
      + "(WMaxLimPct = round(limitWatts/WMax×100), reads WMax from Model 120, no Solar API needed); "
      + "otherwise reads P_Grid and P_PV from the Fronius Solar API snapshot for closed-loop "
      + "zero-export control (requires frodo.solar-api.enabled=true). "
      + "Use enable=false to deactivate the limit and restore normal operation. "
      + "Requires frodo.modbus.write-enabled=true."
  )
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Power limit applied successfully"),
    @APIResponse(
      responseCode = "404",
      description = "Device not found or Model 120 not present",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "409",
      description = "Write operations disabled (frodo.modbus.write-enabled=false)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Device connection failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public Response setPowerLimit(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id,
    PowerLimitRequest request
  ) {
    if (request == null) {
      throw new IllegalArgumentException("Request body is required");
    }

    ModbusDeviceEntity device = requireDevice(id);
    DeviceAddress address = DeviceAddress.fromEntity(device);
    LOG.infof("Set power limit request: device=%d, address=%s, enable=%b, ramp=%s, revert=%s",
      id, address, Boolean.valueOf(request.enable()),
      request.rampSeconds(), request.revertSeconds());

    int ramp   = request.rampSeconds()   != null ? request.rampSeconds()   : 0;
    int revert = request.revertSeconds() != null ? request.revertSeconds() : 0;

    try {
      if (request.enable()) {

        if (request.limitWatts() != null) {
          // Fixed mode (FIXED_LIMIT): cap inverter output to the requested watt limit.
          // WMax is read from Model 120 (WRtg) and the percentage is computed here.
          int limitPct = sunSpecService.computeFixedLimitPct(address, request.limitWatts());
          sunSpecService.setPowerLimit(address, limitPct, true, ramp, revert);

        } else {
          // Dynamic zero-export via Solar API.
          // Primary formula:   targetWatts = -P_Load + (P_Battery ?? 0)
          // Fallback formula:  houseLoad   = P_PV + effectiveGridW
          // 100 W dead-band: grid imports < 100 W are treated as 0 in the fallback
          // to suppress noise near zero-export.
          // Requires frodo.solar-api.enabled=true.
          PowerFlowRealtimeData solarData = solarApiMetricsService.getLastData();
          if (solarData == null || solarData.getSite() == null) {
            throw new DeviceConnectionException(
              "No Solar API data available for dynamic zero-export — "
                + "enable frodo.solar-api.enabled=true and verify the inverter at "
                + device.host + " is reachable");
          }

          PowerFlowRealtimeData.SiteData site = solarData.getSite();
          Double gridW = site.getGridPowerWatts();
          if (gridW == null) {
            throw new DeviceConnectionException(
              "Solar API P_Grid field is not present in the latest site data");
          }

          Double loadW    = site.getLoadPowerWatts();
          Double batteryW = site.getBatteryPowerWatts();
          double pvW      = site.getPVPowerWatts() != null ? site.getPVPowerWatts() : 0.0;

          // 100 W dead-band on grid import
          double effectiveGridW = (gridW > 0.0 && gridW < 100.0) ? 0.0 : gridW;

          final int limitPct;
          if (loadW != null) {
            double battW       = batteryW != null ? batteryW : 0.0;
            double targetWatts = -loadW + battW;
            limitPct = sunSpecService.computeLimitPctFromWatts(address, targetWatts);
            LOG.infof(
              "Dynamic limit (PRIMARY): device=%d, P_Grid=%.1f W (eff=%.1f W),"
                + " P_PV=%.1f W, P_Load=%.1f W, P_Battery=%.1f W → target=%.1f W → %d%%",
              id, gridW, effectiveGridW, pvW, loadW, battW, targetWatts,
              Integer.valueOf(limitPct));
          } else {
            limitPct = sunSpecService.computeZeroExportLimitPct(address, effectiveGridW, pvW);
            LOG.infof(
              "Dynamic limit (FALLBACK): device=%d, P_Grid=%.1f W (eff=%.1f W),"
                + " P_PV=%.1f W, P_Battery=%s W → houseLoad=%.1f W → %d%%",
              id, gridW, effectiveGridW, pvW,
              batteryW != null ? String.format("%.1f", batteryW) : "n/a",
              pvW + effectiveGridW,
              Integer.valueOf(limitPct));
          }

          sunSpecService.setPowerLimit(address, limitPct, true, ramp, revert);
        }

      } else {
        sunSpecService.setPowerLimit(address, 0, false, ramp, revert);
      }

      // Notify scheduler so it respects a manual re-enable (skips re-blocking until window ends)
      exportSchedulerService.notifyManualOverride(id, request.enable());

      return Response.noContent().build();

    } catch (DeviceNotFoundException ex) {
      throw ex; // re-throw as-is
    } catch (IllegalArgumentException ex) {
      // limitWatts <= 0, or Nameplate model (120) not found on this device
      throw new DeviceNotFoundException(
        "Power limit failed on device " + id + ": " + ex.getMessage());
    } catch (IllegalStateException ex) {
      // "Write operations are disabled" must remain a 409 so the UI shows the correct hint.
      // Any other IllegalStateException (SunSpec not found, missing W field, etc.) is a
      // device/data error and should surface as 503.
      if (ex.getMessage() != null && ex.getMessage().contains("Write operations are disabled")) {
        throw ex; // → GlobalExceptionMapper → 409
      }
      throw new DeviceConnectionException("Failed to set power limit: " + ex.getMessage(), ex);
    } catch (ModbusException ex) {
      // Exception code 0x04 (Server Device Failure) commonly means Modbus write access
      // is not enabled in the Fronius inverter's web UI (Communication → Modbus → Enable write).
      String hint = (ex.getExceptionCode() == 0x04)
        ? " (Modbus Server Device Failure – verify that Modbus write access is enabled"
          + " in the inverter's web UI under Communication → Modbus TCP → Enable write)"
        : "";
      throw new DeviceConnectionException("Failed to set power limit: " + ex.getMessage() + hint, ex);
    } catch (IOException | TimeoutException ex) {
      throw new DeviceConnectionException("Failed to set power limit: " + ex.getMessage(), ex);
    }
  }

  // ========== Export Schedule ==========

  /**
   * Returns the daily recurring grid-export schedule configured for this device.
   *
   * @param id device ID
   * @return schedule configuration, or 404 if no schedule has been configured
   */
  @GET
  @Path("/controls/power-limit/schedule")
  @Operation(
    summary = "Get grid-export schedule",
    description = "Returns the daily recurring schedule that controls when the inverter's "
      + "grid export is automatically blocked and re-enabled."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Schedule found",
      content = @Content(schema = @Schema(implementation = ExportScheduleResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "No schedule configured for this device",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public ExportScheduleResponse getExportSchedule(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    requireDevice(id); // validates device exists
    ExportScheduleEntity entity = scheduleRepository.findByDeviceId(id)
      .orElseThrow(() -> new DeviceNotFoundException("No export schedule configured for device " + id));
    return toResponse(entity);
  }

  /**
   * Creates or replaces the daily recurring grid-export schedule for a device.
   *
   * <p>Times use 24-hour {@code HH:mm} format. The schedule is applied by a
   * background scheduler that runs every minute; changes take effect within
   * one minute of saving.</p>
   *
   * <p>Crossing midnight is supported: if {@code blockFrom} is after
   * {@code enableFrom} the block window runs from {@code blockFrom} to midnight
   * and from midnight to {@code enableFrom} (e.g. 22:00–06:00).</p>
   *
   * @param id      device ID
   * @param request schedule parameters
   * @return the persisted schedule
   */
  @PUT
  @Path("/controls/power-limit/schedule")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "Set grid-export schedule",
    description = "Creates or replaces the daily recurring schedule. "
      + "blockFrom and enableFrom are HH:mm 24-hour times. "
      + "Crossing midnight is supported (blockFrom > enableFrom). "
      + "Changes take effect within one minute."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Schedule saved",
      content = @Content(schema = @Schema(implementation = ExportScheduleResponse.class))
    ),
    @APIResponse(
      responseCode = "400",
      description = "Invalid time format or missing fields",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public ExportScheduleResponse setExportSchedule(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id,
    ExportScheduleRequest request
  ) {
    if (request == null) {
      throw new IllegalArgumentException("Request body is required");
    }

    LocalTime blockFrom  = parseTime(request.blockFrom(),  "blockFrom");
    LocalTime enableFrom = parseTime(request.enableFrom(), "enableFrom");

    // Resolve and validate strategy
    ExportBlockStrategy strategy = request.strategy() != null
      ? request.strategy()
      : ExportBlockStrategy.ZERO_EXPORT_DYNAMIC;

    requireDevice(id);
    ExportScheduleEntity entity = scheduleRepository.upsert(
      id, request.enabled(), blockFrom, enableFrom, strategy, request.limitWatts());
    exportSchedulerService.invalidateCache(id);

    LOG.infof(
      "Export schedule saved: device=%d, enabled=%b, blockFrom=%s, enableFrom=%s, strategy=%s, limitWatts=%s",
      id, Boolean.valueOf(request.enabled()), request.blockFrom(), request.enableFrom(),
      strategy, request.limitWatts());

    return toResponse(entity);
  }

  /**
   * Deletes the grid-export schedule for a device.
   *
   * <p>Note: deleting a schedule does not immediately change the device's
   * current WMaxLim_Ena state. Use the manual power-limit toggle if needed.</p>
   *
   * @param id device ID
   * @return 204 No Content on success
   */
  @DELETE
  @Path("/controls/power-limit/schedule")
  @Operation(
    summary = "Delete grid-export schedule",
    description = "Removes the recurring export schedule. "
      + "Does not immediately change the device state — "
      + "use the manual power-limit toggle if the device is currently blocked."
  )
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Schedule deleted"),
    @APIResponse(
      responseCode = "404",
      description = "No schedule found for this device",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  @Blocking
  public Response deleteExportSchedule(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    requireDevice(id);
    if (!scheduleRepository.deleteByDeviceId(id)) {
      throw new DeviceNotFoundException("No export schedule found for device " + id);
    }
    exportSchedulerService.invalidateCache(id);
    LOG.infof("Export schedule deleted for device %d", id);
    return Response.noContent().build();
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

  /**
   * Converts an {@link ExportScheduleEntity} to an {@link ExportScheduleResponse},
   * computing whether the current wall-clock time falls inside the block window.
   */
  private static ExportScheduleResponse toResponse(ExportScheduleEntity entity) {
    LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
    boolean currentlyBlocked = entity.enabled
      && ExportSchedulerService.isInBlockWindow(now, entity.blockFrom, entity.enableFrom);

    return new ExportScheduleResponse(
      entity.deviceId,
      entity.enabled,
      entity.blockFrom.format(TIME_FMT),
      entity.enableFrom.format(TIME_FMT),
      currentlyBlocked,
      entity.strategy,
      entity.limitWatts
    );
  }

  /**
   * Parses a {@code "HH:mm"} time string, throwing {@link IllegalArgumentException}
   * with a descriptive message on failure.
   *
   * @param value     the string to parse
   * @param fieldName field name for the error message
   * @return parsed {@link LocalTime}
   */
  private static LocalTime parseTime(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    try {
      return LocalTime.parse(value.strip(), TIME_FMT);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
        fieldName + " must be in HH:mm format (24-hour), got: '" + value + "'");
    }
  }
}
