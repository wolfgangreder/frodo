package at.or.reder.frodo.modbus;

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

import java.util.List;

/**
 * REST endpoint for reading Modbus device registers via TCP.
 */
@Path("/api/modbus")
@Tag(name = "Modbus", description = "Modbus TCP device access endpoints")
public class ModbusResource {

    @Inject
    ModbusTcpService modbusTcpService;

    @GET
    @Path("/{unitId}/holding-registers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Read holding registers",
            description = "Reads holding registers from a Modbus TCP device using function code 03"
    )
    public io.smallrye.mutiny.Uni<ModbusRegisterResponse> readHoldingRegisters(
            @Parameter(description = "Modbus unit ID (1-247)") @PathParam("unitId") int unitId,
            @Parameter(description = "Starting register address (0-based)") @QueryParam("start") int startAddr,
            @Parameter(description = "Number of registers to read") @QueryParam("count") int count) {

        return modbusTcpService.readHoldingRegisters(unitId, startAddr, count)
                .onItem().transform(registers -> {
                    List<Integer> values = new java.util.ArrayList<>(registers.length);
                    for (int reg : registers) {
                        values.add(reg);
                    }
                    return new ModbusRegisterResponse(unitId, startAddr, values);
                });
    }

    public record ModbusRegisterResponse(int unitId, int startAddress, List<Integer> registers) {
    }
}
