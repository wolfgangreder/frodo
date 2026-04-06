package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.ConnectionStatus;
import at.or.reder.frodo.api.dto.ConnectionTestRequest;
import at.or.reder.frodo.api.dto.ConnectionTestResponse;
import at.or.reder.frodo.api.dto.DeviceDetailResponse;
import at.or.reder.frodo.api.dto.DeviceIdentificationDto;
import at.or.reder.frodo.api.dto.DeviceListResponse;
import at.or.reder.frodo.api.dto.DeviceRequest;
import at.or.reder.frodo.api.dto.DeviceSummary;
import at.or.reder.frodo.api.dto.ErrorResponse;
import at.or.reder.frodo.api.exception.DeviceConnectionException;
import at.or.reder.frodo.api.exception.DeviceNotFoundException;
import at.or.reder.frodo.modbus.ModbusException;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.service.ConnectionTestService;
import at.or.reder.frodo.modbus.service.DeviceInfoCacheService;
import at.or.reder.frodo.modbus.service.DeviceInfoCollectorService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * REST API for Modbus device management.
 *
 * <p>Provides CRUD operations for devices and device identification retrieval.</p>
 */
@Path("/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Devices", description = "Modbus device management endpoints")
public class DeviceResource {

  private static final Logger LOG = Logger.getLogger(DeviceResource.class);

  @Inject
  ModbusDeviceRepository deviceRepository;

  @Inject
  DeviceInfoCacheService cacheService;

  @Inject
  DeviceInfoCollectorService collectorService;

  @Inject
  ConnectionTestService connectionTestService;

