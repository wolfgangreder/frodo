package at.or.reder.frodo.api.exception;

import at.or.reder.frodo.api.dto.ErrorResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Global exception mapper for REST API exceptions.
 *
 * <p>Maps application exceptions to structured JSON error responses.</p>
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

  private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

  @Context
  UriInfo uriInfo;

  @Override
  public Response toResponse(Exception exception) {
    if (exception instanceof DeviceNotFoundException) {
      return handleNotFound((DeviceNotFoundException) exception);
    } else if (exception instanceof DeviceConnectionException) {
      return handleConnectionError((DeviceConnectionException) exception);
    } else if (exception instanceof IllegalStateException) {
      return handleConflict((IllegalStateException) exception);
    } else if (exception instanceof IllegalArgumentException) {
      return handleBadRequest((IllegalArgumentException) exception);
    } else {
      return handleGenericError(exception);
    }
  }

  private Response handleNotFound(DeviceNotFoundException exception) {
    LOG.debugf("Device not found: %s", exception.getMessage());
    ErrorResponse error = new ErrorResponse(
      404,
      "Not Found",
      exception.getMessage(),
      Instant.now(),
      getRequestPath()
    );
    return Response.status(Response.Status.NOT_FOUND).entity(error).build();
  }

  private Response handleConnectionError(DeviceConnectionException exception) {
    LOG.warnf(exception, "Device connection error: %s", exception.getMessage());
    ErrorResponse error = new ErrorResponse(
      503,
      "Service Unavailable",
      exception.getMessage(),
      Instant.now(),
      getRequestPath()
    );
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(error).build();
  }

  private Response handleConflict(IllegalStateException exception) {
    LOG.warnf("Conflict: %s", exception.getMessage());
    ErrorResponse error = new ErrorResponse(
      409,
      "Conflict",
      exception.getMessage(),
      Instant.now(),
      getRequestPath()
    );
    return Response.status(Response.Status.CONFLICT).entity(error).build();
  }

  private Response handleBadRequest(IllegalArgumentException exception) {
    LOG.debugf("Bad request: %s", exception.getMessage());
    ErrorResponse error = new ErrorResponse(
      400,
      "Bad Request",
      exception.getMessage(),
      Instant.now(),
      getRequestPath()
    );
    return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
  }

  private Response handleGenericError(Exception exception) {
    LOG.errorf(exception, "Unexpected error: %s", exception.getMessage());
    ErrorResponse error = new ErrorResponse(
      500,
      "Internal Server Error",
      "An unexpected error occurred: " + exception.getMessage(),
      Instant.now(),
      getRequestPath()
    );
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
  }

  private String getRequestPath() {
    return uriInfo != null ? uriInfo.getPath() : "unknown";
  }
}
