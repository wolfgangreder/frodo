package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.MetricsDataEntity;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for historical metrics data.
 *
 * @param deviceId device ID
 * @param from     start of time range
 * @param to       end of time range
 * @param count    number of data points returned
 * @param data     list of data points
 */
public record MetricsDataResponse(
  Long deviceId,
  Instant from,
  Instant to,
  int count,
  List<MetricsDataPoint> data
) {

  /**
   * Creates a response from a list of data entities.
   *
   * @param deviceId device ID
   * @param entities list of data entities
   * @param from     start of time range
   * @param to       end of time range
   * @return data response
   */
  public static MetricsDataResponse from(Long deviceId, List<MetricsDataEntity> entities,
                                          Instant from, Instant to) {
    List<MetricsDataPoint> points = entities.stream()
      .map(e -> new MetricsDataPoint(
        e.recordedAt,
        e.sunspecModelId,
        e.fieldName,
        e.valueNumeric,
        e.valueString
      ))
      .toList();
    return new MetricsDataResponse(deviceId, from, to, points.size(), points);
  }
}
