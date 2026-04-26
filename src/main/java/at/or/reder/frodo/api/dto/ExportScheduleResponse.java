package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response containing the persisted grid-export schedule for a device.
 *
 * @param deviceId         ID of the device this schedule belongs to
 * @param enabled          whether the schedule is currently active
 * @param blockFrom        time of day when export blocking starts ({@code "HH:mm"})
 * @param enableFrom       time of day when export is re-enabled ({@code "HH:mm"})
 * @param currentlyBlocked whether the current wall-clock time falls inside the block window,
 *                         computed server-side at the moment of the API call
 * @param strategy         blocking strategy applied during the window
 * @param limitWatts       fixed power cap in Watts (only relevant for
 *                         {@link ExportBlockStrategy#FIXED_LIMIT})
 */
@Schema(description = "Persisted daily recurring grid-export schedule")
public record ExportScheduleResponse(

  @Schema(description = "Device ID this schedule belongs to")
  Long deviceId,

  @Schema(description = "Whether the schedule is currently active")
  boolean enabled,

  @Schema(description = "Daily start of export-block window (HH:mm)", example = "11:00")
  String blockFrom,

  @Schema(description = "Daily end of export-block window (HH:mm)", example = "15:00")
  String enableFrom,

  @Schema(description = "true if the current time is inside the configured block window")
  boolean currentlyBlocked,

  @Schema(
    description = "Blocking strategy: ZERO_EXPORT_DYNAMIC (closed-loop, needs Smart Meter) "
      + "or FIXED_LIMIT (static watt cap, no Smart Meter required)",
    enumeration = {"ZERO_EXPORT_DYNAMIC", "FIXED_LIMIT"}
  )
  ExportBlockStrategy strategy,

  @Schema(
    description = "Fixed power cap in Watts; only meaningful when strategy is FIXED_LIMIT",
    example = "50"
  )
  Integer limitWatts

) {
}
