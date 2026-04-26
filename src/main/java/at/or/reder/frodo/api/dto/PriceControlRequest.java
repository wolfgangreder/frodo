package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code PUT /api/price-control}.
 */
@Schema(description = "Global price-controlled export setting")
public record PriceControlRequest(

  @Schema(description = "Whether global price-controlled export limiting is enabled",
          example = "true")
  boolean enabled,

  @Schema(description = "Allowed grid export above load + battery demand when price is negative, in Watts. "
          + "0 = strict zero-export. Default: 50 W.",
          example = "50")
  Integer exportToleranceWatts

) {}
