package at.or.reder.frodo.api.dto;

import java.time.Instant;

/**
 * Summary information for a single Modbus device.
 *
 * @param id                  device ID
 * @param name                device name
 * @param host                Modbus TCP host
 * @param port                Modbus TCP port
 * @param unitId              Modbus unit ID
 * @param enabled             whether device is enabled
 * @param connectionStatus    connection status (UNKNOWN, CONNECTED, FAILED)
 * @param lastSuccessfulRead  timestamp of last successful read (null if never)
 * @param hasDeviceInfo       whether device identification is cached
 */
public record DeviceSummary(
  Long id,
  String name,
  String host,
  int port,
  int unitId,
  boolean enabled,
  ConnectionStatus connectionStatus,
  Instant lastSuccessfulRead,
  boolean hasDeviceInfo
) {
}
