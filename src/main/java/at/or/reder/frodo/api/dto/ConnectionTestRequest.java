package at.or.reder.frodo.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for testing connection to a Modbus device without saving.
 *
 * @param host   Modbus TCP host
 * @param port   Modbus TCP port (1-65535)
 * @param unitId Modbus unit ID (0-247)
 */
public record ConnectionTestRequest(
  @NotBlank(message = "Host is required")
  String host,

  @NotNull(message = "Port is required")
  @Min(value = 1, message = "Port must be between 1 and 65535")
  @Max(value = 65535, message = "Port must be between 1 and 65535")
  Integer port,

  @NotNull(message = "Unit ID is required")
  @Min(value = 0, message = "Unit ID must be between 0 and 247")
  @Max(value = 247, message = "Unit ID must be between 0 and 247")
  Integer unitId
) {
}
