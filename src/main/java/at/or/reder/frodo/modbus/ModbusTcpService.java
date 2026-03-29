package at.or.reder.frodo.modbus;

import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.ModbusObjectId;
import at.or.reder.frodo.modbus.model.ReadDeviceIdCode;
import io.smallrye.mutiny.Uni;
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
 * Service for accessing Modbus devices over TCP using connection pooling.
 * Supports Modbus TCP protocol (MBAP header + PDU).
 *
 * <p>Supported function codes:</p>
 * <ul>
 *   <li>FC 0x03 - Read Holding Registers (see {@code refdoc/modbus.pdf} Section 6.3)</li>
 *   <li>FC 0x06 - Write Single Register (see {@code refdoc/modbus.pdf} Section 6.6, guarded by write-enabled config)</li>
 *   <li>FC 0x10 - Write Multiple Registers (see {@code refdoc/modbus.pdf} Section 6.16, guarded by write-enabled config)</li>
 *   <li>FC 0x2B/0x0E - Read Device Identification (see {@code refdoc/modbus.pdf} Section 6.21, MEI Transport)</li>
 * </ul>
 *
 * <p><b>Protocol References:</b></p>
 * <ul>
 *   <li>Modbus Application Protocol V1.1b3: {@code refdoc/modbus.pdf}</li>
 *   <li>MBAP Header: Section 4.1 (Transaction ID, Protocol ID, Length, Unit ID)</li>
 *   <li>Function Codes: Section 5 (public codes), Section 6 (detailed specifications)</li>
 *   <li>Exception Responses: Section 7 (exception codes 0x01-0x0B)</li>
 * </ul>
 */
@ApplicationScoped
public class ModbusTcpService {

  private static final Logger LOG = Logger.getLogger(ModbusTcpService.class);

  /** Function code for Write Single Register. */
  static final int FC_WRITE_SINGLE_REGISTER = 0x06;

  /** Function code for Write Multiple Registers. */
  static final int FC_WRITE_MULTIPLE_REGISTERS = 0x10;

  /** Function code for Read Device Identification (Encapsulated Interface Transport). */
  static final int FC_READ_DEVICE_IDENTIFICATION = 0x2B;

  /** MEI type for Read Device Identification. */
  static final int MEI_TYPE_READ_DEVICE_ID = 0x0E;

  /** Exception response flag: high bit set on function code. */
  static final int EXCEPTION_RESPONSE_FLAG = 0x80;

  /** Maximum number of "More Follows" continuation requests to prevent infinite loops. */
  static final int MAX_CONTINUATION_REQUESTS = 20;

  @Inject
  ModbusConnectionPool connectionPool;

    @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
    boolean modbusEnabled;

    @ConfigProperty(name = "frodo.modbus.write-enabled", defaultValue = "false")
    boolean writeEnabled;

    @ConfigProperty(name = "frodo.modbus.request.max-retries", defaultValue = "3")
    int maxRetries;

    @ConfigProperty(name = "frodo.modbus.request.retry-delay-seconds", defaultValue = "2")
    int retryDelaySeconds;

    private final AtomicInteger transactionIdCounter = new AtomicInteger(1);

    /**
     * Reads holding registers from a Modbus TCP device.
     *
     * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.3 (FC 0x03)</p>
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
     * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.3</p>
     * <p>Request PDU: Function code (1 byte) + Starting Address (2 bytes) + Quantity (2 bytes)</p>
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
     * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 4.1 (MBAP Header)</p>
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
     * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.3</p>
     * <p>Response PDU: Function code (1 byte) + Byte count (1 byte) + Register values (N*2 bytes)</p>
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

  // ---- FC 0x2B / MEI 0x0E: Read Device Identification ----

  /**
   * Reads device identification from a Modbus device using FC 0x2B/MEI 0x0E.
   *
   * <p>This method handles segmented responses by automatically issuing
   * continuation requests when the "More Follows" flag is set in the
   * response. All objects from all segments are merged into a single
   * {@link DeviceIdentification} result.</p>
   *
   * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.21
   * (Encapsulated Interface Transport, MEI Type 0x0E)</p>
   *
   * @param unitId   Modbus unit/device ID (1-247)
   * @param readCode the identification level to request
   * @return Uni resolving to the device identification data
   */
  public Uni<DeviceIdentification> readDeviceIdentification(int unitId, ReadDeviceIdCode readCode) {
    if (!modbusEnabled) {
      LOG.debug("Modbus is disabled, returning empty device identification");
      return Uni.createFrom().item(DeviceIdentification.basic("", "", "", Instant.now()));
    }

    Map<Integer, String> collectedObjects = new HashMap<>();
    return readDeviceIdSegment(unitId, readCode, ModbusObjectId.VENDOR_NAME, collectedObjects, 0)
      .onFailure().retry()
        .withBackOff(Duration.ofSeconds(retryDelaySeconds))
        .atMost(maxRetries)
      .onFailure().invoke(e -> LOG.errorf(e, "Failed to read device identification after %d retries", maxRetries));
  }

