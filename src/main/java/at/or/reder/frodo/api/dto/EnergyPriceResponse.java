package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Hourly raw energy price from a provider.
 */
@Schema(description = "Hourly energy price from provider")
public record EnergyPriceResponse(

  @Schema(description = "Start of the hour (ISO 8601 UTC)", example = "2026-05-10T13:00:00")
  String startTime,

  @Schema(description = "End of the hour (ISO 8601 UTC)", example = "2026-05-10T14:00:00")
  String endTime,

  @Schema(description = "Import price in ct/kWh; null if not yet fetched")
  BigDecimal priceImportCt,

  @Schema(description = "Export price in ct/kWh; null if not yet fetched")
  BigDecimal priceExportCt,

  @Schema(description = "Provider that delivered import price", example = "MANUAL")
  String importSource,

  @Schema(description = "Provider that delivered export price", example = "AWATTAR")
  String exportSource,

  @Schema(description = "Record created at (ISO 8601)", example = "2026-05-10T12:55:00Z")
  String createdAt,

  @Schema(description = "Record last updated at (ISO 8601)", example = "2026-05-10T12:55:01Z")
  String updatedAt
) {
}
