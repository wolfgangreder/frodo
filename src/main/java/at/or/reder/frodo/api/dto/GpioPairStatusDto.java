package at.or.reder.frodo.api.dto;

/**
 * Status snapshot for a single GPIO pair.
 */
public record GpioPairStatusDto(
  String name,
  boolean available,
  int outputPin,
  Boolean outputPinState,
  boolean outputManualOverride,
  int inputPin,
  String inputBias,
  Boolean inputPinState,
  boolean externalModeActive,
  Long assignedDeviceId,
  String errorMessage
) {}
