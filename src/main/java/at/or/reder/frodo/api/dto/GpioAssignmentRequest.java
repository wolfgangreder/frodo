package at.or.reder.frodo.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a GPIO pair assignment.
 *
 * @param gpioPairName name of the GPIO pair as configured in application.properties
 */
public record GpioAssignmentRequest(
  @NotNull @Size(min = 1, max = 64) String gpioPairName
) {}
