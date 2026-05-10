package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request body for creating or updating a tariff window.
 */
@Schema(description = "Tariff window create/update request")
public record TariffWindowRequest(

  @Schema(description = "Price direction: IMPORT or EXPORT", example = "IMPORT", required = true)
  String direction,

  @Schema(description = "Tariff valid from date (yyyy-MM-dd)", example = "2026-01-01", required = true)
  String validFrom,

  @Schema(description = "Tariff valid to date (yyyy-MM-dd, exclusive); omit or null = still active")
  String validTo,

  @Schema(description = "Days of week (comma-separated MON/TUE/WED/THU/FRI/SAT/SUN); null = all days",
    example = "MON,TUE,WED,THU,FRI")
  String daysOfWeek,

  @Schema(description = "Window start time (HH:mm:ss)", example = "07:00:00", required = true)
  String timeFrom,

  @Schema(description = "Window end time (HH:mm:ss); 00:00:00 = end-of-day", example = "22:00:00",
    required = true)
  String timeTo,

  @Schema(description = "Fixed price in ct/kWh", example = "32.5", required = true)
  BigDecimal priceCt,

  @Schema(description = "Priority; highest wins when multiple windows match", example = "10")
  int priority,

  @Schema(description = "Optional description", example = "Peak import tariff weekdays")
  String description
) {
}
