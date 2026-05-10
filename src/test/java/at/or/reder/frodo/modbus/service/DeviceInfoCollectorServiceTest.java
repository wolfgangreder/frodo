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

import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DeviceInfoCollectorService}.
 *
 * <p>Note: Full integration tests with Quarkus test framework are in Stage 5+.
 * These tests validate basic logic and data structures.</p>
 */
class DeviceInfoCollectorServiceTest {

  @Test
  void testTruncateErrorMessage_WithShortMessage_ReturnsOriginal() {
    DeviceInfoCollectorService collector = new DeviceInfoCollectorService();
    String shortMessage = "Connection failed";

    String result = collector.truncateErrorMessage(shortMessage);

    assertEquals(shortMessage, result);
  }

  @Test
  void testTruncateErrorMessage_WithLongMessage_Truncates() {
    DeviceInfoCollectorService collector = new DeviceInfoCollectorService();
    String longMessage = "A".repeat(600); // 600 characters

    String result = collector.truncateErrorMessage(longMessage);

    assertEquals(500, result.length());
    assertTrue(result.endsWith("..."));
    assertEquals("A".repeat(497) + "...", result);
  }

  @Test
  void testTruncateErrorMessage_WithNullMessage_ReturnsDefault() {
    DeviceInfoCollectorService collector = new DeviceInfoCollectorService();

    String result = collector.truncateErrorMessage(null);

    assertEquals("Unknown error", result);
  }

  @Test
  void testTruncateErrorMessage_WithExactly500Chars_ReturnsOriginal() {
    DeviceInfoCollectorService collector = new DeviceInfoCollectorService();
    String message = "A".repeat(500);

    String result = collector.truncateErrorMessage(message);

    assertEquals(message, result);
    assertEquals(500, result.length());
  }

  @Test
  void testDeviceEntity_GetConnectionString() {
    ModbusDeviceEntity device = new ModbusDeviceEntity();
    device.host = "192.168.1.100";
    device.port = 502;
    device.unitId = 1;

    String connectionString = device.getConnectionString();

    assertEquals("192.168.1.100:502/1", connectionString);
  }
}
