package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Pre-calculated monthly cost summary.
 */
@Schema(description = "Monthly cost summary")
public record MonthlyCostResponse(

  @Schema(description = "Year-month key (yyyy-MM)", example = "2026-05")
  String yearMonth,

  @Schema(description = "Total kWh imported from grid this month", example = "312.4")
  BigDecimal totalImportKwh,

  @Schema(description = "Total kWh exported to grid this month", example = "98.7")
  BigDecimal totalExportKwh,

  @Schema(description = "Total import cost in EUR", example = "89.04")
  BigDecimal totalImportCostEur,

  @Schema(description = "Total export income in EUR", example = "7.11")
  BigDecimal totalExportIncomeEur,

  @Schema(description = "Total grid fees in EUR", example = "4.45")
  BigDecimal totalFeeEur,

  @Schema(description = "Fixed/standing charge in EUR (from FroFixedCost)", example = "12.00")
  BigDecimal fixedCostEur,

  @Schema(description = "Net cost in EUR (import - export + fees + fixed)", example = "98.38")
  BigDecimal netCostEur,

  @Schema(description = "Number of completed hourly cost records summed", example = "312")
  int hoursCalculated,

  @Schema(description = "Last updated timestamp (ISO 8601)", example = "2026-05-10T13:00:00Z")
  String updatedAt
) {
}
