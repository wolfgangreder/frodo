package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Current cost control runtime configuration.
 */
@Schema(description = "Cost control runtime configuration")
public record CostControlConfigResponse(

  @Schema(description = "Provider ID used for import prices (e.g. MANUAL)", example = "MANUAL")
  String importProviderId,

  @Schema(description = "Provider ID used for export prices (e.g. AWATTAR)", example = "AWATTAR")
  String exportProviderId,

  @Schema(description = "Quartz cron for import price fetch", example = "0 55 * * * ?")
  String importFetchCron,

  @Schema(description = "Quartz cron for export price fetch", example = "0 55 * * * ?")
  String exportFetchCron,

  @Schema(description = "P_Grid sampling interval in seconds", example = "15")
  int sampleIntervalSeconds,

  @Schema(description = "Dead-band threshold in watts; P_Grid within ±deadBandWatts of zero treated as zero",
    example = "10.0")
  double deadBandWatts,

  @Schema(description = "Retention period for hourly energy and cost records in days", example = "365")
  int retentionHourlyDays,

  @Schema(description = "Retention period for monthly cost summaries in years", example = "10")
  int retentionMonthlyYears,

  @Schema(description = "Timestamp of last config update (ISO 8601)", example = "2026-05-10T08:00:00Z")
  String updatedAt
) {
}
