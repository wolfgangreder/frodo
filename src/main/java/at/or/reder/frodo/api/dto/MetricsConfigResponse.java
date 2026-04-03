package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a metrics scraping configuration.
 *
 * @param deviceId              device ID
 * @param scrapeIntervalSeconds scrape interval in seconds
 * @param enabled               whether scraping is enabled
 * @param storeToDatabase       whether DB storage is enabled
 * @param retentionDays         data retention period in days
 * @param parameters            list of parameter configurations
 * @param lastScrapeTime        timestamp of last scrape
 * @param lastScrapeStatus      status of last scrape (SUCCESS, FAILED, TIMEOUT)
 * @param lastErrorMessage      error message from last failed scrape
 */
public record MetricsConfigResponse(
  Long deviceId,
  Integer scrapeIntervalSeconds,
  Boolean enabled,
  Boolean storeToDatabase,
  Integer retentionDays,
  List<ParameterConfigResponse> parameters,
  Instant lastScrapeTime,
  String lastScrapeStatus,
  String lastErrorMessage
) {

  /**
   * Creates a default (unsaved) config response for a device.
   *
   * @param deviceId device ID
   * @return default config response
   */
  public static MetricsConfigResponse defaultConfig(Long deviceId) {
    return new MetricsConfigResponse(
      deviceId, 30, false, true, 365,
      List.of(), null, null, null
    );
  }

  /**
   * Creates a response from a config entity.
   *
   * @param entity metrics config entity
   * @return response DTO
   */
  public static MetricsConfigResponse from(MetricsConfigEntity entity) {
    return new MetricsConfigResponse(
      entity.device.id,
      entity.scrapeIntervalSeconds,
      entity.enabled,
      entity.storeToDatabase,
      entity.retentionDays,
      entity.parameters.stream()
        .map(ParameterConfigResponse::from)
        .toList(),
      entity.lastScrapeTime,
      entity.lastScrapeStatus != null ? entity.lastScrapeStatus.name() : null,
      entity.lastErrorMessage
    );
  }
}
