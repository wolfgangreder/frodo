package at.or.reder.frodo.api.dto;

import java.math.BigDecimal;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Market price entry response.
 */
@Schema(description = "Market price entry from aWATTar AT")
public record MarketPriceResponse(

  @Schema(description = "Start time of the price hour (ISO 8601)", example = "2024-01-15T14:00:00")
  String startTime,

  @Schema(description = "End time of the price hour (ISO 8601)", example = "2024-01-15T15:00:00")
  String endTime,

  @Schema(description = "Market price in ct/kWh (euro-cents per kilowatt-hour)", example = "4.25")
  BigDecimal priceCt,

  @Schema(description = "When this price was fetched", example = "2024-01-14T14:00:00Z")
  String fetchedAt
) {
}