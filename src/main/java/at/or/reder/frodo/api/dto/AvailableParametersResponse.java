package at.or.reder.frodo.api.dto;

import java.util.List;

/**
 * Response DTO listing all available SunSpec parameters for metrics collection.
 *
 * @param deviceId       device ID
 * @param parameters     list of available parameters grouped by model
 * @param discoveryBased true if the parameter list was obtained from live SunSpec
 *                       discovery (only models actually on the device), false if
 *                       built from the static registry (all known models listed)
 */
public record AvailableParametersResponse(
  Long deviceId,
  List<AvailableParameter> parameters,
  boolean discoveryBased
) {
}
