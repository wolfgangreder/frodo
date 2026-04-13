package at.or.reder.frodo.api.dto;

/**
 * DTO describing a single SunSpec parameter available for metrics collection.
 *
 * @param modelId     SunSpec model ID (e.g. 113, 124)
 * @param modelName   human-readable model name (e.g. "Inverter (Three Phase, Float)")
 * @param fieldName   field name within the model (e.g. "W", "ChaState")
 * @param units       measurement units (e.g. "W", "V", "A"), may be null
 * @param description human-readable description of the field
 * @param metricName  Prometheus metric name that will be used for this parameter
 *                    (e.g. "frodo_sunspec_ac_power_watts"), resolved from the
 *                    semantic mapping or generated as a fallback
 */
public record AvailableParameter(
  Integer modelId,
  String modelName,
  String fieldName,
  String units,
  String description,
  String metricName
) {
}