  /**
   * Reads a single segment of a device identification response, recursively
   * issuing continuation requests if "More Follows" is indicated.
   *
   * @param unitId           Modbus unit/device ID
   * @param readCode         the identification level to request
   * @param startObjectId    the object ID to start from (0x00 for first, or continuation ID)
   * @param collectedObjects accumulated objects from previous segments
   * @param segmentCount     number of continuation requests made so far (safety limit)
   * @return Uni resolving to the merged DeviceIdentification
   */
  private Uni<DeviceIdentification> readDeviceIdSegment(int unitId, ReadDeviceIdCode readCode,
                                                        int startObjectId,
                                                        Map<Integer, String> collectedObjects,
                                                        int segmentCount) {
    if (segmentCount >= MAX_CONTINUATION_REQUESTS) {
      LOG.warnf("Exceeded maximum continuation requests (%d) for device identification on unit %d",
        MAX_CONTINUATION_REQUESTS, unitId);
      return Uni.createFrom().item(buildDeviceIdentification(collectedObjects));
    }

    int transactionId = getNextTransactionId();
    byte[] request = buildReadDeviceIdentificationRequest(unitId, readCode, startObjectId, transactionId);

    return connectionPool.executeRequest(request, transactionId)
      .onItem().transformToUni(response -> {
        try {
          ParsedDeviceIdResponse parsed = parseReadDeviceIdentificationResponse(response);
          collectedObjects.putAll(parsed.objects());

          if (parsed.moreFollows() && parsed.nextObjectId() >= 0) {
            LOG.debugf("More follows for device identification on unit %d, next object ID: 0x%02X",
              unitId, parsed.nextObjectId());
            return readDeviceIdSegment(unitId, readCode, parsed.nextObjectId(),
              collectedObjects, segmentCount + 1);
          }

          return Uni.createFrom().item(buildDeviceIdentification(collectedObjects));
        } catch (ModbusException e) {
          return Uni.createFrom().failure(e);
        }
      });
  }

  /**
   * Builds a Modbus TCP Read Device Identification (FC 0x2B/MEI 0x0E) request frame.
   *
   * <p>Frame structure:</p>
   * <pre>
   * MBAP Header (7 bytes) + Function Code (0x2B) + MEI Type (0x0E)
   *   + Read Device ID Code (1 byte) + Object ID (1 byte)
   * </pre>
   *
   * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.21</p>
   * <p>Read Device ID Codes: 0x01 (Basic), 0x02 (Regular), 0x03 (Extended), 0x04 (Specific)</p>
   *
   * @param unitId        Modbus unit/device ID
   * @param readCode      the identification level to request
   * @param objectId      the starting object ID (0x00 for first request)
   * @param transactionId transaction ID for request
   * @return complete Modbus TCP frame (MBAP + PDU)
   */
  static byte[] buildReadDeviceIdentificationRequest(int unitId, ReadDeviceIdCode readCode,
                                                     int objectId, int transactionId) {
    byte[] pdu = {
      (byte) FC_READ_DEVICE_IDENTIFICATION,  // Function code: 0x2B
      (byte) MEI_TYPE_READ_DEVICE_ID,        // MEI Type: 0x0E
      (byte) readCode.getCode(),             // Read Device ID code
      (byte) objectId                        // Object ID
    };
    return buildMbapFrame(transactionId, unitId, pdu);
  }

