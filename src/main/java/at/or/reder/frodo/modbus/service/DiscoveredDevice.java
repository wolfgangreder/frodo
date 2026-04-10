package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.model.DeviceType;

import java.util.List;

/**
 * Result of discovering a device on a Modbus TCP connection or via Solar API.
 *
 * <p>Encapsulates all information gathered during discovery: the connection
 * address, device type, identification metadata, and SunSpec model IDs.
 * Used by {@link DeviceDiscoveryService} to report discovery results before
 * persisting them to the database.</p>
 *
 * @param host         hostname or IP address of the Modbus TCP server
 * @param port         TCP port number
 * @param unitId       Modbus unit/slave ID (-1 if unknown, e.g. Solar API Ohmpilot)
 * @param deviceType   detected device type
 * @param manufacturer manufacturer name from SunSpec Common model or FC 0x2B
 * @param model        product model from SunSpec Common model or FC 0x2B
 * @param serialNumber serial number from SunSpec Common model or FC 0x2B
 * @param version      firmware version from SunSpec Common model or FC 0x2B
 * @param modelIds     list of SunSpec model IDs found on the device (empty for non-SunSpec)
 * @param source       how the device was discovered ("sunspec", "modbus-fc2b", "solar-api")
 */
public record DiscoveredDevice(
  String host,
  int port,
  int unitId,
  DeviceType deviceType,
  String manufacturer,
  String model,
  String serialNumber,
  String version,
  List<Integer> modelIds,
  String source
) {

  /** Discovery source: SunSpec model chain. */
  public static final String SOURCE_SUNSPEC = "sunspec";

  /** Discovery source: Modbus FC 0x2B device identification. */
  public static final String SOURCE_MODBUS_FC2B = "modbus-fc2b";

  /** Discovery source: Fronius Solar API. */
  public static final String SOURCE_SOLAR_API = "solar-api";

  /**
   * Checks whether this device has a known Modbus unit ID.
   *
   * @return true if unitId is valid (1-247)
   */
  public boolean hasUnitId() {
    return unitId >= 1 && unitId <= 247;
  }

  /**
   * Checks whether this device has SunSpec model support.
   *
   * @return true if SunSpec models were discovered
   */
  public boolean hasSunSpec() {
    return modelIds != null && !modelIds.isEmpty();
  }

  /**
   * Returns a human-readable connection string.
   *
   * @return connection string (e.g. "192.168.1.160:502/1" or "192.168.1.160:80/solar-api")
   */
  public String connectionString() {
    if (hasUnitId()) {
      return String.format("%s:%d/%d", host, port, unitId);
    }
    return String.format("%s:%d/%s", host, port, source);
  }

  /**
   * Creates a default device name from the discovered information.
   *
   * @return a suggested device name
   */
  public String suggestedName() {
    String typeName = deviceType != null ? deviceType.getDisplayName() : "Device";
    if (manufacturer != null && model != null) {
      return String.format("%s %s", manufacturer, model);
    }
    if (hasUnitId()) {
      return String.format("%s (Unit %d)", typeName, unitId);
    }
    return typeName;
  }
}
