package at.or.reder.frodo.solarapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * Smartloads data container from Fronius Solar API.
 *
 * <p>Contains data for Ohmpilot and OhmpilotEco devices from the
 * {@code GetPowerFlowRealtimeData.fcgi} endpoint.</p>
 *
 * <p><b>Example JSON:</b></p>
 * <pre>
 * {
 *   "Ohmpilots": {
 *     "0": {
 *       "P_AC_Total": 0.0,
 *       "State": "normal",
 *       "Temperature": 52.9
 *     }
 *   },
 *   "OhmpilotEcos": {}
 * }
 * </pre>
 */
public record SmartloadsData(
  @JsonProperty("Ohmpilots") Map<String, OhmpilotData> ohmpilots,
  @JsonProperty("OhmpilotEcos") Map<String, OhmpilotData> ohmpilotEcos
) {

  /**
   * Gets all Ohmpilot devices (standard version).
   *
   * <p>Map key is the ComponentId (e.g. "0", "1").</p>
   *
   * @return map of ComponentId to Ohmpilot data, never null
   */
  public Map<String, OhmpilotData> getOhmpilots() {
    return ohmpilots != null ? ohmpilots : Collections.emptyMap();
  }

  /**
   * Gets all OhmpilotEco devices (dual heating rod version).
   *
   * <p>Map key is the ComponentId (e.g. "0", "1").</p>
   *
   * @return map of ComponentId to OhmpilotEco data, never null
   */
  public Map<String, OhmpilotData> getOhmpilotEcos() {
    return ohmpilotEcos != null ? ohmpilotEcos : Collections.emptyMap();
  }

  /**
   * Counts the total number of discovered Ohmpilot devices (all types).
   *
   * @return total count of Ohmpilots + OhmpilotEcos
   */
  public int getTotalDeviceCount() {
    return getOhmpilots().size() + getOhmpilotEcos().size();
  }

  /**
   * Checks if any Ohmpilot devices are present.
   *
   * @return true if at least one Ohmpilot or OhmpilotEco is discovered
   */
  public boolean hasDevices() {
    return getTotalDeviceCount() > 0;
  }
}
