package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.ModbusObjectId;
import at.or.reder.frodo.modbus.model.ReadDeviceIdCode;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.net.NetClient;
import io.vertx.mutiny.core.net.NetSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for testing connections to arbitrary Modbus devices.
 * Unlike the main ModbusTcpService which uses a pooled connection,
 * this service creates temporary one-shot connections for testing purposes.
 *
 * <p>Connection test strategy:</p>
 * <ol>
 *   <li>Open a TCP connection and send FC 0x03 (Read Holding Registers) to read
 *       the SunSpec signature at register 40000. This verifies the device actually
 *       responds with register data - exactly what Frodo needs to operate.</li>
 *   <li>If the SunSpec signature read succeeds, open a second connection and
 *       attempt FC 0x2B (Read Device Identification) to enrich the result with
 *       manufacturer/model info. This is optional - failure here doesn't affect
 *       the overall test result.</li>
 * </ol>
 *
 * <p>Each Modbus request uses its own fresh TCP connection because some devices
 * (e.g. Fronius Gen24) close the connection after sending an exception response.</p>
 */
@ApplicationScoped
public class ConnectionTestService {

  private static final Logger LOG = Logger.getLogger(ConnectionTestService.class);

  /** Function code for Read Holding Registers. */
  private static final int FC_READ_HOLDING_REGISTERS = 0x03;

  /** Function code for Read Device Identification (Encapsulated Interface Transport). */
  private static final int FC_READ_DEVICE_IDENTIFICATION = 0x2B;

  /** MEI type for Read Device Identification. */
  private static final int MEI_TYPE_READ_DEVICE_ID = 0x0E;

  /** Exception response flag: high bit set on function code. */
  private static final int EXCEPTION_RESPONSE_FLAG = 0x80;

  /** SunSpec base register address (40001 in Modbus addressing = 40000 in protocol). */
  private static final int SUNSPEC_BASE_ADDRESS = 40000;

  /** SunSpec "SunS" signature: 0x53756e53. */
  private static final String SUNSPEC_SIGNATURE = "SunS";

  @Inject
  Vertx vertx;

  @ConfigProperty(name = "frodo.modbus.connection.test-timeout-seconds", defaultValue = "10")
  int testTimeoutSeconds;

  private final AtomicInteger transactionIdCounter = new AtomicInteger(1);

  /**
   * Tests connection to a Modbus device by reading the SunSpec signature,
   * then optionally enriches the result with device identification.
   *
   * @param host   hostname or IP address
   * @param port   Modbus TCP port
   * @param unitId Modbus unit ID
   * @return Uni with test result
   */
  public Uni<TestResult> testConnection(String host, int port, int unitId) {
    LOG.infof("Testing connection to %s:%d (unit %d)", host, port, unitId);
    long startTime = System.currentTimeMillis();

    // Step 1: Read SunSpec signature via FC 0x03 (primary test)
    return readSunSpecSignature(host, port, unitId)
      .onItem().transformToUni(signature -> {
        long elapsed = System.currentTimeMillis() - startTime;
        boolean isSunSpec = SUNSPEC_SIGNATURE.equals(signature);
        LOG.infof("SunSpec signature read: '%s' (isSunSpec=%b, %dms)", signature, isSunSpec, elapsed);

        // Step 2: Try to get device identification on a separate connection (optional enrichment)
        return readDeviceIdentification(host, port, unitId)
          .onItem().transform(identification -> {
            String method = isSunSpec ? "SunSpec" : "Modbus";
            return new TestResult(true, System.currentTimeMillis() - startTime,
              identification, null, method);
          })
          .onFailure().recoverWithItem(ex -> {
            LOG.debugf("Device identification enrichment failed (non-critical): %s", ex.getMessage());
            String method = isSunSpec ? "SunSpec" : "Modbus";
            return new TestResult(true, elapsed, null, null, method);
          });
      })
      .onFailure().recoverWithItem(ex -> {
        long elapsed = System.currentTimeMillis() - startTime;
        LOG.warnf("Connection test failed for %s:%d (unit %d): %s", host, port, unitId, ex.getMessage());
        return new TestResult(false, elapsed, null, ex.getMessage(), null);
      });
  }

  /**
   * Opens a fresh TCP connection and reads the SunSpec signature at register 40000.
   *
   * @return Uni containing the 4-character ASCII string read from registers 40000-40001
   */
  private Uni<String> readSunSpecSignature(String host, int port, int unitId) {
    int transactionId = transactionIdCounter.getAndIncrement() & 0xFFFF;
    byte[] request = buildReadHoldingRegistersRequest(unitId, SUNSPEC_BASE_ADDRESS, 2, transactionId);
    NetClient netClient = vertx.createNetClient();

    return netClient.connect(port, host)
      .ifNoItem().after(Duration.ofSeconds(testTimeoutSeconds))
      .failWith(() -> new RuntimeException("TCP connection timeout"))
      .onItem().transformToUni(socket ->
        sendAndReceive(socket, request, "SunSpec signature", transactionId, unitId)
          .onItem().transform(response -> parseSunSpecSignatureResponse(response))
          .onTermination().invoke(() -> {
            socket.closeAndForget();
            netClient.close();
          })
      )
      .onFailure().invoke(ex -> netClient.close());
  }

