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

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.ModbusTcpService;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.ModbusObjectId;
import at.or.reder.frodo.modbus.model.ReadDeviceIdCode;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

  /** Exception response flag: high bit set on function code. */
  private static final int EXCEPTION_RESPONSE_FLAG = 0x80;

  /** Function code for Read Holding Registers. */
  private static final int FC_READ_HOLDING_REGISTERS = 0x03;

  /** Function code for Read Device Identification (Encapsulated Interface Transport). */
  private static final int FC_READ_DEVICE_IDENTIFICATION = 0x2B;

  /** SunSpec base register address (40001 in Modbus addressing = 40000 in protocol). */
  private static final int SUNSPEC_BASE_ADDRESS = 40000;

  /** SunSpec "SunS" signature: 0x53756e53. */
  private static final String SUNSPEC_SIGNATURE = "SunS";

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
   * @return test result
   */
  public TestResult testConnection(String host, int port, int unitId) {
    LOG.infof("Testing connection to %s:%d (unit %d)", host, port, unitId);
    long startTime = System.currentTimeMillis();

    // Step 1: Read SunSpec signature via FC 0x03 (primary test)
    String signature;
    try {
      signature = readSunSpecSignature(host, port, unitId);
    } catch (Exception ex) {
      long elapsed = System.currentTimeMillis() - startTime;
      LOG.warnf("Connection test failed for %s:%d (unit %d): %s", host, port, unitId, ex.getMessage());
      return new TestResult(false, elapsed, null, ex.getMessage(), null);
    }

    long elapsed = System.currentTimeMillis() - startTime;
    boolean isSunSpec = SUNSPEC_SIGNATURE.equals(signature);
    LOG.infof("SunSpec signature read: '%s' (isSunSpec=%b, %dms)", signature, isSunSpec, elapsed);

    // Step 2: Try to get device identification on a separate connection (optional enrichment)
    String method = isSunSpec ? "SunSpec" : "Modbus";
    try {
      DeviceIdentification identification = readDeviceIdentification(host, port, unitId);
      return new TestResult(true, System.currentTimeMillis() - startTime,
        identification, null, method);
    } catch (Exception ex) {
      LOG.debugf("Device identification enrichment failed (non-critical): %s", ex.getMessage());
      return new TestResult(true, elapsed, null, null, method);
    }
  }

  /**
   * Opens a fresh TCP connection and reads the SunSpec signature at register 40000.
   *
   * @return the 4-character ASCII string read from registers 40000-40001
   * @throws IOException if connection or read fails
   */
  private String readSunSpecSignature(String host, int port, int unitId) throws IOException {
    int transactionId = transactionIdCounter.getAndIncrement() & 0xFFFF;
    byte[] request = ModbusTcpService.buildReadHoldingRegistersRequest(unitId, SUNSPEC_BASE_ADDRESS, 2, transactionId);

    byte[] response = sendAndReceive(host, port, request, "SunSpec signature", transactionId, unitId);
    return parseSunSpecSignatureResponse(response);
  }

  /**
   * Opens a fresh TCP connection and reads device identification via FC 0x2B.
   *
   * @return device identification
   * @throws IOException if connection or read fails
   */
  private DeviceIdentification readDeviceIdentification(String host, int port, int unitId) throws IOException {
    int transactionId = transactionIdCounter.getAndIncrement() & 0xFFFF;
    byte[] request = ModbusTcpService.buildReadDeviceIdentificationRequest(
      unitId, ReadDeviceIdCode.BASIC, ModbusObjectId.VENDOR_NAME, transactionId);

    byte[] response = sendAndReceive(host, port, request, "Device ID", transactionId, unitId);
    return parseDeviceIdentificationResponse(response);
  }

  /**
   * Opens a TCP connection, sends a Modbus request, and reads the complete MBAP response.
   *
   * @param host          hostname or IP address
   * @param port          TCP port
   * @param request       raw MBAP frame to send
   * @param label         descriptive label for logging
   * @param transactionId transaction ID for logging
   * @param unitId        unit ID for logging
   * @return raw response bytes
   * @throws IOException if connection, send, or read fails
   */
  private byte[] sendAndReceive(String host, int port, byte[] request, String label,
                                int transactionId, int unitId) throws IOException {
    int timeoutMs = testTimeoutSeconds * 1000;

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeoutMs);
      socket.setSoTimeout(timeoutMs);

      OutputStream out = socket.getOutputStream();
      InputStream in = socket.getInputStream();

      out.write(request);
      out.flush();
      LOG.debugf("%s request sent (txId=%d, unitId=%d)", label, transactionId, unitId);

      // Read MBAP header (6 bytes: TxID + Protocol + Length)
      byte[] header = readExactly(in, 6, label);
      int length = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);

      // Read remaining payload (Unit ID + PDU)
      byte[] payload = readExactly(in, length, label);

      // Assemble complete frame
      byte[] response = new byte[6 + length];
      System.arraycopy(header, 0, response, 0, 6);
      System.arraycopy(payload, 0, response, 6, length);

      LOG.debugf("%s response received (%d bytes, txId=%d, unitId=%d)",
        label, response.length, transactionId, unitId);
      return response;
    }
  }

  /**
   * Reads exactly {@code count} bytes from the input stream.
   *
   * @throws IOException if the stream ends before all bytes are read
   */
  private byte[] readExactly(InputStream in, int count, String label) throws IOException {
    byte[] buf = new byte[count];
    int offset = 0;
    while (offset < count) {
      int read = in.read(buf, offset, count - offset);
      if (read < 0) {
        throw new IOException(label + ": stream closed after " + offset + " of " + count + " bytes");
      }
      offset += read;
    }
    return buf;
  }

  // -- Response parsers -----------------------------------------------------

  /**
   * Parses FC 0x03 response and extracts 4-char ASCII string from 2 registers.
   *
   * @throws IOException if response is an error or malformed
   */
  private String parseSunSpecSignatureResponse(byte[] response) throws IOException {
    // Minimum: MBAP(7) + FC(1) + ExceptionCode(1) = 9 bytes for exception
    if (response.length < 9) {
      throw new IOException("Response too short (got " + response.length + " bytes)");
    }

    int offset = 7;
    int functionCode = response[offset] & 0xFF;

    if ((functionCode & EXCEPTION_RESPONSE_FLAG) != 0) {
      int exceptionCode = response[offset + 1] & 0xFF;
      throw new IOException("Modbus exception 0x%02X reading SunSpec signature".formatted(exceptionCode));
    }

    if (functionCode != FC_READ_HOLDING_REGISTERS) {
      throw new IOException("Unexpected function code: 0x%02X".formatted(functionCode));
    }

    // Successful FC 0x03: MBAP(7) + FC(1) + ByteCount(1) + Data(N)
    if (response.length < 13) {
      throw new IOException("Response too short for register data (got " + response.length + " bytes)");
    }

    int byteCount = response[offset + 1] & 0xFF;
    if (byteCount < 4 || offset + 2 + byteCount > response.length) {
      throw new IOException("Invalid byte count: " + byteCount);
    }

    byte[] signatureBytes = new byte[4];
    System.arraycopy(response, offset + 2, signatureBytes, 0, 4);
    return new String(signatureBytes, StandardCharsets.US_ASCII);
  }

  /**
   * Parses FC 0x2B response into DeviceIdentification.
   *
   * @throws IOException if response is an error or malformed
   */
  private DeviceIdentification parseDeviceIdentificationResponse(byte[] response) throws IOException {
    if (response.length < 9) {
      throw new IOException("Response too short (got " + response.length + " bytes)");
    }

    int offset = 7;
    int functionCode = response[offset] & 0xFF;

    if ((functionCode & EXCEPTION_RESPONSE_FLAG) != 0) {
      int exceptionCode = response[offset + 1] & 0xFF;
      throw new IOException("Modbus exception 0x%02X reading device identification".formatted(exceptionCode));
    }

    if (functionCode != FC_READ_DEVICE_IDENTIFICATION) {
      throw new IOException("Unexpected function code: 0x%02X".formatted(functionCode));
    }

    if (response.length < 14) {
      throw new IOException("Response too short for device identification (got " + response.length + " bytes)");
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
