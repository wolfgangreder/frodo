package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.AggregationMode;
import at.or.reder.frodo.modbus.entity.MetricsParameterEntity;

/**
 * Response DTO for a single metrics parameter configuration.
 *
 * @param id               parameter entity ID
 * @param sunspecModelId   SunSpec model ID
 * @param fieldName        field name within the model
 * @param enabled          whether this parameter is enabled
 * @param customMetricName custom Prometheus metric name (may be null)
 * @param aggregationMode  how scraped values are reduced before DB writes
 */
public record ParameterConfigResponse(
  Long id,
  Integer sunspecModelId,
  String fieldName,
  Boolean enabled,
  String customMetricName,
  AggregationMode aggregationMode
) {

  /**
   * Creates a response from a parameter entity.
   *
   * @param entity parameter entity
   * @return response DTO
   */
  public static ParameterConfigResponse from(MetricsParameterEntity entity) {
    return new ParameterConfigResponse(
      entity.id,
      entity.sunspecModelId,
      entity.fieldName,
      entity.enabled,
      entity.customMetricName,
      entity.aggregationMode
    );
  }
}