  /**
   * Opens a fresh TCP connection and reads device identification via FC 0x2B.
   *
   * @return Uni containing device identification, or failure if not supported
   */
  private Uni<DeviceIdentification> readDeviceIdentification(String host, int port, int unitId) {
    int transactionId = transactionIdCounter.getAndIncrement() & 0xFFFF;
    byte[] request = buildReadDeviceIdRequest(unitId, ReadDeviceIdCode.BASIC, transactionId, (byte) 0);
    NetClient netClient = vertx.createNetClient();

    return netClient.connect(port, host)
      .ifNoItem().after(Duration.ofSeconds(testTimeoutSeconds))
      .failWith(() -> new RuntimeException("TCP connection timeout"))
      .onItem().transformToUni(socket ->
        sendAndReceive(socket, request, "Device ID", transactionId, unitId)
          .onItem().transform(response -> parseDeviceIdentificationResponse(response))
          .onTermination().invoke(() -> {
            socket.closeAndForget();
            netClient.close();
          })
      )
      .onFailure().invoke(ex -> netClient.close());
  }

  /**
   * Sends a Modbus request and waits for a complete MBAP response frame.
   * This is the shared send/receive logic for all Modbus function codes.
   *
   * @param socket        connected socket
   * @param request       raw MBAP frame to send
   * @param label         descriptive label for logging
   * @param transactionId transaction ID for logging
   * @param unitId        unit ID for logging
   * @return Uni containing the raw response bytes
   */
  private Uni<byte[]> sendAndReceive(NetSocket socket, byte[] request, String label,
                                     int transactionId, int unitId) {
    return Uni.createFrom().<byte[]>emitter(emitter -> {
      Buffer frameBuffer = Buffer.buffer();

      socket.handler(buffer -> {
        frameBuffer.appendBuffer(buffer);

        // Try to parse complete MBAP frame (header is 6 bytes: TxID + Protocol + Length)
        if (frameBuffer.length() >= 6) {
          int length = ((frameBuffer.getByte(4) & 0xFF) << 8) | (frameBuffer.getByte(5) & 0xFF);
          int totalLength = 6 + length;

          if (frameBuffer.length() >= totalLength) {
            byte[] response = new byte[totalLength];
            for (int i = 0; i < totalLength; i++) {
              response[i] = frameBuffer.getByte(i);
            }
            LOG.debugf("%s response received (%d bytes, txId=%d, unitId=%d)",
              label, totalLength, transactionId, unitId);
            emitter.complete(response);
          }
        }
      });

      socket.exceptionHandler(emitter::fail);

      socket.write(Buffer.buffer(request))
        .subscribe().with(
          v -> LOG.debugf("%s request sent (txId=%d, unitId=%d)", label, transactionId, unitId),
          emitter::fail
        );
    })
    .ifNoItem().after(Duration.ofSeconds(testTimeoutSeconds))
    .failWith(() -> new RuntimeException(label + " read timeout"));
  }

  // ── Frame builders ──────────────────────────────────────────────────────

  /**
   * Builds FC 0x03 (Read Holding Registers) request frame.
   */
  private byte[] buildReadHoldingRegistersRequest(int unitId, int startAddr, int numRegisters, int transactionId) {
    byte[] frame = new byte[12]; // MBAP(7) + PDU(5)
    frame[0] = (byte) ((transactionId >> 8) & 0xFF);
    frame[1] = (byte) (transactionId & 0xFF);
    frame[2] = 0; // Protocol ID high
    frame[3] = 0; // Protocol ID low
    frame[4] = 0; // Length high
    frame[5] = 6; // Length low (UnitID + PDU = 1 + 5)
    frame[6] = (byte) unitId;
    frame[7] = (byte) FC_READ_HOLDING_REGISTERS;
    frame[8] = (byte) ((startAddr >> 8) & 0xFF);
    frame[9] = (byte) (startAddr & 0xFF);
    frame[10] = (byte) ((numRegisters >> 8) & 0xFF);
    frame[11] = (byte) (numRegisters & 0xFF);
    return frame;
  }

  /**
   * Builds FC 0x2B/0x0E (Read Device Identification) request frame.
   */
  private byte[] buildReadDeviceIdRequest(int unitId, ReadDeviceIdCode readCode, int transactionId, byte objectId) {
    byte[] frame = new byte[11]; // MBAP(7) + PDU(4)
    frame[0] = (byte) ((transactionId >> 8) & 0xFF);
    frame[1] = (byte) (transactionId & 0xFF);
    frame[2] = 0; // Protocol ID high
    frame[3] = 0; // Protocol ID low
    frame[4] = 0; // Length high
    frame[5] = 5; // Length low (UnitID + PDU = 1 + 4)
    frame[6] = (byte) unitId;
    frame[7] = (byte) FC_READ_DEVICE_IDENTIFICATION;
    frame[8] = (byte) MEI_TYPE_READ_DEVICE_ID;
    frame[9] = (byte) readCode.getCode();
    frame[10] = objectId;
    return frame;
  }

