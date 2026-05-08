package at.or.reder.frodo.solarapi;

import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SolarApiFields}.
 *
 * <p>Verifies that the Solar API site field descriptors are complete, correctly
 * named, and consistent with the constants used by the scraping pipeline.</p>
 */
class SolarApiFieldsTest {

  private static final List<SolarApiFields.FieldDescriptor> FIELDS = SolarApiFields.SITE_FIELDS;

  @Test
  void siteFields_hasExactlySixFields() {
    assertEquals(6, FIELDS.size());
  }

  @Test
  void siteFields_containsAllExpectedFieldNames() {
    Set<String> names = FIELDS.stream()
      .map(SolarApiFields.FieldDescriptor::fieldName)
      .collect(Collectors.toSet());

    assertTrue(names.contains("grid_power_watts"));
    assertTrue(names.contains("load_power_watts"));
    assertTrue(names.contains("pv_power_watts"));
    assertTrue(names.contains("battery_power_watts"));
    assertTrue(names.contains("autonomy_ratio"));
    assertTrue(names.contains("self_consumption_ratio"));
  }

  @Test
  void siteFields_powerFields_haveWattsUnit() {
    List<String> powerFields = List.of(
      "grid_power_watts", "load_power_watts", "pv_power_watts", "battery_power_watts");

    for (SolarApiFields.FieldDescriptor field : FIELDS) {
      if (powerFields.contains(field.fieldName())) {
        assertEquals("W", field.units(),
          "Power field " + field.fieldName() + " should have unit W");
      }
    }
  }

  @Test
  void siteFields_ratioFields_haveEmptyUnit() {
    List<String> ratioFields = List.of("autonomy_ratio", "self_consumption_ratio");

    for (SolarApiFields.FieldDescriptor field : FIELDS) {
      if (ratioFields.contains(field.fieldName())) {
        assertEquals("", field.units(),
          "Ratio field " + field.fieldName() + " should have empty unit string");
      }
    }
  }

  @Test
  void siteFields_allMetricNames_startWithFrodoSolarSite() {
    for (SolarApiFields.FieldDescriptor field : FIELDS) {
      assertTrue(field.metricName().startsWith("frodo_solar_site_"),
        "Metric name for " + field.fieldName() + " should start with frodo_solar_site_");
    }
  }

  @Test
  void siteFields_metricName_matchesFieldNameSuffix() {
    for (SolarApiFields.FieldDescriptor field : FIELDS) {
      String expectedSuffix = field.fieldName().toLowerCase();
      assertTrue(field.metricName().endsWith(expectedSuffix),
        "Metric name " + field.metricName() + " should end with " + expectedSuffix);
    }
  }

  @Test
  void siteFields_noNullFieldNames() {
    for (SolarApiFields.FieldDescriptor field : FIELDS) {
      assertNotNull(field.fieldName(), "fieldName must not be null");
      assertNotNull(field.units(), "units must not be null (use empty string for dimensionless)");
      assertNotNull(field.description(), "description must not be null");
      assertNotNull(field.metricName(), "metricName must not be null");
    }
  }

  @Test
  void siteFields_noBlankDescriptions() {
    for (SolarApiFields.FieldDescriptor field : FIELDS) {
      assertFalse(field.description().isBlank(),
        "Description for " + field.fieldName() + " must not be blank");
    }
  }

  @Test
  void siteFields_fieldNamesAreUnique() {
    long distinctCount = FIELDS.stream()
      .map(SolarApiFields.FieldDescriptor::fieldName)
      .distinct()
      .count();
    assertEquals(FIELDS.size(), distinctCount, "Field names must be unique");
  }

  @Test
  void solarApiModelId_sentinelValue_isUsedConsistently() {
    // Verify the sentinel used in the pipeline matches the constant
    assertEquals(-1, SunSpecConstants.MODEL_ID_SOLAR_API);
    assertTrue(SunSpecConstants.isSolarApiModel(SunSpecConstants.MODEL_ID_SOLAR_API));
  }
}
