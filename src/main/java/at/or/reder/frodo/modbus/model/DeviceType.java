package at.or.reder.frodo.modbus.model;

/**
 * Types of devices that can be discovered on a Modbus TCP connection.
 *
 * <p>In a Fronius gateway architecture, the inverter acts as a Modbus TCP
 * gateway for multiple RTU devices, each with a unique Unit ID. This enum
 * classifies the device types that Frodo can discover and manage.</p>
 *
 * <p>Device type is determined during discovery by inspecting the SunSpec
 * model chain (for SunSpec-compatible devices) or via Solar API device
 * class information (for non-SunSpec devices like Ohmpilot).</p>
 */
public enum DeviceType {

  /**
   * Solar inverter (SunSpec models 101-103, 111-113).
   * Typically at Unit ID 1.
   */
  INVERTER("Inverter", "Solar inverter"),

  /**
   * Battery storage system (SunSpec model 124).
   */
  STORAGE("Storage", "Battery storage system"),

  /**
   * Energy meter (SunSpec models 201-204, 211-214).
   * Typically at Unit IDs 200-203.
   */
  SMART_METER("Smart Meter", "Energy meter"),

  /**
   * Ohmpilot excess energy controller (Fronius smartload device class).
   * No standard SunSpec model; data available via Solar API.
   */
  OHMPILOT("Ohmpilot", "Smartload excess energy controller"),

  /**
   * Unknown or unrecognized device type.
   */
  UNKNOWN("Unknown", "Unknown device type");

  private final String displayName;
  private final String description;

  DeviceType(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Returns a human-readable display name for this device type.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns a description of this device type.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks whether this device type has SunSpec model support.
   *
   * @return true if the device type uses standard SunSpec models
   */
  public boolean hasSunSpecSupport() {
    return this == INVERTER || this == STORAGE || this == SMART_METER;
  }
}
