package at.or.reder.frodo.solarapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * Power flow realtime data from Fronius Solar API.
 *
 * <p>Response data from {@code GET /solar_api/v1/GetPowerFlowRealtimeData.fcgi}.</p>
 *
 * <p>This endpoint provides a unified view of all devices in the PV system:</p>
 * <ul>
 *   <li>Inverters - PV production and battery status</li>
 *   <li>Site - Aggregated metrics (grid, load, PV, battery)</li>
 *   <li>Smartloads - Ohmpilot devices</li>
 *   <li>SecondaryMeters - Additional meters</li>
 * </ul>
 *
 * <p><b>API Reference:</b> {@code refdoc/solar_api.pdf}</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PowerFlowRealtimeData(
  @JsonProperty("Inverters") Map<String, InverterData> inverters,
  @JsonProperty("Site") SiteData site,
  @JsonProperty("Smartloads") SmartloadsData smartloads,
  @JsonProperty("SecondaryMeters") Map<String, Object> secondaryMeters,
  @JsonProperty("Version") String version
) {

  /**
   * Inverter data from Solar API.
   *
   * <p>Map key is the DeviceId (e.g. "1").</p>
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InverterData(
    @JsonProperty("Battery_Mode") String batteryMode,
    @JsonProperty("DT") Integer deviceType,
    @JsonProperty("E_Total") Double energyTotal,
    @JsonProperty("P") Double power,
    @JsonProperty("SOC") Double stateOfCharge
  ) {

    /**
     * Gets the current power output in watts.
     *
     * @return power in W, or null if not available
     */
    public Double getPowerWatts() {
      return power;
    }

    /**
     * Gets the total energy produced in watt-hours.
     *
     * @return total energy in Wh, or null if not available
     */
    public Double getEnergyWattHours() {
      return energyTotal;
    }

    /**
     * Gets the battery state of charge percentage.
     *
     * @return SOC in % (0-100), or null if no battery
     */
    public Double getBatterySOC() {
      return stateOfCharge;
    }

    /**
     * Gets the battery operating mode.
     *
     * @return mode string (e.g. "normal", "nearly depleted", "charging")
     */
    public String getBatteryMode() {
      return batteryMode;
    }
  }

  /**
   * Site-level aggregated data.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SiteData(
    @JsonProperty("BackupMode") Boolean backupMode,
    @JsonProperty("BatteryStandby") Boolean batteryStandby,
    @JsonProperty("Meter_Location") String meterLocation,
    @JsonProperty("Mode") String mode,
    @JsonProperty("P_Akku") Double powerBattery,
    @JsonProperty("P_Grid") Double powerGrid,
    @JsonProperty("P_Load") Double powerLoad,
    @JsonProperty("P_PV") Double powerPV,
    @JsonProperty("rel_Autonomy") Double relativeAutonomy,
    @JsonProperty("rel_SelfConsumption") Double relativeSelfConsumption
  ) {

    /**
     * Gets the current grid power in watts (positive = import, negative = export).
     *
     * @return grid power in W
     */
    public Double getGridPowerWatts() {
      return powerGrid;
    }

    /**
     * Gets the current load consumption in watts.
     *
     * @return load power in W (typically negative)
     */
    public Double getLoadPowerWatts() {
      return powerLoad;
    }

    /**
     * Gets the current PV production in watts.
     *
     * @return PV power in W
     */
    public Double getPVPowerWatts() {
      return powerPV;
    }

    /**
     * Gets the current battery power in watts (positive = charging, negative = discharging).
     *
     * @return battery power in W
     */
    public Double getBatteryPowerWatts() {
      return powerBattery;
    }

    /**
     * Gets the relative autonomy percentage.
     *
     * @return autonomy in % (0-100)
     */
    public Double getAutonomyPercent() {
      return relativeAutonomy;
    }

    /**
     * Gets the self-consumption percentage.
     *
     * @return self-consumption in % (0-100)
     */
    public Double getSelfConsumptionPercent() {
      return relativeSelfConsumption;
    }
  }

  /**
   * Gets all inverters in the system.
   *
   * @return map of DeviceId to inverter data, never null
   */
  public Map<String, InverterData> getInverters() {
    return inverters != null ? inverters : Collections.emptyMap();
  }

  /**
   * Gets the site-level aggregated data.
   *
   * @return site data, or null if not available
   */
  public SiteData getSite() {
    return site;
  }

  /**
   * Gets the smartloads (Ohmpilot devices).
   *
   * @return smartloads data, or null if not available
   */
  public SmartloadsData getSmartloads() {
    return smartloads;
  }

  /**
   * Gets the API version.
   *
   * @return version string (e.g. "13")
   */
  public String getVersion() {
    return version;
  }

  /**
   * Checks if any Ohmpilot devices are discovered.
   *
   * @return true if smartloads contains at least one Ohmpilot
   */
  public boolean hasOhmpilots() {
    return smartloads != null && smartloads.hasDevices();
  }
}
