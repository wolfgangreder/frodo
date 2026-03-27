package at.or.reder.frodo.modbus;

import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for accessing Modbus devices over TCP using connection pooling.
 * Supports Modbus TCP protocol (MBAP header + PDU).
 */
@ApplicationScoped
public class ModbusTcpService {

    private static final Logger LOG = Logger.getLogger(ModbusTcpService.class);

    @Inject
    ModbusConnectionPool connectionPool;

    @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
    boolean modbusEnabled;

    @ConfigProperty(name = "frodo.modbus.request.max-retries", defaultValue = "3")
    int maxRetries;

    @ConfigProperty(name = "frodo.modbus.request.retry-delay-seconds", defaultValue = "2")
    int retryDelaySeconds;

    private final AtomicInteger transactionIdCounter = new AtomicInteger(1);

    /**
     * Reads holding registers from a Modbus TCP device.
     *
     * @param unitId    Modbus unit/device ID (1-247)
     * @param startAddr starting register address (0-based)
     * @param count     number of registers to read
     * @return Uni resolving to an array of register values
     */
    public Uni<int[]> readHoldingRegisters(int unitId, int startAddr, int count) {
        if (!modbusEnabled) {
            LOG.debug("Modbus is disabled, returning empty response");
            return Uni.createFrom().item(new int[0]);
        }

        int transactionId = getNextTransactionId();
        byte[] request = buildReadHoldingRegistersRequest(unitId, startAddr, count, transactionId);

        return connectionPool.executeRequest(request, transactionId)
                .onItem().transform(response -> parseReadHoldingRegistersResponse(response, count))
                .onFailure().retry()
                    .withBackOff(Duration.ofSeconds(retryDelaySeconds))
                    .atMost(maxRetries)
                .onFailure().invoke(e -> LOG.errorf(e, "Failed to read holding registers after %d retries", maxRetries));
    }

    /**
     * Gets the next transaction ID (thread-safe, wraps at 0xFFFF).
     *
     * @return next transaction ID in range 0-65535
     */
    private int getNextTransactionId() {
        return transactionIdCounter.updateAndGet(x -> (x + 1) & 0xFFFF);
    }

    /**
     * Builds a Modbus TCP Read Holding Registers (FC=03) request frame.
     *
     * @param unitId        Modbus unit/device ID
     * @param startAddr     starting register address
     * @param count         number of registers to read
     * @param transactionId transaction ID for request
     * @return complete Modbus TCP frame (MBAP + PDU)
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
     *
     * @param transactionId transaction ID for request
     * @param unitId        Modbus unit/device ID
     * @param pdu           Protocol Data Unit (function code + data)
     * @return complete Modbus TCP frame with MBAP header
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
     *
     * @param response      complete Modbus TCP response frame
     * @param expectedCount expected number of registers (for validation)
     * @return array of register values
     * @throws IllegalArgumentException if response is malformed or incomplete
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
