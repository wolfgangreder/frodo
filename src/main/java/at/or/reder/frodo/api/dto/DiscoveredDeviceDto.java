package at.or.reder.frodo.api.dto;

import at.or.reder.frodo.modbus.model.DeviceType;
import at.or.reder.frodo.modbus.service.DiscoveredDevice;

import java.util.List;

/**
 * DTO representation of a discovered device.
 *
 * <p>Used in {@link DeviceDiscoveryResponse} to convey discovery results
 * to the REST API client.</p>
 *
 * @param host         hostname or IP address of the Modbus TCP server
 * @param port         TCP port number
 * @param unitId       Modbus unit/slave ID (-1 if unknown)
 * @param deviceType   detected device type
 * @param manufacturer manufacturer name (null if not available)
 * @param model        product model name (null if not available)
 * @param serialNumber serial number (null if not available)
 * @param version      firmware version (null if not available)
 * @param modelIds     SunSpec model IDs found on the device (empty for non-SunSpec)
 * @param source       how the device was discovered ("sunspec", "modbus-fc2b", "solar-api")
 * @param suggestedName suggested device name based on discovery info
 * @param hasSunSpec   whether the device has SunSpec model support
 */
public record DiscoveredDeviceDto(
  String host,
  int port,
  int unitId,
  DeviceType deviceType,
  String manufacturer,
  String model,
  String serialNumber,
  String version,
  List<Integer> modelIds,
  String source,
  String suggestedName,
  boolean hasSunSpec
) {

  /**
   * Creates a DTO from a {@link DiscoveredDevice} domain model.
   *
   * @param device the discovered device
   * @return the DTO
   */
  public static DiscoveredDeviceDto fromModel(DiscoveredDevice device) {
    return new DiscoveredDeviceDto(
      device.host(),
      device.port(),
      device.unitId(),
      device.deviceType(),
      device.manufacturer(),
      device.model(),
      device.serialNumber(),
      device.version(),
      device.modelIds(),
      device.source(),
      device.suggestedName(),
      device.hasSunSpec()
    );
  }

  /**
   * Creates a list of DTOs from a list of discovered devices.
   *
   * @param devices the discovered devices
   * @return list of DTOs
   */
  public static List<DiscoveredDeviceDto> fromModelList(List<DiscoveredDevice> devices) {
    return devices.stream()
      .map(DiscoveredDeviceDto::fromModel)
      .toList();
  }
}
