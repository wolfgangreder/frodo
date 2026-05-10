package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Fixed/standing charge response.
 */
@Schema(description = "Fixed cost entry")
public record FixedCostResponse(

  @Schema(description = "Entry ID", example = "1")
  long id,

  @Schema(description = "Energy flow direction: IMPORT, EXPORT, or BOTH", example = "BOTH")
  String direction,

  @Schema(description = "Date from which this cost is active (yyyy-MM-dd)", example = "2026-01-01")
  String validFrom,

  @Schema(description = "Monthly fixed cost in EUR", example = "12.00")
  BigDecimal monthlyCostEur,

  @Schema(description = "Optional description", example = "Grid connection fee")
  String description
) {
}