  // ── Response parsers ────────────────────────────────────────────────────

  /**
   * Parses FC 0x03 response and extracts 4-char ASCII string from 2 registers.
   *
   * @throws RuntimeException if response is an error or malformed
   */
  private String parseSunSpecSignatureResponse(byte[] response) {
    // Minimum: MBAP(7) + FC(1) + ExceptionCode(1) = 9 bytes for exception
    if (response.length < 9) {
      throw new RuntimeException("Response too short (got " + response.length + " bytes)");
    }

    int offset = 7;
    int functionCode = response[offset] & 0xFF;

    if ((functionCode & EXCEPTION_RESPONSE_FLAG) != 0) {
      int exceptionCode = response[offset + 1] & 0xFF;
      throw new RuntimeException("Modbus exception 0x%02X reading SunSpec signature".formatted(exceptionCode));
    }

    if (functionCode != FC_READ_HOLDING_REGISTERS) {
      throw new RuntimeException("Unexpected function code: 0x%02X".formatted(functionCode));
    }

    // Successful FC 0x03: MBAP(7) + FC(1) + ByteCount(1) + Data(N)
    if (response.length < 13) {
      throw new RuntimeException("Response too short for register data (got " + response.length + " bytes)");
    }

    int byteCount = response[offset + 1] & 0xFF;
    if (byteCount < 4 || offset + 2 + byteCount > response.length) {
      throw new RuntimeException("Invalid byte count: " + byteCount);
    }

    byte[] signatureBytes = new byte[4];
    System.arraycopy(response, offset + 2, signatureBytes, 0, 4);
    return new String(signatureBytes, StandardCharsets.US_ASCII);
  }

  /**
   * Parses FC 0x2B response into DeviceIdentification.
   *
   * @throws RuntimeException if response is an error or malformed
   */
  private DeviceIdentification parseDeviceIdentificationResponse(byte[] response) {
    if (response.length < 9) {
      throw new RuntimeException("Response too short (got " + response.length + " bytes)");
    }

    int offset = 7;
    int functionCode = response[offset] & 0xFF;

    if ((functionCode & EXCEPTION_RESPONSE_FLAG) != 0) {
      int exceptionCode = response[offset + 1] & 0xFF;
      throw new RuntimeException("Modbus exception 0x%02X reading device identification".formatted(exceptionCode));
    }

    if (functionCode != FC_READ_DEVICE_IDENTIFICATION) {
      throw new RuntimeException("Unexpected function code: 0x%02X".formatted(functionCode));
    }

    if (response.length < 14) {
      throw new RuntimeException("Response too short for device identification (got " + response.length + " bytes)");
    }

    // offset+6: Number of Objects
    int numObjects = response[offset + 6] & 0xFF;
    Map<Integer, String> objects = new HashMap<>();
    int objOffset = offset + 7;

    for (int i = 0; i < numObjects && objOffset < response.length - 1; i++) {
      int objectId = response[objOffset] & 0xFF;
      int objectLength = response[objOffset + 1] & 0xFF;

      if (objOffset + 2 + objectLength > response.length) {
        break;
      }

      String objectValue = new String(response, objOffset + 2, objectLength, StandardCharsets.US_ASCII);
      objects.put(objectId, objectValue.trim());
      objOffset += 2 + objectLength;
    }

    return new DeviceIdentification(
      objects.get(ModbusObjectId.VENDOR_NAME),
      objects.get(ModbusObjectId.PRODUCT_CODE),
      objects.get(ModbusObjectId.MAJOR_MINOR_REVISION),
      objects.get(ModbusObjectId.VENDOR_URL),
      objects.get(ModbusObjectId.PRODUCT_NAME),
      objects.get(ModbusObjectId.MODEL_NAME),
      objects.get(ModbusObjectId.USER_APPLICATION_NAME),
      objects,
      Instant.now()
    );
  }

  /**
   * Result of a connection test.
   *
   * @param success         true if the device responded to a Modbus read request with valid data
   * @param responseTimeMs  total time for the connection test
   * @param identification  device identification if FC 0x2B succeeded, null otherwise
   * @param errorMessage    error description if success is false
   * @param detectionMethod describes how the device was detected (e.g. "SunSpec", "Modbus")
   */
  public record TestResult(
    boolean success,
    long responseTimeMs,
    DeviceIdentification identification,
    String errorMessage,
    String detectionMethod
  ) {
    public boolean hasIdentification() {
      return identification != null;
    }
  }
}
