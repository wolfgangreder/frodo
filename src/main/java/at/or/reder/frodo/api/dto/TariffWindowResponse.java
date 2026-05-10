package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response for a tariff window (fixed-price time slot).
 */
@Schema(description = "Fixed-price tariff window")
public record TariffWindowResponse(

  @Schema(description = "Database ID", example = "1")
  long id,

  @Schema(description = "Price direction: IMPORT or EXPORT", example = "IMPORT")
  String direction,

  @Schema(description = "Tariff valid from date (inclusive, ISO 8601)", example = "2026-01-01")
  String validFrom,

  @Schema(description = "Tariff valid to date (exclusive, ISO 8601); null = still active")
  String validTo,

  @Schema(description = "Days of week this window applies to (comma-separated MON/TUE/…); null = all days",
    example = "MON,TUE,WED,THU,FRI")
  String daysOfWeek,

  @Schema(description = "Window start time within day (HH:mm:ss)", example = "07:00:00")
  String timeFrom,

  @Schema(description = "Window end time within day; 00:00:00 = end-of-day", example = "22:00:00")
  String timeTo,

  @Schema(description = "Fixed price in ct/kWh", example = "32.5")
  BigDecimal priceCt,

  @Schema(description = "Priority; highest wins when multiple windows match", example = "10")
  int priority,

  @Schema(description = "Optional description", example = "Peak import tariff weekdays")
  String description
) {
}
