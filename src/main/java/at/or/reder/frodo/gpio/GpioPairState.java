package at.or.reder.frodo.gpio;

/**
 * Runtime state for one initialised GPIO pair.
 *
 * <p>Created during startup when both output and input lines are successfully
 * opened. Immutable after construction; concurrent reads are safe.</p>
 *
 * @param name             pair name from configuration
 * @param outputPin        BCM pin number of the output line
 * @param outputBlockLevel pin level when export is blocked ("HIGH" or "LOW")
 * @param inputPin         BCM pin number of the input line
 * @param inputActiveLevel pin level when external mode is active ("HIGH" or "LOW")
 * @param outputLineFd     kernel file descriptor for the output line
 * @param inputLineFd      kernel file descriptor for the input line
 */
record GpioPairState(
  String name,
  int outputPin,
  String outputBlockLevel,
  int inputPin,
  String inputActiveLevel,
  int outputLineFd,
  int inputLineFd
) {}
