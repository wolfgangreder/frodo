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

import at.or.reder.frodo.modbus.model.DeviceIdentification;
import at.or.reder.frodo.modbus.model.ModbusObjectId;
import at.or.reder.frodo.modbus.model.ReadDeviceIdCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Modbus Read Device Identification (FC 0x2B/MEI 0x0E)
 * frame building and response parsing in ModbusTcpService.
 */
class ModbusDeviceIdentificationTest {

  // ---- Frame Building Tests ----

  @Test
  void testBuildBasicRequest() {
    byte[] frame = ModbusTcpService.buildReadDeviceIdentificationRequest(
      1, ReadDeviceIdCode.BASIC, 0x00, 42);

    // MBAP header (7) + PDU (4) = 11 bytes
    assertEquals(11, frame.length);

    // MBAP header
    assertEquals(0x00, frame[0]); // Transaction ID high
    assertEquals(42, frame[1] & 0xFF); // Transaction ID low
    assertEquals(0x00, frame[2]); // Protocol ID high
    assertEquals(0x00, frame[3]); // Protocol ID low
    assertEquals(0x00, frame[4]); // Length high
    assertEquals(0x05, frame[5]); // Length low (1 unitId + 4 PDU)
    assertEquals(0x01, frame[6]); // Unit ID

    // PDU
    assertEquals(0x2B, frame[7] & 0xFF);  // Function code
    assertEquals(0x0E, frame[8] & 0xFF);  // MEI Type
    assertEquals(0x01, frame[9] & 0xFF);  // Read Device ID Code: BASIC
    assertEquals(0x00, frame[10] & 0xFF); // Object ID: 0x00
  }

  @Test
  void testBuildRegularRequest() {
    byte[] frame = ModbusTcpService.buildReadDeviceIdentificationRequest(
      5, ReadDeviceIdCode.REGULAR, 0x00, 100);

    assertEquals(0x2B, frame[7] & 0xFF);  // FC
    assertEquals(0x0E, frame[8] & 0xFF);  // MEI Type
    assertEquals(0x02, frame[9] & 0xFF);  // REGULAR
    assertEquals(0x00, frame[10] & 0xFF); // Object ID
    assertEquals(0x05, frame[6] & 0xFF);  // Unit ID = 5
  }

  @Test
  void testBuildExtendedRequest() {
    byte[] frame = ModbusTcpService.buildReadDeviceIdentificationRequest(
      1, ReadDeviceIdCode.EXTENDED, 0x00, 1);

    assertEquals(0x03, frame[9] & 0xFF); // EXTENDED
  }

  @Test
  void testBuildSpecificRequest() {
    byte[] frame = ModbusTcpService.buildReadDeviceIdentificationRequest(
      1, ReadDeviceIdCode.SPECIFIC, 0x03, 1);

    assertEquals(0x04, frame[9] & 0xFF);  // SPECIFIC
    assertEquals(0x03, frame[10] & 0xFF); // Object ID = VENDOR_URL
  }

  @Test
  void testBuildContinuationRequest() {
    byte[] frame = ModbusTcpService.buildReadDeviceIdentificationRequest(
      1, ReadDeviceIdCode.EXTENDED, 0x83, 500);

    assertEquals(0x03, frame[9] & 0xFF);  // EXTENDED
    assertEquals(0x83, frame[10] & 0xFF); // Continuation object ID
  }

  @Test
  void testBuildRequestWithLargeTransactionId() {
    byte[] frame = ModbusTcpService.buildReadDeviceIdentificationRequest(
      1, ReadDeviceIdCode.BASIC, 0x00, 0xABCD);

    assertEquals(0xAB, frame[0] & 0xFF);
    assertEquals(0xCD, frame[1] & 0xFF);
  }

  // ---- Response Parsing Tests ----

