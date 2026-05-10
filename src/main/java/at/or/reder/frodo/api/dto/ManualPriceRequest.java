package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request body for manually setting a price for one direction and one hour.
 */
@Schema(description = "Manual price entry request")
public record ManualPriceRequest(

  @Schema(description = "Hour start (ISO 8601 local, no zone, e.g. 2026-05-10T13:00:00)",
    example = "2026-05-10T13:00:00", required = true)
  String hourStart,

  @Schema(description = "Price in ct/kWh", example = "28.5", required = true)
  BigDecimal priceCt
) {
}
