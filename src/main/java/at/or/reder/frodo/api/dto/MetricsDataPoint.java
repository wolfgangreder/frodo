package at.or.reder.frodo.api.dto;

import java.time.Instant;

/**
 * A single metrics data point.
 *
 * @param timestamp    when the value was recorded
 * @param modelId      SunSpec model ID
 * @param fieldName    field name within the model
 * @param numericValue numeric value (null if string value)
 * @param stringValue  string value (null if numeric value)
 */
public record MetricsDataPoint(
  Instant timestamp,
  Integer modelId,
  String fieldName,
  Double numericValue,
  String stringValue
) {
}
