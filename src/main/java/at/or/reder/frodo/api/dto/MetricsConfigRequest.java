package at.or.reder.frodo.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for creating or updating a metrics scraping configuration.
 *
 * @param scrapeIntervalSeconds  scrape interval in seconds (1-300)
 * @param enabled                whether scraping is enabled
 * @param storeToDatabase        whether to persist scraped values to DB
 * @param retentionDays          data retention period in days (1-3650)
 * @param parameters             list of parameter configurations
 */
public record MetricsConfigRequest(
  @NotNull(message = "Scrape interval is required")
  @Min(value = 1, message = "Scrape interval must be at least 1 second")
  @Max(value = 300, message = "Scrape interval cannot exceed 300 seconds")
  Integer scrapeIntervalSeconds,

  @NotNull(message = "Enabled status is required")
  Boolean enabled,

  Boolean storeToDatabase,

  @Min(value = 1, message = "Retention must be at least 1 day")
  @Max(value = 3650, message = "Retention cannot exceed 3650 days")
  Integer retentionDays,

  List<ParameterConfigRequest> parameters
) {
}
