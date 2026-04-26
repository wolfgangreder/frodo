package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response containing the persisted grid-export schedule for a device.
 *
 * @param deviceId             ID of the device this schedule belongs to
 * @param enabled              whether the schedule is currently active
 * @param blockFrom            time of day when export blocking starts ({@code "HH:mm"});
 *                             not used for {@link ExportBlockStrategy#PRICE_CONTROLLED}
 * @param enableFrom           time of day when export is re-enabled ({@code "HH:mm"});
 *                             not used for {@link ExportBlockStrategy#PRICE_CONTROLLED}
 * @param currentlyBlocked     whether the schedule is currently enforcing a block:
 *                             for time-based strategies this reflects the time window;
 *                             for {@link ExportBlockStrategy#PRICE_CONTROLLED} this is
 *                             {@code true} whenever the schedule is enabled (price is
 *                             evaluated each scheduler tick, not at query time)
 * @param strategy             blocking strategy applied during the window
 * @param limitWatts           fixed power cap in Watts (only relevant for
 *                             {@link ExportBlockStrategy#FIXED_LIMIT})
 * @param exportToleranceWatts allowed export buffer in Watts for
 *                             {@link ExportBlockStrategy#PRICE_CONTROLLED}; the inverter
 *                             may export up to this amount above house load + battery
 *                             demand when the market price is negative (default: 50 W)
 */
@Schema(description = "Persisted daily recurring grid-export schedule")
public record ExportScheduleResponse(

  @Schema(description = "Device ID this schedule belongs to")
  Long deviceId,

  @Schema(description = "Whether the schedule is currently active")
  boolean enabled,

  @Schema(description = "Daily start of export-block window (HH:mm); unused for PRICE_CONTROLLED", example = "11:00")
  String blockFrom,

  @Schema(description = "Daily end of export-block window (HH:mm); unused for PRICE_CONTROLLED", example = "15:00")
  String enableFrom,

  @Schema(description = "true if the schedule is currently enforcing a block")
  boolean currentlyBlocked,

  @Schema(
    description = "Blocking strategy: ZERO_EXPORT_DYNAMIC (closed-loop, needs Solar API), "
      + "FIXED_LIMIT (static watt cap, no Solar API required), "
      + "or PRICE_CONTROLLED (dynamic limit when aWATTar AT price is negative)",
    enumeration = {"ZERO_EXPORT_DYNAMIC", "FIXED_LIMIT", "PRICE_CONTROLLED"}
  )
  ExportBlockStrategy strategy,

  @Schema(
    description = "Fixed power cap in Watts; only meaningful when strategy is FIXED_LIMIT",
    example = "500"
  )
  Integer limitWatts,

  @Schema(
    description = "Allowed export buffer in Watts above house load + battery demand "
      + "when market price is negative; only meaningful for PRICE_CONTROLLED (default: 50 W)",
    example = "50"
  )
  Integer exportToleranceWatts

) {
}
