package at.or.reder.frodo.modbus.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents the identification data retrieved from a Modbus device
 * via Read Device Identification (FC 0x2B/MEI 0x0E).
 *
 * <p>The three mandatory fields ({@code vendorName}, {@code productCode},
 * {@code majorMinorRevision}) are always present for a successful Basic
 * identification request. Optional fields may be {@code null} depending
 * on the device's conformity level and the requested read code.</p>
 *
 * @param vendorName           vendor name (Object ID 0x00, mandatory)
 * @param productCode          product code (Object ID 0x01, mandatory)
 * @param majorMinorRevision   firmware revision (Object ID 0x02, mandatory)
 * @param vendorUrl            vendor URL (Object ID 0x03, optional)
 * @param productName          product name (Object ID 0x04, optional)
 * @param modelName            model name (Object ID 0x05, optional)
 * @param userApplicationName  user application name (Object ID 0x06, optional)
 * @param additionalObjects    additional/vendor-specific objects keyed by object ID
 * @param readTime             timestamp when the identification was read
 */
public record DeviceIdentification(
  String vendorName,
  String productCode,
  String majorMinorRevision,
  String vendorUrl,
  String productName,
  String modelName,
  String userApplicationName,
  Map<Integer, String> additionalObjects,
  Instant readTime
) {

  /**
   * Creates a DeviceIdentification with only the mandatory Basic fields.
   *
   * @param vendorName         vendor name
   * @param productCode        product code
   * @param majorMinorRevision firmware revision
   * @param readTime           timestamp when read
   * @return DeviceIdentification with optional fields set to null
   */
  public static DeviceIdentification basic(String vendorName, String productCode,
                                           String majorMinorRevision, Instant readTime) {
    return new DeviceIdentification(vendorName, productCode, majorMinorRevision,
      null, null, null, null, Map.of(), readTime);
  }
}