  /**
   * Parses a Modbus TCP Read Device Identification response.
   *
   * <p>Response PDU structure (after MBAP header + unit ID):</p>
   * <pre>
   * FC (0x2B) + MEI Type (0x0E) + Read Device ID Code (1)
   *   + Conformity Level (1) + More Follows (1) + Next Object ID (1)
   *   + Number of Objects (1) + [Object ID (1) + Object Length (1) + Object Value (n)] ...
   * </pre>
   *
   * @param response complete Modbus TCP response frame (MBAP header + PDU)
   * @return parsed response containing the objects and continuation info
   * @throws ModbusException        if the response is a Modbus exception response
   * @throws IllegalArgumentException if the response is malformed
   */
  static ParsedDeviceIdResponse parseReadDeviceIdentificationResponse(byte[] response) {
    if (response == null || response.length < 8) {
      throw new IllegalArgumentException("Response too short: " + (response == null ? 0 : response.length));
    }

    // Bytes 0-6: MBAP header, byte 7: function code in response
    int fc = response[7] & 0xFF;

    // Check for exception response (high bit set)
    if ((fc & EXCEPTION_RESPONSE_FLAG) != 0) {
      int originalFc = fc & 0x7F;
      int exceptionCode = (response.length > 8) ? (response[8] & 0xFF) : 0;
      throw new ModbusException(originalFc, exceptionCode);
    }

    // Validate function code
    if (fc != FC_READ_DEVICE_IDENTIFICATION) {
      throw new IllegalArgumentException(
        String.format("Unexpected function code: 0x%02X, expected 0x%02X", fc, FC_READ_DEVICE_IDENTIFICATION));
    }

    // Minimum response: MBAP(7) + FC(1) + MEI(1) + ReadCode(1) + Conformity(1) + MoreFollows(1) + NextObjId(1) + NumObj(1) = 14
    if (response.length < 14) {
      throw new IllegalArgumentException("Response too short for device identification: " + response.length);
    }

    int meiType = response[8] & 0xFF;
    if (meiType != MEI_TYPE_READ_DEVICE_ID) {
      throw new IllegalArgumentException(
        String.format("Unexpected MEI type: 0x%02X, expected 0x%02X", meiType, MEI_TYPE_READ_DEVICE_ID));
    }

    // response[9] = Read Device ID code (echo)
    // response[10] = Conformity level
    boolean moreFollows = (response[11] & 0xFF) != 0x00;
    int nextObjectId = response[12] & 0xFF;
    int numberOfObjects = response[13] & 0xFF;

    Map<Integer, String> objects = new HashMap<>();
    int offset = 14;

    for (int i = 0; i < numberOfObjects; i++) {
      if (offset + 2 > response.length) {
        throw new IllegalArgumentException(
          String.format("Truncated response: expected object %d/%d at offset %d, response length %d",
            i + 1, numberOfObjects, offset, response.length));
      }

      int objId = response[offset] & 0xFF;
      int objLen = response[offset + 1] & 0xFF;
      offset += 2;

      if (offset + objLen > response.length) {
        throw new IllegalArgumentException(
          String.format("Truncated object value: object ID 0x%02X, length %d, available %d",
            objId, objLen, response.length - offset));
      }

      String value = new String(response, offset, objLen, StandardCharsets.US_ASCII);
      objects.put(objId, value);
      offset += objLen;
    }

    return new ParsedDeviceIdResponse(objects, moreFollows, nextObjectId);
  }

  /**
   * Builds a {@link DeviceIdentification} from a map of collected object ID to value pairs.
   *
   * @param objects map of object ID to string value
   * @return populated DeviceIdentification record
   */
  static DeviceIdentification buildDeviceIdentification(Map<Integer, String> objects) {
    // Extract standard objects, removing them from the map for additionalObjects
    Map<Integer, String> additional = new HashMap<>();
    for (Map.Entry<Integer, String> entry : objects.entrySet()) {
      int id = entry.getKey();
      if (id > ModbusObjectId.USER_APPLICATION_NAME) {
        additional.put(id, entry.getValue());
      }
    }

    return new DeviceIdentification(
      objects.getOrDefault(ModbusObjectId.VENDOR_NAME, ""),
      objects.getOrDefault(ModbusObjectId.PRODUCT_CODE, ""),
      objects.getOrDefault(ModbusObjectId.MAJOR_MINOR_REVISION, ""),
      objects.get(ModbusObjectId.VENDOR_URL),
      objects.get(ModbusObjectId.PRODUCT_NAME),
      objects.get(ModbusObjectId.MODEL_NAME),
      objects.get(ModbusObjectId.USER_APPLICATION_NAME),
      Map.copyOf(additional),
      Instant.now()
    );
  }

  /**
   * Internal record holding the parsed result of a single Read Device Identification
   * response segment, including continuation information.
   *
   * @param objects       map of object ID to string value from this segment
   * @param moreFollows   true if the device has more objects to send
   * @param nextObjectId  the object ID to request in the next continuation request
   */
  record ParsedDeviceIdResponse(
    Map<Integer, String> objects,
    boolean moreFollows,
    int nextObjectId
  ) {}

