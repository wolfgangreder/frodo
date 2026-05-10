package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request body for creating or updating a grid fee rule.
 */
@Schema(description = "Grid fee create/update request")
public record GridFeeRequest(

  @Schema(description = "Timestamp when this fee becomes active (ISO 8601, no zone)",
    example = "2026-01-01T00:00:00", required = true)
  String validFrom,

  @Schema(description = "Fee type: PERCENT, ABSOLUTE_ENERGY, or ABSOLUTE_TIME",
    example = "ABSOLUTE_ENERGY", required = true)
  String feeType,

  @Schema(description = "Fee value; PERCENT=%, ABSOLUTE_ENERGY=ct/kWh, ABSOLUTE_TIME=EUR/month",
    example = "1.5", required = true)
  BigDecimal feeValue,

  @Schema(description = "Direction fee applies to: IMPORT, EXPORT, or BOTH",
    example = "BOTH", required = true)
  String appliesTo,

  @Schema(description = "Optional description", example = "Network surcharge")
  String description
) {
}
