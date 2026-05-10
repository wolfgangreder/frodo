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

package at.or.reder.frodo.modbus;

import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * REST endpoint for reading Modbus device registers via TCP.
 *
 * <p>Looks up the device by ID to obtain the host, port, and unit ID,
 * then routes the request through the connection pool.</p>
 */
@Path("/devices/{deviceId}/modbus")
@Tag(name = "Modbus", description = "Modbus TCP device access endpoints")
public class ModbusResource {

  @Inject
  ModbusTcpService modbusTcpService;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @GET
  @Path("/holding-registers")
  @Produces(MediaType.APPLICATION_JSON)
  @Blocking
  @Operation(
    summary = "Read holding registers",
    description = "Reads holding registers from a Modbus TCP device using function code 03"
  )
  public ModbusRegisterResponse readHoldingRegisters(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId,
    @Parameter(description = "Starting register address (0-based)")
    @QueryParam("start") int startAddr,
    @Parameter(description = "Number of registers to read")
    @QueryParam("count") int count)
    throws IOException, TimeoutException {

    ModbusDeviceEntity device = deviceRepository.findByIdOptional(deviceId)
      .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

    DeviceAddress address = DeviceAddress.fromEntity(device);
    int[] registers = modbusTcpService.readHoldingRegisters(address, startAddr, count);
    List<Integer> values = Arrays.stream(registers).boxed().toList();
    return new ModbusRegisterResponse(device.unitId, startAddr, values);
  }

  public record ModbusRegisterResponse(int unitId, int startAddress, List<Integer> registers) {
  }
}
