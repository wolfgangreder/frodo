package at.or.reder.frodo.api.dto;

import java.util.List;

/**
 * Response DTO for device discovery results.
 *
 * @param host           hostname or IP that was scanned
 * @param port           port that was scanned
 * @param devicesFound   total number of devices discovered
 * @param devices        list of discovered devices
 * @param savedDeviceIds IDs of saved devices (empty if autoSave was false)
 */
public record DeviceDiscoveryResponse(
  String host,
  int port,
  int devicesFound,
  List<DiscoveredDeviceDto> devices,
  List<Long> savedDeviceIds
) {

  /**
   * Creates a response without saved device IDs (discovery only, no auto-save).
   *
   * @param host    scanned host
   * @param port    scanned port
   * @param devices discovered devices
   * @return response without saved IDs
   */
  public static DeviceDiscoveryResponse discoveryOnly(String host, int port,
                                                       List<DiscoveredDeviceDto> devices) {
    return new DeviceDiscoveryResponse(host, port, devices.size(), devices, List.of());
  }

  /**
   * Creates a response with saved device IDs (auto-save enabled).
   *
   * @param host           scanned host
   * @param port           scanned port
   * @param devices        discovered devices
   * @param savedDeviceIds IDs of saved/updated device entities
   * @return response with saved IDs
   */
  public static DeviceDiscoveryResponse withSavedDevices(String host, int port,
                                                          List<DiscoveredDeviceDto> devices,
                                                          List<Long> savedDeviceIds) {
    return new DeviceDiscoveryResponse(host, port, devices.size(), devices, savedDeviceIds);
  }
}
