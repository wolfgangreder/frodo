package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.GpioAssignmentDto;
import at.or.reder.frodo.api.dto.GpioAssignmentRequest;
import at.or.reder.frodo.api.dto.GpioManualOutputRequest;
import at.or.reder.frodo.api.dto.GpioPairStatusDto;
import at.or.reder.frodo.api.dto.GpioStatusDto;
import at.or.reder.frodo.gpio.GpioConfig;
import at.or.reder.frodo.gpio.GpioPairStatus;
import at.or.reder.frodo.gpio.GpioService;
import at.or.reder.frodo.gpio.GpioStatus;
import at.or.reder.frodo.modbus.entity.GpioDeviceAssignmentEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.GpioDeviceAssignmentRepository;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * REST resource for GPIO export control management.
 *
 * <p>Provides status, manual pin testing, and device-to-pair assignments.</p>
 */
@Path("/gpio")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "GPIO", description = "GPIO-based export control (RPi5 only)")
public class GpioResource {

  private static final Logger LOG = Logger.getLogger(GpioResource.class);

  @Inject
  GpioService gpioService;

  @Inject
  GpioConfig gpioConfig;

  @Inject
  GpioDeviceAssignmentRepository assignmentRepository;

  @Inject
  ModbusDeviceRepository deviceRepository;

  // ========== Status ==========

  @GET
  @Path("/status")
  @Operation(summary = "Get GPIO system status", description = "Returns full system and per-pair status snapshot.")
  public GpioStatusDto getStatus() {
    GpioStatus status = gpioService.getStatus();
    return toStatusDto(status);
  }

  // ========== Pairs ==========

  @GET
  @Path("/pairs")
  @Operation(summary = "List configured pair names", description = "Returns all GPIO pair names from application.properties.")
  public Set<String> getPairs() {
    return gpioService.getConfiguredPairNames();
  }

  @PUT
  @Path("/pairs/{name}/output")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Set manual output override", description = "Directly drives the output pin HIGH or LOW for testing.")
  public Response setManualOutput(@PathParam("name") String name,
                                  @Valid GpioManualOutputRequest request) {
    if (!gpioConfig.pairs().containsKey(name)) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorBody("GPIO pair '" + name + "' not found in configuration"))
        .build();
    }
    if (!gpioService.isPairAvailable(name)) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .entity(new ErrorBody("GPIO pair '" + name + "' is not available"))
        .build();
    }
    try {
      gpioService.setManualOutput(name, request.high());
      return Response.ok().build();
    } catch (IOException e) {
      LOG.errorf(e, "Failed to set manual output for pair '%s'", name);
      return Response.serverError()
        .entity(new ErrorBody("Failed to set manual output: " + e.getMessage()))
        .build();
    }
  }

  @DELETE
  @Path("/pairs/{name}/output")
  @Operation(summary = "Clear manual output override", description = "Clears the manual test override; scheduler resumes automatic control.")
  public Response clearManualOutput(@PathParam("name") String name) {
    if (!gpioConfig.pairs().containsKey(name)) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorBody("GPIO pair '" + name + "' not found in configuration"))
        .build();
    }
    gpioService.clearManualOutput(name);
    return Response.ok().build();
  }

  // ========== Assignments ==========

  @GET
  @Path("/assignments")
  @Operation(summary = "List all GPIO assignments", description = "Returns all device-to-pair assignments.")
  @Transactional
  public List<GpioAssignmentDto> getAssignments() {
    return assignmentRepository.listAll().stream()
      .map(a -> new GpioAssignmentDto(a.deviceId, a.gpioPairName, a.updatedAt))
      .toList();
  }

  @GET
  @Path("/assignments/{deviceId}")
  @Operation(summary = "Get GPIO assignment for a device")
  @Transactional
  public Response getAssignment(@PathParam("deviceId") Long deviceId) {
    Optional<GpioDeviceAssignmentEntity> assignment = assignmentRepository.findByDeviceId(deviceId);
    if (assignment.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorBody("No GPIO assignment for device " + deviceId))
        .build();
    }
    GpioDeviceAssignmentEntity a = assignment.get();
    return Response.ok(new GpioAssignmentDto(a.deviceId, a.gpioPairName, a.updatedAt)).build();
  }

  @PUT
  @Path("/assignments/{deviceId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create or update GPIO assignment", description = "Assigns a GPIO pair to a device.")
  @Transactional
  public Response setAssignment(@PathParam("deviceId") Long deviceId,
                                @Valid GpioAssignmentRequest request) {
    // Validate device exists
    ModbusDeviceEntity device = deviceRepository.findById(deviceId);
    if (device == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorBody("Device " + deviceId + " not found"))
        .build();
    }

    // Validate pair name exists in configuration
    if (!gpioConfig.pairs().containsKey(request.gpioPairName())) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(new ErrorBody("GPIO pair '" + request.gpioPairName()
          + "' not found in configuration"))
        .build();
    }

    // Check pair not already assigned to a different device
    Optional<GpioDeviceAssignmentEntity> existingPair =
      assignmentRepository.findByPairName(request.gpioPairName());
    if (existingPair.isPresent() && !existingPair.get().deviceId.equals(deviceId)) {
      return Response.status(Response.Status.CONFLICT)
        .entity(new ErrorBody("GPIO pair '" + request.gpioPairName()
          + "' is already assigned to device " + existingPair.get().deviceId))
        .build();
    }

    // Create or update
    GpioDeviceAssignmentEntity entity = assignmentRepository.findByDeviceId(deviceId)
      .orElse(new GpioDeviceAssignmentEntity());
    entity.deviceId = deviceId;
    entity.gpioPairName = request.gpioPairName();
    assignmentRepository.persist(entity);

    return Response.ok(new GpioAssignmentDto(entity.deviceId, entity.gpioPairName, entity.updatedAt))
      .build();
  }

  @DELETE
  @Path("/assignments/{deviceId}")
  @Operation(summary = "Remove GPIO assignment", description = "Removes the GPIO pair assignment for a device.")
  @Transactional
  public Response deleteAssignment(@PathParam("deviceId") Long deviceId) {
    Optional<GpioDeviceAssignmentEntity> assignment = assignmentRepository.findByDeviceId(deviceId);
    if (assignment.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorBody("No GPIO assignment for device " + deviceId))
        .build();
    }
    assignmentRepository.delete(assignment.get());
    return Response.noContent().build();
  }

  // ========== Helpers ==========

  private GpioStatusDto toStatusDto(GpioStatus status) {
    List<GpioPairStatusDto> pairs = status.pairs().stream()
      .map(p -> new GpioPairStatusDto(
        p.name(), p.available(), p.outputPin(), p.outputPinState(),
        p.outputManualOverride(), p.inputPin(), p.inputBias(), p.inputPinState(),
        p.externalModeActive(), p.assignedDeviceId(), p.errorMessage()))
      .toList();
    return new GpioStatusDto(
      status.available(), status.isRaspberryPi5(), status.platform(),
      status.errorMessage(), pairs);
  }

  /**
   * Simple error body for JSON error responses.
   */
  private record ErrorBody(String message) {}
}
