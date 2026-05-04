package at.or.reder.frodo.api.dto;

import java.time.Instant;

/**
 * Response for a GPIO pair ↔ device assignment.
 */
public record GpioAssignmentDto(
  Long deviceId,
  String gpioPairName,
  Instant updatedAt
) {}
