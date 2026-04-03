package at.or.reder.frodo.api.dto;

import java.util.List;

/**
 * Response DTO listing all available SunSpec parameters for metrics collection.
 *
 * @param deviceId   device ID
 * @param parameters list of available parameters grouped by model
 */
public record AvailableParametersResponse(
  Long deviceId,
  List<AvailableParameter> parameters
) {
}
