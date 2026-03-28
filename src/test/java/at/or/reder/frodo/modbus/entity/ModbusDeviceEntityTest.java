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
