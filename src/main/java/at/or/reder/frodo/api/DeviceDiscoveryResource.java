package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.DeviceDiscoveryRequest;
import at.or.reder.frodo.api.dto.DeviceDiscoveryResponse;
import at.or.reder.frodo.api.dto.DeviceListResponse;
import at.or.reder.frodo.api.dto.DeviceSummary;
import at.or.reder.frodo.api.dto.DiscoveredDeviceDto;
import at.or.reder.frodo.api.dto.ErrorResponse;
import at.or.reder.frodo.api.dto.ConnectionStatus;
import at.or.reder.frodo.api.exception.DeviceNotFoundException;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.service.DeviceDiscoveryService;
import at.or.reder.frodo.modbus.service.DiscoveredDevice;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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

/**
 * REST API for device discovery operations.
 *
 * <p>Provides endpoints to discover Modbus and Solar API devices on a
 * Fronius gateway, discover sub-devices for an existing device, and
 * list sub-devices of a parent device.</p>
 */
@Path("/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Device Discovery", description = "Device discovery and sub-device management endpoints")
public class DeviceDiscoveryResource {

  private static final Logger LOG = Logger.getLogger(DeviceDiscoveryResource.class);

  @Inject
  DeviceDiscoveryService discoveryService;

  @Inject
  ModbusDeviceRepository deviceRepository;

  /**
   * Triggers device discovery on a Modbus TCP gateway.
   *
   * <p>Scans the given host:port for Modbus/SunSpec devices and optionally
   * queries the Solar API for Ohmpilot devices. If {@code autoSave} is true,
   * discovered devices are persisted to the database.</p>
   *
   * @param request discovery request parameters
   * @return discovery results with list of found devices
   */
  @POST
  @Path("/discover")
  @Blocking
  @Operation(
    summary = "Discover devices",
    description = "Scans a Modbus TCP gateway for connected devices using SunSpec discovery, "
      + "FC 0x2B device identification, and Solar API (if enabled). "
      + "Optionally saves discovered devices with autoSave=true."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Discovery completed",
      content = @Content(schema = @Schema(implementation = DeviceDiscoveryResponse.class))
    ),
    @APIResponse(
      responseCode = "400",
      description = "Validation error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Discovery service unavailable",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public DeviceDiscoveryResponse discoverDevices(@Valid DeviceDiscoveryRequest request) {
    LOG.infof("Discovery requested: host=%s, port=%d, unitIdRanges=%s, autoSave=%b",
      request.host(), request.effectivePort(),
      request.unitIdRanges(), request.effectiveAutoSave());

    List<DiscoveredDevice> discovered;
    if (request.unitIdRanges() != null && !request.unitIdRanges().isBlank()) {
      discovered = discoveryService.discoverDevices(
        request.host(), request.effectivePort(), request.unitIdRanges());
    } else {
      discovered = discoveryService.discoverDevices(
        request.host(), request.effectivePort());
    }

    List<DiscoveredDeviceDto> deviceDtos = DiscoveredDeviceDto.fromModelList(discovered);

    if (request.effectiveAutoSave()) {
      List<ModbusDeviceEntity> saved =
        discoveryService.saveDiscoveredDevices(null, discovered);
      List<Long> savedIds = saved.stream()
        .map(e -> e.id)
        .toList();
      LOG.infof("Auto-saved %d discovered device(s)", savedIds.size());
      return DeviceDiscoveryResponse.withSavedDevices(
        request.host(), request.effectivePort(), deviceDtos, savedIds);
    }

    return DeviceDiscoveryResponse.discoveryOnly(
      request.host(), request.effectivePort(), deviceDtos);
  }

  /**
   * Discovers sub-devices for an existing device.
   *
   * <p>Uses the device's host and port as the Modbus TCP gateway address
   * and scans for additional devices. Discovered devices are saved as
   * children of the specified parent device.</p>
   *
   * @param id parent device ID
   * @return discovery results with saved sub-devices
   */
  @POST
  @Path("/{id}/discover-sub-devices")
  @Blocking
  @Transactional
  @Operation(
    summary = "Discover sub-devices",
    description = "Scans for sub-devices (smart meters, Ohmpilots) connected via the specified "
      + "device's Modbus TCP gateway. Discovered devices are automatically saved as children "
      + "of the parent device."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "Sub-device discovery completed",
      content = @Content(schema = @Schema(implementation = DeviceDiscoveryResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Parent device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @APIResponse(
      responseCode = "503",
      description = "Discovery failed",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public DeviceDiscoveryResponse discoverSubDevices(
    @Parameter(description = "Parent device ID", required = true)
    @PathParam("id") Long id
  ) {
    LOG.infof("Sub-device discovery requested: parentId=%d", id);
    ModbusDeviceEntity parent = deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));

    List<DiscoveredDevice> discovered =
      discoveryService.discoverDevices(parent.host, parent.port);

    List<DiscoveredDeviceDto> deviceDtos = DiscoveredDeviceDto.fromModelList(discovered);

    // Save all discovered devices as children of the parent
    List<ModbusDeviceEntity> saved =
      discoveryService.saveDiscoveredDevices(id, discovered);
    List<Long> savedIds = saved.stream()
      .map(e -> e.id)
      .toList();

    LOG.infof("Sub-device discovery complete: parent=%d, found=%d, saved=%d",
      id, discovered.size(), savedIds.size());

    return DeviceDiscoveryResponse.withSavedDevices(
      parent.host, parent.port, deviceDtos, savedIds);
  }

  /**
   * Lists sub-devices of a parent device.
   *
   * @param id parent device ID
   * @return list of child devices
   */
  @GET
  @Path("/{id}/sub-devices")
  @Transactional
  @Operation(
    summary = "List sub-devices",
    description = "Returns all devices that are children of the specified parent device."
  )
  @APIResponses({
    @APIResponse(
      responseCode = "200",
      description = "List of sub-devices",
      content = @Content(schema = @Schema(implementation = DeviceListResponse.class))
    ),
    @APIResponse(
      responseCode = "404",
      description = "Parent device not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
  })
  public DeviceListResponse getSubDevices(
    @Parameter(description = "Parent device ID", required = true)
    @PathParam("id") Long id
  ) {
    LOG.debugf("Listing sub-devices: parentId=%d", id);

    // Verify parent exists
    deviceRepository.findByIdOptional(id)
      .orElseThrow(() -> DeviceNotFoundException.forId(id));

    List<ModbusDeviceEntity> children = deviceRepository.listChildDevices(id);
    List<DeviceSummary> summaries = children.stream()
      .map(this::toDeviceSummary)
      .toList();

    return new DeviceListResponse(summaries, summaries.size());
  }

  // ========== Private Helper Methods ==========

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
      device.deviceType,
      device.autoDiscovered,
      device.parentDevice != null ? device.parentDevice.id : null,
      status,
      lastSuccessfulRead,
      hasDeviceInfo
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
