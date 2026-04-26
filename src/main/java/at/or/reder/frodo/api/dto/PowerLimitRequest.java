package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for setting the inverter power output limit via SunSpec Model 123.
 *
 * <p>When {@code enable} is {@code true} the server computes the limit based on the
 * supplied parameters:</p>
 * <ul>
 *   <li>If {@code limitWatts} is provided (≥ 1): applies a fixed watt cap
 *       ({@code limitPct = limitWatts / WMax × 100}). No Smart Meter read is required.</li>
 *   <li>If {@code limitWatts} is absent (null): reads the Smart Meter's real-time power
 *       and computes a closed-loop zero-export (Nulleinspeisung) limit. Requires an
 *       enabled Smart Meter child device.</li>
 * </ul>
 *
 * @param limitPercent  (optional, ignored) legacy field – the server now computes the limit.
 * @param enable        {@code true} to activate the power limit (WMaxLim_Ena = 1),
 *                      {@code false} to deactivate it (WMaxLim_Ena = 0), restoring
 *                      normal inverter operation.
 * @param limitWatts    optional fixed watt cap; when set the server uses a fixed
 *                      percentage of WMax instead of reading the Smart Meter.
 *                      Must be ≥ 1 when provided. Ignored when {@code enable} is false.
 * @param rampSeconds   optional ramp time in seconds for a smooth power transition
 *                      (0 or null = immediate change).
 * @param revertSeconds optional auto-revert timeout in seconds; the device reverts
 *                      to its previous state after this time (0 or null = no revert).
 *                      Useful as a safety net in automated control scenarios.
 */
@Schema(description = "Request to set the inverter power output limit (SunSpec Model 123)")
public record PowerLimitRequest(
  @Schema(description = "Deprecated – ignored. Server computes limit automatically.",
    nullable = true)
  Integer limitPercent,

  @Schema(description = "true = activate limit, false = deactivate limit and restore normal operation",
    required = true)
  boolean enable,

  @Schema(
    description = "Fixed watt cap in Watts (≥ 1). When provided the server applies "
      + "limitWatts/WMax×100 as the limit without reading the Smart Meter. "
      + "When absent the server uses the Smart Meter for closed-loop zero-export control.",
    nullable = true,
    example = "50"
  )
  Integer limitWatts,

  @Schema(description = "Ramp time in seconds for smooth power transition (null or 0 = immediate)",
    nullable = true)
  Integer rampSeconds,

  @Schema(description = "Auto-revert timeout in seconds (null or 0 = no auto-revert)",
    nullable = true)
  Integer revertSeconds
) {
}
