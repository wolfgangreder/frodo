package at.or.reder.frodo.gpio;

import java.util.List;

/**
 * GPIO system status snapshot.
 *
 * <p>Wraps per-pair statuses with system-level platform detection
 * and availability information.</p>
 *
 * @param available      GPIO system initialised successfully (at least one pair opened)
 * @param isRaspberryPi5 platform detection result
 * @param platform       platform description (e.g. "Raspberry Pi 5 Model B Rev 1.0")
 * @param errorMessage   system-level error message ({@code null} when available)
 * @param pairs          status for every configured pair
 */
public record GpioStatus(
  boolean available,
  boolean isRaspberryPi5,
  String platform,
  String errorMessage,
  List<GpioPairStatus> pairs
) {}
