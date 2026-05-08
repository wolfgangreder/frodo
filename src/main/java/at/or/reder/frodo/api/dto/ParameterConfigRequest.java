package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.AggregationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for configuring a single metrics parameter.
 *
 * @param sunspecModelId   SunSpec model ID (e.g. 113, 124)
 * @param fieldName        field name within the model (e.g. "W", "ChaState")
 * @param enabled          whether this parameter is enabled for collection
 * @param customMetricName optional custom Prometheus metric name
 * @param aggregationMode  how scraped values are reduced before DB writes
 *                         (defaults to {@code MINUTE_AVERAGE} when null)
 */
public record ParameterConfigRequest(
  @NotNull(message = "SunSpec model ID is required")
  Integer sunspecModelId,

  @NotBlank(message = "Field name is required")
  String fieldName,

  @NotNull(message = "Enabled status is required")
  Boolean enabled,

  String customMetricName,

  AggregationMode aggregationMode
) {
}
