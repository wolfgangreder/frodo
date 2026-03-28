package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.model.DeviceIdentification;

import java.time.Instant;

/**
 * Device identification information (Modbus FC 0x2B/0x0E MEI Type 14).
 *
 * @param vendorName          vendor name
 * @param productCode         product code
 * @param revision            major/minor revision
 * @param vendorUrl           vendor URL (optional)
 * @param productName         product name (optional)
 * @param modelName           model name (optional)
 * @param userApplicationName user application name (optional)
 * @param readTime            timestamp when this data was read
 */
public record DeviceIdentificationDto(
  String vendorName,
  String productCode,
  String revision,
  String vendorUrl,
  String productName,
  String modelName,
  String userApplicationName,
  Instant readTime
) {

  /**
   * Creates a DTO from a DeviceIdentification model object.
   *
   * @param deviceId  the device identification model
   * @param readTime  when this data was read
   * @return DTO for API response
   */
  public static DeviceIdentificationDto fromModel(DeviceIdentification deviceId, Instant readTime) {
    return new DeviceIdentificationDto(
      deviceId.vendorName(),
      deviceId.productCode(),
      deviceId.majorMinorRevision(),
      deviceId.vendorUrl(),
      deviceId.productName(),
      deviceId.modelName(),
      deviceId.userApplicationName(),
      readTime
    );
  }
}