  @Test
  void testParseBasicIdentificationResponse() throws ModbusException {
    // Build a response with 3 basic objects: VendorName, ProductCode, Revision
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x01, 0x01,  // FC, MEI, ReadCode, ConformityLevel
      0x00, 0x00,               // MoreFollows=false, NextObjectId=0x00
      3,                        // Number of objects
      new int[]{0x00}, "SolarTech",    // VendorName
      new int[]{0x01}, "ST-5000",      // ProductCode
      new int[]{0x02}, "1.2.3"         // Revision
    );

    ModbusTcpService.ParsedDeviceIdResponse parsed =
      ModbusTcpService.parseReadDeviceIdentificationResponse(response);

    assertFalse(parsed.moreFollows());
    assertEquals(3, parsed.objects().size());
    assertEquals("SolarTech", parsed.objects().get(0x00));
    assertEquals("ST-5000", parsed.objects().get(0x01));
    assertEquals("1.2.3", parsed.objects().get(0x02));
  }

  @Test
  void testParseRegularIdentificationResponse() throws ModbusException {
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x02, 0x02,  // ConformityLevel=Regular
      0x00, 0x00,
      6,
      new int[]{0x00}, "SolarTech",
      new int[]{0x01}, "ST-5000",
      new int[]{0x02}, "1.2.3",
      new int[]{0x03}, "http://solartech.example.com",
      new int[]{0x04}, "SolarInverter 5000",
      new int[]{0x05}, "SI-5K-PRO"
    );

    ModbusTcpService.ParsedDeviceIdResponse parsed =
      ModbusTcpService.parseReadDeviceIdentificationResponse(response);

    assertEquals(6, parsed.objects().size());
    assertEquals("http://solartech.example.com", parsed.objects().get(0x03));
    assertEquals("SolarInverter 5000", parsed.objects().get(0x04));
    assertEquals("SI-5K-PRO", parsed.objects().get(0x05));
  }

  @Test
  void testParseResponseWithMoreFollows() throws ModbusException {
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x03, 0x03,
      0xFF, 0x83,               // MoreFollows=true, NextObjectId=0x83
      3,
      new int[]{0x00}, "Vendor",
      new int[]{0x01}, "Code",
      new int[]{0x02}, "Rev"
    );

    ModbusTcpService.ParsedDeviceIdResponse parsed =
      ModbusTcpService.parseReadDeviceIdentificationResponse(response);

    assertTrue(parsed.moreFollows());
    assertEquals(0x83, parsed.nextObjectId());
    assertEquals(3, parsed.objects().size());
  }

  @Test
  void testParseExceptionResponse() {
    // Exception response: FC with high bit set (0xAB = 0x2B | 0x80), exception code
    byte[] response = new byte[]{
      0x00, 0x01,             // Transaction ID
      0x00, 0x00,             // Protocol ID
      0x00, 0x03,             // Length
      0x01,                   // Unit ID
      (byte) 0xAB,            // FC 0x2B with error bit
      0x01                    // Exception code: Illegal Function
    };

    ModbusException ex = assertThrows(ModbusException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));

    assertEquals(0x2B, ex.getFunctionCode());
    assertEquals(0x01, ex.getExceptionCode());
    assertTrue(ex.getMessage().contains("Illegal Function"));
  }

  @Test
  void testParseExceptionResponseIllegalDataAddress() {
    byte[] response = new byte[]{
      0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01,
      (byte) 0xAB,            // Error response
      0x02                    // Illegal Data Address
    };

    ModbusException ex = assertThrows(ModbusException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));

    assertEquals(0x02, ex.getExceptionCode());
    assertTrue(ex.getMessage().contains("Illegal Data Address"));
  }

  @Test
  void testParseExceptionResponseServerDeviceFailure() {
    byte[] response = new byte[]{
      0x00, 0x01, 0x00, 0x00, 0x00, 0x03, 0x01,
      (byte) 0xAB,
      0x04                    // Server Device Failure
    };

    ModbusException ex = assertThrows(ModbusException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));

    assertEquals(0x04, ex.getExceptionCode());
    assertTrue(ex.getMessage().contains("Server Device Failure"));
  }

  @Test
  void testParseNullResponse() {
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(null));
  }

  @Test
  void testParseTooShortResponse() {
    byte[] response = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x01};
    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));
  }

  @Test
  void testParseWrongFunctionCode() {
    byte[] response = new byte[]{
      0x00, 0x01, 0x00, 0x00, 0x00, 0x08, 0x01,
      0x03,                   // Wrong FC (Read Holding Registers)
      0x0E, 0x01, 0x01, 0x00, 0x00, 0x00
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));
  }

  @Test
  void testParseWrongMeiType() {
    byte[] response = new byte[]{
      0x00, 0x01, 0x00, 0x00, 0x00, 0x08, 0x01,
      0x2B,                   // Correct FC
      0x0D,                   // Wrong MEI type
      0x01, 0x01, 0x00, 0x00, 0x00
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));
  }

  @Test
  void testParseTruncatedObjectList() {
    // Claim 2 objects but only include 1
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x01, 0x01,
      0x00, 0x00,
      2,                        // Claims 2 objects
      new int[]{0x00}, "Vendor" // Only 1 object
    );

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));
  }

  @Test
  void testParseTruncatedObjectValue() {
    // Object claims length 100 but response is too short
    byte[] response = new byte[]{
      0x00, 0x01, 0x00, 0x00, 0x00, 0x0A, 0x01,  // MBAP
      0x2B, 0x0E, 0x01, 0x01, 0x00, 0x00,         // FC, MEI, etc.
      0x01,                                         // 1 object
      0x00, 0x64                                    // Object ID 0x00, length 100 (but data missing)
    };

    assertThrows(IllegalArgumentException.class,
      () -> ModbusTcpService.parseReadDeviceIdentificationResponse(response));
  }

  @Test
  void testParseEmptyObjectList() throws ModbusException {
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x01, 0x01,
      0x00, 0x00,
      0  // Zero objects
    );

    ModbusTcpService.ParsedDeviceIdResponse parsed =
      ModbusTcpService.parseReadDeviceIdentificationResponse(response);

    assertTrue(parsed.objects().isEmpty());
    assertFalse(parsed.moreFollows());
  }

  @Test
  void testParseObjectWithEmptyValue() throws ModbusException {
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x01, 0x01,
      0x00, 0x00,
      1,
      new int[]{0x00}, ""  // Empty vendor name
    );

    ModbusTcpService.ParsedDeviceIdResponse parsed =
      ModbusTcpService.parseReadDeviceIdentificationResponse(response);

    assertEquals("", parsed.objects().get(0x00));
  }

  @Test
  void testParseVendorSpecificObjects() throws ModbusException {
    byte[] response = buildDeviceIdResponse(
      0x2B, 0x0E, 0x03, 0x03,
      0x00, 0x00,
      2,
      new int[]{0x80}, "CustomField1",
      new int[]{0x81}, "CustomField2"
    );

    ModbusTcpService.ParsedDeviceIdResponse parsed =
      ModbusTcpService.parseReadDeviceIdentificationResponse(response);

    assertEquals(2, parsed.objects().size());
    assertEquals("CustomField1", parsed.objects().get(0x80));
    assertEquals("CustomField2", parsed.objects().get(0x81));
  }

  // ---- buildDeviceIdentification Tests ----

  @Test
  void testBuildDeviceIdentificationBasic() {
    Map<Integer, String> objects = new HashMap<>();
    objects.put(0x00, "TestVendor");
    objects.put(0x01, "TP-100");
    objects.put(0x02, "3.0.1");

    DeviceIdentification id = ModbusTcpService.buildDeviceIdentification(objects);

    assertEquals("TestVendor", id.vendorName());
    assertEquals("TP-100", id.productCode());
    assertEquals("3.0.1", id.majorMinorRevision());
    assertNull(id.vendorUrl());
    assertNull(id.productName());
    assertNull(id.modelName());
    assertNull(id.userApplicationName());
    assertTrue(id.additionalObjects().isEmpty());
    assertNotNull(id.readTime());
  }

  @Test
  void testBuildDeviceIdentificationFull() {
    Map<Integer, String> objects = new HashMap<>();
    objects.put(0x00, "Vendor");
    objects.put(0x01, "Code");
    objects.put(0x02, "Rev");
    objects.put(0x03, "http://vendor.example.com");
    objects.put(0x04, "ProductX");
    objects.put(0x05, "ModelY");
    objects.put(0x06, "AppZ");
    objects.put(0x80, "VendorData1");
    objects.put(0x81, "VendorData2");

    DeviceIdentification id = ModbusTcpService.buildDeviceIdentification(objects);

    assertEquals("Vendor", id.vendorName());
    assertEquals("Code", id.productCode());
    assertEquals("Rev", id.majorMinorRevision());
    assertEquals("http://vendor.example.com", id.vendorUrl());
    assertEquals("ProductX", id.productName());
    assertEquals("ModelY", id.modelName());
    assertEquals("AppZ", id.userApplicationName());
    assertEquals(2, id.additionalObjects().size());
    assertEquals("VendorData1", id.additionalObjects().get(0x80));
    assertEquals("VendorData2", id.additionalObjects().get(0x81));
  }

  @Test
  void testBuildDeviceIdentificationMissingMandatory() {
    Map<Integer, String> objects = new HashMap<>();
    // Missing all mandatory fields — should use empty strings as defaults

    DeviceIdentification id = ModbusTcpService.buildDeviceIdentification(objects);

    assertEquals("", id.vendorName());
    assertEquals("", id.productCode());
    assertEquals("", id.majorMinorRevision());
  }

  // ---- Helper Methods ----

  /**
   * Builds a mock Modbus Read Device Identification response frame.
   * Variable arguments: pairs of (int[] objectId, String value) after the fixed header fields.
   */
  private static byte[] buildDeviceIdResponse(int fc, int meiType, int readCode, int conformityLevel,
                                              int moreFollows, int nextObjectId,
                                              int numberOfObjects, Object... objectPairs) {
    // Calculate total object data size
    int objectDataSize = 0;
    for (int i = 0; i < objectPairs.length; i += 2) {
      String value = (String) objectPairs[i + 1];
      objectDataSize += 2 + value.length(); // objId(1) + objLen(1) + value(n)
    }

    // MBAP(7) + FC(1) + MEI(1) + ReadCode(1) + Conformity(1) + MoreFollows(1) + NextObjId(1) + NumObj(1) + objects
    int totalLen = 7 + 7 + objectDataSize;
    byte[] response = new byte[totalLen];

    // MBAP header
    response[0] = 0x00;                           // Transaction ID high
    response[1] = 0x01;                           // Transaction ID low
    response[2] = 0x00;                           // Protocol ID high
    response[3] = 0x00;                           // Protocol ID low
    int pduLen = totalLen - 6;                     // Length = total - MBAP header (6 bytes, excl length field)
    response[4] = (byte) (pduLen >> 8);
    response[5] = (byte) (pduLen & 0xFF);
    response[6] = 0x01;                           // Unit ID

    // PDU header
    response[7] = (byte) fc;
    response[8] = (byte) meiType;
    response[9] = (byte) readCode;
    response[10] = (byte) conformityLevel;
    response[11] = (byte) moreFollows;
    response[12] = (byte) nextObjectId;
    response[13] = (byte) numberOfObjects;

    // Objects
    int offset = 14;
    for (int i = 0; i < objectPairs.length; i += 2) {
      int[] objIdArr = (int[]) objectPairs[i];
      String value = (String) objectPairs[i + 1];
      byte[] valueBytes = value.getBytes(StandardCharsets.US_ASCII);
      response[offset++] = (byte) objIdArr[0];
      response[offset++] = (byte) valueBytes.length;
      System.arraycopy(valueBytes, 0, response, offset, valueBytes.length);
      offset += valueBytes.length;
    }

    return response;
  }
}
