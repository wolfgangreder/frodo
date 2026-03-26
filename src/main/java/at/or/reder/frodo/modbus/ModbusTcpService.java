package at.or.reder.frodo.modbus;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.net.NetClient;
import io.vertx.mutiny.core.net.NetSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service for accessing Modbus devices over TCP using raw Vert.x TCP sockets.
 * Supports Modbus TCP protocol (MBAP header + PDU).
 */
@ApplicationScoped
public class ModbusTcpService {

    private static final Logger LOG = Logger.getLogger(ModbusTcpService.class);

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "frodo.modbus.host", defaultValue = "localhost")
    String modbusHost;

    @ConfigProperty(name = "frodo.modbus.port", defaultValue = "502")
    int modbusPort;

    @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
    boolean modbusEnabled;

    /**
     * Reads holding registers from a Modbus TCP device.
     *
     * @param unitId    Modbus unit/device ID (1-247)
     * @param startAddr starting register address (0-based)
     * @param count     number of registers to read
     * @return Uni resolving to an array of register values
     */
    public io.smallrye.mutiny.Uni<int[]> readHoldingRegisters(int unitId, int startAddr, int count) {
        if (!modbusEnabled) {
            LOG.debug("Modbus is disabled, returning empty response");
            return io.smallrye.mutiny.Uni.createFrom().item(new int[0]);
        }

        NetClient client = vertx.createNetClient();

        return client.connect(modbusPort, modbusHost)
                .onItem().transformToUni(socket -> sendReadHoldingRegistersRequest(socket, unitId, startAddr, count))
                .onFailure().invoke(e -> LOG.errorf(e, "Failed to connect to Modbus device at %s:%d", modbusHost, modbusPort));
    }

    private io.smallrye.mutiny.Uni<int[]> sendReadHoldingRegistersRequest(
            NetSocket socket, int unitId, int startAddr, int count) {

        // Build Modbus TCP request (MBAP header + function code 0x03)
        byte[] request = buildReadHoldingRegistersRequest(unitId, startAddr, count, 1);

        return io.smallrye.mutiny.Uni.createFrom().<int[]>emitter(emitter -> {
            socket.handler(buffer -> {
                try {
                    int[] registers = parseReadHoldingRegistersResponse(buffer.getBytes(), count);
                    emitter.complete(registers);
                } catch (Exception e) {
                    emitter.fail(e);
                } finally {
                    socket.closeAndForget();
                }
            });
            socket.exceptionHandler(e -> {
                emitter.fail(e);
                socket.closeAndForget();
            });
            socket.write(io.vertx.mutiny.core.buffer.Buffer.buffer(request)).subscribe().with(
                    v -> LOG.debugf("Modbus request sent: unitId=%d, startAddr=%d, count=%d", unitId, startAddr, count),
                    emitter::fail
            );
        });
    }

    /**
     * Builds a Modbus TCP Read Holding Registers (FC=03) request frame.
     */
    static byte[] buildReadHoldingRegistersRequest(int unitId, int startAddr, int count, int transactionId) {
        byte[] pdu = {
                0x03,                       // Function code: Read Holding Registers
                (byte) (startAddr >> 8),    // Starting address high byte
                (byte) (startAddr & 0xFF),  // Starting address low byte
                (byte) (count >> 8),        // Quantity high byte
                (byte) (count & 0xFF)       // Quantity low byte
        };
        return buildMbapFrame(transactionId, unitId, pdu);
    }

    /**
     * Wraps a PDU in a Modbus TCP MBAP header.
     * Frame structure: Transaction ID (2) + Protocol ID (2) + Length (2) + Unit ID (1) + PDU (n)
     */
    static byte[] buildMbapFrame(int transactionId, int unitId, byte[] pdu) {
        byte[] frame = new byte[7 + pdu.length]; // 6 (MBAP) + 1 (unitId) + PDU
        frame[0] = (byte) (transactionId >> 8);   // Transaction ID high
        frame[1] = (byte) (transactionId & 0xFF); // Transaction ID low
        frame[2] = 0x00;                           // Protocol ID high (always 0)
        frame[3] = 0x00;                           // Protocol ID low (always 0)
        int length = 1 + pdu.length;               // Unit ID byte + PDU length
        frame[4] = (byte) (length >> 8);           // Length high
        frame[5] = (byte) (length & 0xFF);         // Length low
        frame[6] = (byte) unitId;                  // Unit identifier
        System.arraycopy(pdu, 0, frame, 7, pdu.length);
        return frame;
    }

    /**
     * Parses a Modbus TCP Read Holding Registers response.
     */
    static int[] parseReadHoldingRegistersResponse(byte[] response, int expectedCount) {
        if (response.length < 9) {
            throw new IllegalArgumentException("Response too short: " + response.length);
        }
        int byteCount = response[8] & 0xFF;
        if (response.length < 9 + byteCount) {
            throw new IllegalArgumentException("Incomplete response");
        }
        int regCount = byteCount / 2;
        int[] registers = new int[regCount];
        for (int i = 0; i < regCount; i++) {
            registers[i] = ((response[9 + i * 2] & 0xFF) << 8) | (response[10 + i * 2] & 0xFF);
        }
        return registers;
    }
}
