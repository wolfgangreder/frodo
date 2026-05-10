package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request body for creating a fixed/standing charge entry.
 */
@Schema(description = "Fixed cost create request")
public record FixedCostRequest(

  @Schema(description = "Energy flow direction: IMPORT, EXPORT, or BOTH", example = "BOTH", required = true)
  String direction,

  @Schema(description = "Date from which this cost is active (yyyy-MM-dd)", example = "2026-01-01", required = true)
  String validFrom,

  @Schema(description = "Monthly fixed cost in EUR", example = "12.00", required = true)
  BigDecimal monthlyCostEur,

  @Schema(description = "Optional description", example = "Grid connection fee")
  String description
) {
}
