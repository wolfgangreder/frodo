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

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ModbusDeviceEntity}.
 */
class ModbusDeviceEntityTest {

  @Test
  void testEntityCreation() {
    ModbusDeviceEntity device = new ModbusDeviceEntity();
    device.name = "Test Device";
    device.host = "192.168.1.100";
    device.port = 502;
    device.unitId = 1;
    device.enabled = true;
    device.description = "Test description";
    device.connectionTimeoutSeconds = 30;

    assertEquals("Test Device", device.name);
    assertEquals("192.168.1.100", device.host);
    assertEquals(502, device.port);
    assertEquals(1, device.unitId);
    assertTrue(device.enabled);
    assertEquals("Test description", device.description);
    assertEquals(30, device.connectionTimeoutSeconds);
  }

  @Test
  void testGetConnectionString() {
    ModbusDeviceEntity device = new ModbusDeviceEntity();
    device.host = "192.168.1.100";
    device.port = 502;
    device.unitId = 5;

    assertEquals("192.168.1.100:502/5", device.getConnectionString());
  }

  @Test
  void testPrePersistLifecycle() {
    ModbusDeviceEntity device = new ModbusDeviceEntity();
    device.onCreate();

    assertNotNull(device.createdAt);
    assertNotNull(device.updatedAt);
    // Both should be set within same millisecond
    assertTrue(device.updatedAt.toEpochMilli() - device.createdAt.toEpochMilli() < 100);
  }

  @Test
  void testPreUpdateLifecycle() throws InterruptedException {
    ModbusDeviceEntity device = new ModbusDeviceEntity();
    device.onCreate();
    Instant originalUpdatedAt = device.updatedAt;

    Thread.sleep(10); // Ensure time difference
    device.onUpdate();

    assertTrue(device.updatedAt.isAfter(originalUpdatedAt));
  }

  @Test
  void testDefaultEnabledValue() {
    ModbusDeviceEntity device = new ModbusDeviceEntity();
    assertTrue(device.enabled);
  }
}
