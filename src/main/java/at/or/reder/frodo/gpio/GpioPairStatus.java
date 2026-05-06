package at.or.reder.frodo.gpio;

/**
 * Status snapshot for a single GPIO pair.
 *
 * <p>Used in REST responses and health checks to report the current state
 * of a configured GPIO pair.</p>
 *
 * @param name                pair name as defined in application.properties
 * @param available           this pair is initialised and its lines are open
 * @param outputPin           BCM pin number of the output line
 * @param outputPinState      current output pin level ({@code null} if unavailable)
 * @param outputManualOverride {@code true} when a manual test override is active
 * @param inputPin            BCM pin number of the input line
 * @param inputBias           configured input bias ("PULL_UP", "PULL_DOWN", or "DISABLE")
 * @param inputPinState       current input pin level ({@code null} if unavailable)
 * @param externalModeActive  derived: input pin is at its active level
 * @param assignedDeviceId    device this pair is currently assigned to ({@code null} = unassigned)
 * @param errorMessage        non-null when {@code available=false}
 */
public record GpioPairStatus(
  String name,
  boolean available,
  int outputPin,
  Boolean outputPinState,
  boolean outputManualOverride,
  int inputPin,
  String inputBias,
  Boolean inputPinState,
  boolean externalModeActive,
  Long assignedDeviceId,
  String errorMessage
) {}
