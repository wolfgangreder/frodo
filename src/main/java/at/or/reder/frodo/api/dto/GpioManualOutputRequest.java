package at.or.reder.frodo.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for manual GPIO output override.
 *
 * @param high {@code true} to drive output HIGH, {@code false} to drive LOW
 */
public record GpioManualOutputRequest(
  @NotNull Boolean high
) {}
