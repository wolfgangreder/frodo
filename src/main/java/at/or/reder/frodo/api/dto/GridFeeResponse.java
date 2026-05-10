package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response for a grid surcharge fee rule.
 */
@Schema(description = "Grid fee rule")
public record GridFeeResponse(

  @Schema(description = "Database ID", example = "1")
  long id,

  @Schema(description = "Timestamp when this fee became active (ISO 8601)", example = "2026-01-01T00:00:00")
  String validFrom,

  @Schema(description = "Fee calculation type: PERCENT, ABSOLUTE_ENERGY, or ABSOLUTE_TIME",
    example = "ABSOLUTE_ENERGY")
  String feeType,

  @Schema(description = "Fee value; PERCENT=%, ABSOLUTE_ENERGY=ct/kWh, ABSOLUTE_TIME=EUR/month",
    example = "1.5")
  BigDecimal feeValue,

  @Schema(description = "Which direction fee applies to: IMPORT, EXPORT, or BOTH", example = "BOTH")
  String appliesTo,

  @Schema(description = "Optional description", example = "Network surcharge")
  String description
) {
}
