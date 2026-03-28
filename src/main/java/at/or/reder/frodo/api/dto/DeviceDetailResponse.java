package at.or.reder.frodo.api.dto;

import java.time.Instant;

/**
 * Detailed information for a single Modbus device including device identification.
 *
 * @param id                  device ID
 * @param name                device name
 * @param host                Modbus TCP host
 * @param port                Modbus TCP port
 * @param unitId              Modbus unit ID
 * @param enabled             whether device is enabled
 * @param description         device description (optional)
 * @param identification      device identification data (null if not available)
 * @param lastUpdated         timestamp when identification was last updated
 * @param cached              whether identification data is from cache
 * @param connectionStatus    connection status (UNKNOWN, CONNECTED, FAILED)
 */
public record DeviceDetailResponse(
  Long id,
  String name,
  String host,
  int port,
  int unitId,
  boolean enabled,
  String description,
  DeviceIdentificationDto identification,
  Instant lastUpdated,
  boolean cached,
  ConnectionStatus connectionStatus
) {
}
