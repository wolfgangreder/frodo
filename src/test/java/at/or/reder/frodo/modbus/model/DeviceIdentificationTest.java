package at.or.reder.frodo.modbus.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceIdentificationTest {

  @Test
  void testFullRecord() {
    Instant now = Instant.now();
    Map<Integer, String> additional = Map.of(0x80, "CustomValue");

    DeviceIdentification id = new DeviceIdentification(
      "TestVendor", "TC-100", "1.0.0",
      "http://example.com", "TestProduct", "Model-X", "MyApp",
      additional, now
    );

    assertEquals("TestVendor", id.vendorName());
    assertEquals("TC-100", id.productCode());
    assertEquals("1.0.0", id.majorMinorRevision());
    assertEquals("http://example.com", id.vendorUrl());
    assertEquals("TestProduct", id.productName());
    assertEquals("Model-X", id.modelName());
    assertEquals("MyApp", id.userApplicationName());
    assertEquals(1, id.additionalObjects().size());
    assertEquals("CustomValue", id.additionalObjects().get(0x80));
    assertEquals(now, id.readTime());
  }

  @Test
  void testBasicFactory() {
    Instant now = Instant.now();
    DeviceIdentification id = DeviceIdentification.basic("Vendor", "PC-001", "2.1.0", now);

    assertEquals("Vendor", id.vendorName());
    assertEquals("PC-001", id.productCode());
    assertEquals("2.1.0", id.majorMinorRevision());
    assertNull(id.vendorUrl());
    assertNull(id.productName());
    assertNull(id.modelName());
    assertNull(id.userApplicationName());
    assertNotNull(id.additionalObjects());
    assertTrue(id.additionalObjects().isEmpty());
    assertEquals(now, id.readTime());
  }

  @Test
  void testRecordEquality() {
    Instant now = Instant.now();
    DeviceIdentification a = DeviceIdentification.basic("V", "P", "R", now);
    DeviceIdentification b = DeviceIdentification.basic("V", "P", "R", now);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }
}
