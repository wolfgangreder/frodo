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

package at.or.reder.frodo.modbus.entity;

import at.or.reder.frodo.modbus.model.DeviceIdentification;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ModbusDeviceInfoEntity}.
 */
class ModbusDeviceInfoEntityTest {

  @Test
  void testEntityCreation() {
    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    info.vendorName = "SolarTech";
    info.productCode = "ST-5000";
    info.revision = "1.2.3";
    info.vendorUrl = "http://example.com";
    info.productName = "Solar Inverter";
    info.modelName = "Model X";
    info.userAppName = "App Y";
    info.conformityLevel = 2;
    info.lastReadSuccess = true;
    info.readAttemptCount = 1;

    assertEquals("SolarTech", info.vendorName);
    assertEquals("ST-5000", info.productCode);
    assertEquals("1.2.3", info.revision);
    assertEquals("http://example.com", info.vendorUrl);
    assertEquals("Solar Inverter", info.productName);
    assertEquals("Model X", info.modelName);
    assertEquals("App Y", info.userAppName);
    assertEquals(2, info.conformityLevel);
    assertTrue(info.lastReadSuccess);
    assertEquals(1, info.readAttemptCount);
  }

  @Test
  void testUpdateFromDeviceIdentification() {
    DeviceIdentification identification = DeviceIdentification.basic(
      "Vendor", "Product", "1.0.0", Instant.now());

    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    info.updateFrom(identification, true, null);

    assertEquals("Vendor", info.vendorName);
    assertEquals("Product", info.productCode);
    assertEquals("1.0.0", info.revision);
    assertTrue(info.lastReadSuccess);
    assertNull(info.lastErrorMessage);
    assertEquals(1, info.readAttemptCount);
    assertNotNull(info.lastReadAt);
  }

  @Test
  void testUpdateFromDeviceIdentificationWithError() {
    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    info.updateFrom(null, false, "Connection timeout");

    assertFalse(info.lastReadSuccess);
    assertEquals("Connection timeout", info.lastErrorMessage);
    assertEquals(1, info.readAttemptCount);
    assertNotNull(info.lastReadAt);
  }

  @Test
  void testToDeviceIdentification() {
    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    info.vendorName = "TestVendor";
    info.productCode = "TC-100";
    info.revision = "2.0.0";
    info.vendorUrl = "http://test.example.com";
    info.productName = "TestProduct";
    info.modelName = "TM-1";
    info.userAppName = "TestApp";
    info.lastReadAt = Instant.now();
    info.onCreate();

    DeviceIdentification identification = info.toDeviceIdentification();

    assertEquals("TestVendor", identification.vendorName());
    assertEquals("TC-100", identification.productCode());
    assertEquals("2.0.0", identification.majorMinorRevision());
    assertEquals("http://test.example.com", identification.vendorUrl());
    assertEquals("TestProduct", identification.productName());
    assertEquals("TM-1", identification.modelName());
    assertEquals("TestApp", identification.userApplicationName());
    assertEquals(info.lastReadAt, identification.readTime());
  }

  @Test
  void testPrePersistLifecycle() {
    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    info.onCreate();

    assertNotNull(info.createdAt);
    assertNotNull(info.updatedAt);
    // Both should be set within same millisecond
    assertTrue(info.updatedAt.toEpochMilli() - info.createdAt.toEpochMilli() < 100);
  }

  @Test
  void testDefaultReadAttemptCount() {
    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    assertEquals(0, info.readAttemptCount);
  }

  @Test
  void testIncrementReadAttemptCount() {
    ModbusDeviceInfoEntity info = new ModbusDeviceInfoEntity();
    DeviceIdentification identification = DeviceIdentification.basic(
      "V", "P", "R", Instant.now());

    info.updateFrom(identification, true, null);
    assertEquals(1, info.readAttemptCount);

    info.updateFrom(identification, true, null);
    assertEquals(2, info.readAttemptCount);

    info.updateFrom(null, false, "Error");
    assertEquals(3, info.readAttemptCount);
  }
}
