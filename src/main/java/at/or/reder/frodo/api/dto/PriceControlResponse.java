package at.or.reder.frodo.api.dto;

import java.math.BigDecimal;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response for {@code GET /api/price-control} and {@code PUT /api/price-control}.
 */
@Schema(description = "Global price-controlled export setting with current market price state")
public record PriceControlResponse(

  @Schema(description = "Whether global price-controlled export limiting is enabled",
          example = "true")
  boolean enabled,

  @Schema(description = "Allowed grid export above load + battery demand when price is negative, in Watts",
          example = "50")
  int exportToleranceWatts,

  @Schema(description = "Current aWATTar AT market price in ct/kWh, or null if not yet available",
          example = "-1.25")
  BigDecimal currentPriceCt,

  @Schema(description = "True when price control is enabled AND the current price is negative "
          + "(i.e. the scheduler is actively limiting export right now)",
          example = "false")
  boolean currentlyBlocking

) {}