  // ---- FC 0x06: Write Single Register ----

  /**
   * Writes a single holding register on a Modbus TCP device.
   *
   * <p>This operation is guarded by the {@code frodo.modbus.write-enabled}
   * configuration property. When disabled, it returns a failed Uni.</p>
   *
   * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.6 (FC 0x06)</p>
   *
   * @param unitId  Modbus unit/device ID (1-247)
   * @param address register address to write
   * @param value   value to write (0-65535)
   * @return Uni that completes when the write is acknowledged
   * @throws IllegalStateException if write operations are disabled
   */
  public Uni<Void> writeSingleRegister(int unitId, int address, int value) {
    if (!writeEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Write operations are disabled. Set frodo.modbus.write-enabled=true to enable."));
    }
    if (!modbusEnabled) {
      LOG.debug("Modbus is disabled, skipping write");
      return Uni.createFrom().voidItem();
    }

    int transactionId = getNextTransactionId();
    byte[] request = buildWriteSingleRegisterRequest(unitId, address, value, transactionId);

    return connectionPool.executeRequest(request, transactionId)
      .onItem().transform(response -> {
        parseWriteSingleRegisterResponse(response);
        return null;
      })
      .replaceWithVoid()
      .onFailure().retry()
        .withBackOff(Duration.ofSeconds(retryDelaySeconds))
        .atMost(maxRetries)
      .onFailure().invoke(e -> LOG.errorf(e, "Failed to write single register at address %d", address));
  }

  /**
   * Builds a Modbus TCP Write Single Register (FC=06) request frame.
   *
   * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.6</p>
   * <p>Request PDU: Function code (1 byte) + Register Address (2 bytes) + Register Value (2 bytes)</p>
   *
   * @param unitId        Modbus unit/device ID
   * @param address       register address to write
   * @param value         value to write
   * @param transactionId transaction ID for request
   * @return complete Modbus TCP frame (MBAP + PDU)
   */
  static byte[] buildWriteSingleRegisterRequest(int unitId, int address, int value, int transactionId) {
    byte[] pdu = {
      (byte) FC_WRITE_SINGLE_REGISTER,   // Function code: 0x06
      (byte) (address >> 8),              // Register address high byte
      (byte) (address & 0xFF),            // Register address low byte
      (byte) (value >> 8),                // Register value high byte
      (byte) (value & 0xFF)               // Register value low byte
    };
    return buildMbapFrame(transactionId, unitId, pdu);
  }

  /**
   * Parses a Modbus TCP Write Single Register response.
   * The response is an echo of the request (address + value).
   *
   * @param response complete Modbus TCP response frame
   * @throws ModbusException if the response is an exception
   * @throws IllegalArgumentException if the response is malformed
   */
  static void parseWriteSingleRegisterResponse(byte[] response) {
    if (response == null || response.length < 8) {
      throw new IllegalArgumentException("Response too short: " + (response == null ? 0 : response.length));
    }
    int fc = response[7] & 0xFF;
    if ((fc & EXCEPTION_RESPONSE_FLAG) != 0) {
      int exceptionCode = (response.length > 8) ? (response[8] & 0xFF) : 0;
      throw new ModbusException(fc & 0x7F, exceptionCode);
    }
    if (fc != FC_WRITE_SINGLE_REGISTER) {
      throw new IllegalArgumentException(
        String.format("Unexpected function code: 0x%02X, expected 0x%02X", fc, FC_WRITE_SINGLE_REGISTER));
    }
    // Response is echo of request: FC + Address(2) + Value(2) = 5 bytes after MBAP
    if (response.length < 12) {
      throw new IllegalArgumentException("Write single register response too short: " + response.length);
    }
  }

  // ---- FC 0x10: Write Multiple Registers ----

