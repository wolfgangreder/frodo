package at.or.reder.frodo.solarapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ohmpilot device data from Fronius Solar API.
 *
 * <p>Data structure for a single Ohmpilot device from the
 * {@code GetPowerFlowRealtimeData.fcgi} endpoint.</p>
 *
 * <p><b>Example JSON:</b></p>
 * <pre>
 * {
 *   "P_AC_Total": 0.0,
 *   "State": "normal",
 *   "Temperature": 52.9
 * }
 * </pre>
 *
 * <p><b>States:</b></p>
 * <ul>
 *   <li>{@code normal} - Normal operation</li>
 *   <li>{@code boost} - Boost mode active</li>
 *   <li>{@code fault} - Fault condition</li>
 *   <li>{@code startup} - Device starting up</li>
 *   <li>{@code standby} - Standby mode</li>
 * </ul>
 */
public record OhmpilotData(
  @JsonProperty("P_AC_Total") Double powerTotal,
  @JsonProperty("State") String state,
  @JsonProperty("Temperature") Double temperature
) {

  /**
   * Gets the power consumption in watts.
   *
   * @return power in W, or null if not available
   */
  public Double getPowerWatts() {
    return powerTotal;
  }

  /**
   * Gets the operating state.
   *
   * @return state string (normal, boost, fault, startup, standby)
   */
  public String getState() {
    return state;
  }

  /**
   * Gets the storage/tank temperature in degrees Celsius.
   *
   * @return temperature in °C, or null if not available
   */
  public Double getTemperatureCelsius() {
    return temperature;
  }

  /**
   * Checks if the device is in normal operating state.
   *
   * @return true if state is "normal"
   */
  public boolean isNormal() {
    return "normal".equalsIgnoreCase(state);
  }

  /**
   * Checks if the device is in fault state.
   *
   * @return true if state is "fault"
   */
  public boolean isFault() {
    return "fault".equalsIgnoreCase(state);
  }

  /**
   * Checks if the device is actively consuming power.
   *
   * @return true if power is greater than 0
   */
  public boolean isActive() {
    return powerTotal != null && powerTotal > 0.0;
  }
}