  /**
   * Lists all Modbus devices.
   *
   * @return list of all devices
   */
  @GET
  @Transactional
  @Operation(summary = "List all devices", description = "Returns a list of all configured Modbus devices")
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "List of devices",
      content = @Content(schema = @Schema(implementation = DeviceListResponse.class))
    )
  })
  public DeviceListResponse listDevices() {
    LOG.debug("Listing all devices");
    List<ModbusDeviceEntity> entities = deviceRepository.listAll();

    List<DeviceSummary> summaries = entities.stream()
      .map(this::toDeviceSummary)
      .toList();

    return new DeviceListResponse(summaries, summaries.size());
  }

  /**
   * Gets details for a specific device including cached device identification.
   *
   * @param id device ID
   * @return device details
   */
  @GET
  @Path("/{id}")
  @Transactional
  @Operation(summary = "Get device details", description = "Returns detailed information for a specific device including cached device identification")
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Device details",
      content = @Content(schema = @Schema(implementation = DeviceDetailResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public DeviceDetailResponse getDevice(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    LOG.debugf("Getting device: id=%d", id);
    ModbusDeviceEntity device = deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));

    return toDeviceDetailResponse(device, true);
  }

  /**
   * Creates a new device.
   *
   * @param request device creation request
   * @return created device details
   */
  @POST
  @Transactional
  @Operation(summary = "Create device", description = "Creates a new Modbus device configuration")
  @APIResponses({
    @APIResponse(
      responseCode = "201",
      description = "Device created",
      content = @Content(schema = @Schema(implementation = DeviceDetailResponse.class))
    ),
    @APIResponse(
      responseCode = "400",
      description = "Validation error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public Response createDevice(@Valid DeviceRequest request) {
    LOG.infof("Creating device: name='%s', connection=%s:%d/%d",
      request.name(), request.host(), request.port(), request.unitId());

    ModbusDeviceEntity device = new ModbusDeviceEntity();
    updateDeviceFromRequest(device, request);
    deviceRepository.save(device);

    DeviceDetailResponse response = toDeviceDetailResponse(device, true);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  /**
   * Tests connection to a Modbus device without saving.
   *
   * @param request connection test request
   * @return connection test result
   */
  @POST
  @Path("/test")
  @Blocking
  @Operation(
    summary = "Test device connection",
    description = "Tests connectivity to a Modbus device without saving the configuration. " +
      "Attempts to establish a TCP connection and read device identification (FC 0x2B)."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Connection test completed",
      content = @Content(schema = @Schema(implementation = ConnectionTestResponse.class))
    ),
    @APIResponse(
      responseCode = "400",
      description = "Validation error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public ConnectionTestResponse testConnection(@Valid ConnectionTestRequest request) {
    LOG.infof("Testing connection: host=%s, port=%d, unitId=%d",
      request.host(), request.port(), request.unitId());

    ConnectionTestService.TestResult result =
      connectionTestService.testConnection(request.host(), request.port(), request.unitId());

    if (result.success() && result.hasIdentification()) {
      var id = result.identification();
      return ConnectionTestResponse.success(
        id.vendorName(),
        id.productCode(),
        id.modelName(),
        id.majorMinorRevision(),
        result.responseTimeMs(),
        result.detectionMethod()
      );
    } else if (result.success()) {
      return ConnectionTestResponse.successWithoutIdentification(
        result.responseTimeMs(),
        result.detectionMethod()
      );
    } else {
      return ConnectionTestResponse.failure(result.errorMessage(), result.responseTimeMs());
    }
  }

  /**
   * Updates an existing device.
   *
   * @param id      device ID
   * @param request device update request
   * @return updated device details
   */
  @PUT
  @Path("/{id}")
  @Transactional
  @Operation(summary = "Update device", description = "Updates an existing Modbus device configuration")
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Device updated",
      content = @Content(schema = @Schema(implementation = DeviceDetailResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "400",
      description = "Validation error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public DeviceDetailResponse updateDevice(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id,
    @Valid DeviceRequest request
  ) {
    LOG.infof("Updating device: id=%d", id);
    ModbusDeviceEntity device = deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));

    updateDeviceFromRequest(device, request);
    deviceRepository.save(device);

    // Invalidate cache when device configuration changes
    cacheService.invalidate(id);

    return toDeviceDetailResponse(device, true);
  }

  /**
   * Deletes a device.
   *
   * @param id device ID
   * @return empty response
   */
  @DELETE
  @Path("/{id}")
  @Transactional
  @Operation(summary = "Delete device", description = "Deletes a Modbus device configuration")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Device deleted"),
    @APIResponse(
      responseCode = "404",
      description = "Device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public Response deleteDevice(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    LOG.infof("Deleting device: id=%d", id);
    ModbusDeviceEntity device = deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));

    deviceRepository.delete(device);
    cacheService.invalidate(id);

    return Response.noContent().build();
  }

  /**
   * Gets device identification (cached or fresh).
   *
   * @param id      device ID
   * @param refresh whether to force a fresh read
   * @return device identification
   */
  @GET
  @Path("/{id}/info")
  @Blocking
  @Transactional
  @Operation(
    summary = "Get device identification",
    description = "Returns device identification data. By default, returns cached data if available. Use ?refresh=true to force a fresh read from the device."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Device identification",
      content = @Content(schema = @Schema(implementation = DeviceDetailResponse.class))
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
  public DeviceDetailResponse getDeviceInfo(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id,
    @Parameter(description = "Force fresh read from device")
    @QueryParam("refresh") @DefaultValue("false") boolean refresh
  ) {
    LOG.debugf("Getting device info: id=%d, refresh=%b", id, Boolean.valueOf(refresh));

    ModbusDeviceEntity device = deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));

    if (refresh) {
      return refreshDeviceInfo(device);
    }

    // Try cache first
    Optional<DeviceIdentification> cached = cacheService.get(id);
    if (cached.isPresent()) {
      LOG.debugf("Returning cached device info: id=%d", id);
      DeviceIdentificationDto dto = DeviceIdentificationDto.fromModel(cached.get(), cached.get().readTime());
      return buildDeviceDetailWithIdentification(device, dto, cached.get().readTime(), true);
    }

    // Cache miss or expired, fetch from device
    LOG.debugf("Cache miss or expired, fetching fresh device info: id=%d", id);
    return refreshDeviceInfo(device);
  }

  /**
   * Manually refreshes device identification.
   *
   * @param id device ID
   * @return device identification
   */
  @POST
  @Path("/{id}/info/refresh")
  @Blocking
  @Transactional
  @Operation(
    summary = "Refresh device identification",
    description = "Manually triggers a fresh read of device identification data from the Modbus device"
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Device identification refreshed",
      content = @Content(schema = @Schema(implementation = DeviceDetailResponse.class))
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
  public DeviceDetailResponse refreshDeviceInfoEndpoint(
    @Parameter(description = "Device ID", required = true)
    @PathParam("id") Long id
  ) {
    LOG.infof("Manual refresh requested: id=%d", id);
    ModbusDeviceEntity device = deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));
    return refreshDeviceInfo(device);
  }

  // ========== Private Helper Methods ==========

  private DeviceDetailResponse refreshDeviceInfo(ModbusDeviceEntity device) {
    try {
      DeviceIdentification identification = collectorService.refreshDevice(device.id);
      DeviceIdentificationDto dto = DeviceIdentificationDto.fromModel(identification, identification.readTime());
      return buildDeviceDetailWithIdentification(device, dto, identification.readTime(), false);
    } catch (ModbusException ex) {
      throw new DeviceConnectionException(
        "Failed to read device identification: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new DeviceConnectionException(
        "Failed to connect to device: " + ex.getMessage(), ex);
    }
  }

  private DeviceDetailResponse buildDeviceDetailWithIdentification(
    ModbusDeviceEntity device, DeviceIdentificationDto identification, Instant lastUpdated, boolean cached) {
    return new DeviceDetailResponse(
      device.id,
      device.name,
      device.host,
      device.port,
      device.unitId,
      device.enabled,
      device.description,
      identification,
      lastUpdated,
      cached,
      identification != null ? ConnectionStatus.CONNECTED : ConnectionStatus.UNKNOWN
    );
  }

  private void updateDeviceFromRequest(ModbusDeviceEntity device, DeviceRequest request) {
    device.name = request.name();
    device.host = request.host();
    device.port = request.port();
    device.unitId = request.unitId();
    device.enabled = request.enabled();
    device.description = request.description();

    if (request.connectionTimeoutSeconds() != null) {
      device.connectionTimeoutSeconds = request.connectionTimeoutSeconds();
    }
    // Note: requestTimeoutSeconds not yet implemented in entity (future enhancement)
  }

  private DeviceSummary toDeviceSummary(ModbusDeviceEntity device) {
    boolean hasDeviceInfo = device.deviceInfo != null;
    Instant lastSuccessfulRead = hasDeviceInfo && device.deviceInfo.lastReadSuccess != null
      && device.deviceInfo.lastReadSuccess && device.deviceInfo.lastReadAt != null
      ? device.deviceInfo.lastReadAt : null;

    ConnectionStatus status = determineConnectionStatus(device);

    return new DeviceSummary(
      device.id,
      device.name,
      device.host,
      device.port,
      device.unitId,
      device.enabled,
      status,
      lastSuccessfulRead,
      hasDeviceInfo
    );
  }

  private DeviceDetailResponse toDeviceDetailResponse(ModbusDeviceEntity device, boolean cached) {
    DeviceIdentificationDto identification = null;
    Instant lastUpdated = null;

    if (device.deviceInfo != null && device.deviceInfo.lastReadSuccess != null
      && device.deviceInfo.lastReadSuccess) {
      DeviceIdentification deviceId = device.deviceInfo.toDeviceIdentification();
      identification = DeviceIdentificationDto.fromModel(deviceId, device.deviceInfo.lastReadAt);
      lastUpdated = device.deviceInfo.lastReadAt;
    }

    ConnectionStatus status = determineConnectionStatus(device);

    return new DeviceDetailResponse(
      device.id,
      device.name,
      device.host,
      device.port,
      device.unitId,
      device.enabled,
      device.description,
      identification,
      lastUpdated,
      cached,
      status
    );
  }

  private ConnectionStatus determineConnectionStatus(ModbusDeviceEntity device) {
    if (device.deviceInfo == null) {
      return ConnectionStatus.UNKNOWN;
    }

    if (device.deviceInfo.lastReadSuccess != null && device.deviceInfo.lastReadSuccess) {
      return ConnectionStatus.CONNECTED;
    }

    if (device.deviceInfo.lastReadSuccess != null && !device.deviceInfo.lastReadSuccess) {
      return ConnectionStatus.FAILED;
    }

    return ConnectionStatus.UNKNOWN;
  }
}
