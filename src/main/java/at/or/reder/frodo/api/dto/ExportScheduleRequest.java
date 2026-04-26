package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for creating or updating a device's daily recurring grid-export schedule.
 *
 * <p>Times are expressed in {@code HH:mm} 24-hour format (e.g. {@code "11:00"},
 * {@code "15:30"}).  Crossing midnight is supported: if {@code blockFrom} is after
 * {@code enableFrom} the block window runs from {@code blockFrom} to midnight and
 * from midnight to {@code enableFrom}.</p>
 *
 * <p>Two blocking strategies are available:</p>
 * <ul>
 *   <li>{@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC} (default) — closed-loop
 *       Nulleinspeisung; reads the Smart Meter each minute and sets the inverter
 *       limit to exactly the current house load.  Requires an enabled Smart Meter
 *       child device.</li>
 *   <li>{@link ExportBlockStrategy#FIXED_LIMIT} — writes a constant
 *       {@code WMaxLimPct} derived from {@code limitWatts}.  No Smart Meter needed;
 *       allows a small, configurable grid feed-in.</li>
 * </ul>
 *
 * @param enabled     whether the schedule should be active immediately after saving
 * @param blockFrom   time of day to start blocking grid export (format {@code "HH:mm"})
 * @param enableFrom  time of day to re-enable grid export (format {@code "HH:mm"})
 * @param strategy    blocking strategy; {@code null} defaults to
 *                    {@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC}
 * @param limitWatts  fixed power cap in Watts; required (and must be &gt; 0)
 *                    when {@code strategy} is {@link ExportBlockStrategy#FIXED_LIMIT}
 */
@Schema(description = "Daily recurring schedule for automatic grid-export blocking")
public record ExportScheduleRequest(

  @Schema(description = "Whether this schedule is active", required = true)
  boolean enabled,

  @Schema(
    description = "Time of day to start blocking grid export, format HH:mm (24 h)",
    example = "11:00",
    pattern = "^([01]\\d|2[0-3]):[0-5]\\d$",
    required = true
  )
  String blockFrom,

  @Schema(
    description = "Time of day to re-enable grid export, format HH:mm (24 h)",
    example = "15:00",
    pattern = "^([01]\\d|2[0-3]):[0-5]\\d$",
    required = true
  )
  String enableFrom,

  @Schema(
    description = "Blocking strategy applied during the window. "
      + "ZERO_EXPORT_DYNAMIC (default): closed-loop zero-export using the Smart Meter. "
      + "FIXED_LIMIT: static watt cap — no Smart Meter required, use limitWatts to set the cap.",
    defaultValue = "ZERO_EXPORT_DYNAMIC",
    enumeration = {"ZERO_EXPORT_DYNAMIC", "FIXED_LIMIT"}
  )
  ExportBlockStrategy strategy,

  @Schema(
    description = "Fixed power cap in Watts. Required and must be > 0 when strategy is FIXED_LIMIT.",
    example = "50"
  )
  Integer limitWatts

) {
}