  /**
   * Writes multiple holding registers on a Modbus TCP device.
   *
   * <p>This operation is guarded by the {@code frodo.modbus.write-enabled}
   * configuration property. When disabled, it returns a failed Uni.</p>
   *
   * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.16 (FC 0x10)</p>
   *
   * @param unitId    Modbus unit/device ID (1-247)
   * @param startAddr starting register address
   * @param values    values to write (each 0-65535)
   * @return Uni that completes when the write is acknowledged
   * @throws IllegalStateException if write operations are disabled
   */
  public Uni<Void> writeMultipleRegisters(int unitId, int startAddr, int[] values) {
    if (!writeEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Write operations are disabled. Set frodo.modbus.write-enabled=true to enable."));
    }
    if (!modbusEnabled) {
      LOG.debug("Modbus is disabled, skipping write");
      return Uni.createFrom().voidItem();
    }
    if (values == null || values.length == 0) {
      return Uni.createFrom().failure(
        new IllegalArgumentException("Values array must not be empty"));
    }
    if (values.length > 123) {
      return Uni.createFrom().failure(
        new IllegalArgumentException("Cannot write more than 123 registers at once"));
    }

    int transactionId = getNextTransactionId();
    byte[] request = buildWriteMultipleRegistersRequest(unitId, startAddr, values, transactionId);

    return connectionPool.executeRequest(request, transactionId)
      .onItem().transform(response -> {
        parseWriteMultipleRegistersResponse(response, values.length);
        return null;
      })
      .replaceWithVoid()
      .onFailure().retry()
        .withBackOff(Duration.ofSeconds(retryDelaySeconds))
        .atMost(maxRetries)
      .onFailure().invoke(e -> LOG.errorf(e, "Failed to write %d registers at address %d",
        values.length, startAddr));
  }

  /**
   * Builds a Modbus TCP Write Multiple Registers (FC=10) request frame.
   *
   * <p><b>Protocol Reference:</b> {@code refdoc/modbus.pdf} Section 6.16</p>
   * <p>Request PDU: Function code (1 byte) + Starting Address (2 bytes) + Quantity (2 bytes)
   * + Byte count (1 byte) + Register values (N*2 bytes)</p>
   *
   * @param unitId        Modbus unit/device ID
   * @param startAddr     starting register address
   * @param values        register values to write
   * @param transactionId transaction ID for request
   * @return complete Modbus TCP frame (MBAP + PDU)
   */
  static byte[] buildWriteMultipleRegistersRequest(int unitId, int startAddr, int[] values, int transactionId) {
    int byteCount = values.length * 2;
    byte[] pdu = new byte[6 + byteCount];
    pdu[0] = (byte) FC_WRITE_MULTIPLE_REGISTERS;  // Function code: 0x10
    pdu[1] = (byte) (startAddr >> 8);              // Starting address high byte
    pdu[2] = (byte) (startAddr & 0xFF);            // Starting address low byte
    pdu[3] = (byte) (values.length >> 8);          // Quantity high byte
    pdu[4] = (byte) (values.length & 0xFF);        // Quantity low byte
    pdu[5] = (byte) byteCount;                     // Byte count
    for (int i = 0; i < values.length; i++) {
      pdu[6 + i * 2] = (byte) (values[i] >> 8);
      pdu[7 + i * 2] = (byte) (values[i] & 0xFF);
    }
    return buildMbapFrame(transactionId, unitId, pdu);
  }

  /**
   * Parses a Modbus TCP Write Multiple Registers response.
   *
   * @param response      complete Modbus TCP response frame
   * @param expectedCount expected number of registers written
   * @throws ModbusException if the response is an exception
   * @throws IllegalArgumentException if the response is malformed
   */
  static void parseWriteMultipleRegistersResponse(byte[] response, int expectedCount) {
    if (response == null || response.length < 8) {
      throw new IllegalArgumentException("Response too short: " + (response == null ? 0 : response.length));
    }
    int fc = response[7] & 0xFF;
    if ((fc & EXCEPTION_RESPONSE_FLAG) != 0) {
      int exceptionCode = (response.length > 8) ? (response[8] & 0xFF) : 0;
      throw new ModbusException(fc & 0x7F, exceptionCode);
    }
    if (fc != FC_WRITE_MULTIPLE_REGISTERS) {
      throw new IllegalArgumentException(
        String.format("Unexpected function code: 0x%02X, expected 0x%02X", fc, FC_WRITE_MULTIPLE_REGISTERS));
    }
    // Response: FC + Address(2) + Quantity(2) = 5 bytes after MBAP
    if (response.length < 12) {
      throw new IllegalArgumentException("Write multiple registers response too short: " + response.length);
    }
    int writtenCount = ((response[10] & 0xFF) << 8) | (response[11] & 0xFF);
    if (writtenCount != expectedCount) {
      throw new IllegalArgumentException(
        String.format("Expected %d registers written, but device reported %d", expectedCount, writtenCount));
    }
  }

  /**
   * Checks whether write operations are enabled.
   *
   * @return true if write operations are allowed
   */
  public boolean isWriteEnabled() {
    return writeEnabled;
  }
}
