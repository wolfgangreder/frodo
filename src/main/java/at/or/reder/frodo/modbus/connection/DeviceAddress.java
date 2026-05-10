/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.modbus.connection;

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;

/**
 * Identifies a Modbus TCP device by its network address and unit ID.
 *
 * <p>Used throughout the service layer to route requests to the correct
 * physical device. The connection pool uses {@link #connectionKey()}
 * (host:port) to share TCP connections between multiple unit IDs on
 * the same gateway.</p>
 *
 * @param host   hostname or IP address of the Modbus TCP server
 * @param port   TCP port number (typically 502)
 * @param unitId Modbus unit/slave ID (1-247)
 */
public record DeviceAddress(
  String host,
  int port,
  int unitId
) {

  /**
   * Returns the connection key used by the connection pool.
   *
   * <p>Multiple unit IDs on the same host:port share a single TCP
   * connection, since the unit ID is encoded in the MBAP frame header.</p>
   *
   * @return connection key in format "host:port"
   */
  public String connectionKey() {
    return host + ":" + port;
  }

  /**
   * Creates a DeviceAddress from a {@link ModbusDeviceEntity}.
   *
   * @param entity the device entity
   * @return a DeviceAddress with the entity's host, port, and unitId
   */
  public static DeviceAddress fromEntity(ModbusDeviceEntity entity) {
    return new DeviceAddress(entity.host, entity.port, entity.unitId);
  }

  @Override
  public String toString() {
    return host + ":" + port + "/" + unitId;
  }
}
