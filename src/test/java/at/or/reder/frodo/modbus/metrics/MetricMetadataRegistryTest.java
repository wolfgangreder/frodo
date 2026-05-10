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

package at.or.reder.frodo.modbus.metrics;

import at.or.reder.frodo.modbus.metrics.MetricMetadata.ResolvedMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricMetadataRegistry}.
 *
 * <p>Loads the real {@code metrics-semantic-mapping.json} from the
 * classpath and verifies resolution of field mappings to semantic
 * metric names and tags.</p>
 */
class MetricMetadataRegistryTest {

  private MetricMetadataRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new MetricMetadataRegistry();
    registry.loadMapping();
  }

  @Test
  void testLoadingSucceeds() {
    assertTrue(registry.isLoaded(), "Registry should be loaded");
    assertFalse(registry.getAllMetrics().isEmpty(), "Should have metric definitions");
  }

  @Test
  void testAllMetricsHaveRequiredFields() {
    for (MetricMetadata metric : registry.getAllMetrics()) {
      assertNotNull(metric.metricName(), "metricName must not be null");
      assertNotNull(metric.semanticName(), "semanticName must not be null");
      assertNotNull(metric.description(), "description must not be null");
      assertNotNull(metric.type(), "type must not be null");
      assertNotNull(metric.category(), "category must not be null");
      assertFalse(metric.fields().isEmpty(), "fields must not be empty for " + metric.metricName());

      // Metric names must follow Prometheus conventions
      assertTrue(metric.metricName().startsWith("frodo_sunspec_"),
        "Metric name should start with frodo_sunspec_: " + metric.metricName());
      assertTrue(metric.metricName().matches("[a-z][a-z0-9_]*"),
        "Metric name should be lowercase with underscores: " + metric.metricName());
    }
  }

  // ========== Inverter model field resolution ==========

  @Test
  void testResolveAcPower() {
    Optional<ResolvedMetric> resolved = registry.resolve(113, "W");
    assertTrue(resolved.isPresent(), "Should resolve model 113 field W");
    assertEquals("frodo_sunspec_ac_power_watts", resolved.get().metricName());
    assertTrue(resolved.get().tags().isEmpty(), "AC power should have no extra tags");
  }

  @Test
  void testResolveAcPowerMultipleModels() {
    // All inverter models (101-103, 111-113) should resolve to the same metric
    for (int modelId : List.of(101, 102, 103, 111, 112, 113)) {
      Optional<ResolvedMetric> resolved = registry.resolve(modelId, "W");
      assertTrue(resolved.isPresent(), "Should resolve model " + modelId + " field W");
      assertEquals("frodo_sunspec_ac_power_watts", resolved.get().metricName());
    }
  }

  @Test
  void testResolveAcPhaseCurrentWithTags() {
    Optional<ResolvedMetric> phaseA = registry.resolve(113, "AphA");
    assertTrue(phaseA.isPresent());
    assertEquals("frodo_sunspec_ac_phase_current_amperes", phaseA.get().metricName());
    assertEquals(Map.of("phase", "A"), phaseA.get().tags());

    Optional<ResolvedMetric> phaseB = registry.resolve(113, "AphB");
    assertTrue(phaseB.isPresent());
    assertEquals("frodo_sunspec_ac_phase_current_amperes", phaseB.get().metricName());
    assertEquals(Map.of("phase", "B"), phaseB.get().tags());

    Optional<ResolvedMetric> phaseC = registry.resolve(113, "AphC");
    assertTrue(phaseC.isPresent());
    assertEquals("frodo_sunspec_ac_phase_current_amperes", phaseC.get().metricName());
    assertEquals(Map.of("phase", "C"), phaseC.get().tags());
  }

  @Test
  void testResolveAcFrequency() {
    Optional<ResolvedMetric> resolved = registry.resolve(111, "Hz");
    assertTrue(resolved.isPresent());
    assertEquals("frodo_sunspec_ac_frequency_hertz", resolved.get().metricName());
    assertTrue(resolved.get().tags().isEmpty());
  }

  @Test
  void testResolveTemperatureWithLocationTag() {
    Optional<ResolvedMetric> cabinet = registry.resolve(113, "TmpCab");
    assertTrue(cabinet.isPresent());
    assertEquals("frodo_sunspec_temperature_celsius", cabinet.get().metricName());
    assertEquals(Map.of("location", "cabinet"), cabinet.get().tags());

    Optional<ResolvedMetric> heatsink = registry.resolve(113, "TmpSnk");
    assertTrue(heatsink.isPresent());
    assertEquals("frodo_sunspec_temperature_celsius", heatsink.get().metricName());
    assertEquals(Map.of("location", "heatsink"), heatsink.get().tags());
  }

  // ========== MPPT model 160 with channel tags ==========

  @Test
  void testResolveMpptDcCurrentWithChannel() {
    Optional<ResolvedMetric> ch1 = registry.resolve(160, "module/1/DCA");
    assertTrue(ch1.isPresent(), "Should resolve MPPT channel 1 DC current");
    assertEquals("frodo_sunspec_mppt_dc_current_amperes", ch1.get().metricName());
    assertEquals(Map.of("channel", "1"), ch1.get().tags());

    Optional<ResolvedMetric> ch2 = registry.resolve(160, "module/2/DCA");
    assertTrue(ch2.isPresent(), "Should resolve MPPT channel 2 DC current");
    assertEquals("frodo_sunspec_mppt_dc_current_amperes", ch2.get().metricName());
    assertEquals(Map.of("channel", "2"), ch2.get().tags());
  }

  @Test
  void testResolveMpptDcPowerWithChannel() {
    Optional<ResolvedMetric> ch1 = registry.resolve(160, "module/1/DCW");
    assertTrue(ch1.isPresent());
    assertEquals("frodo_sunspec_mppt_dc_power_watts", ch1.get().metricName());
    assertEquals(Map.of("channel", "1"), ch1.get().tags());
  }

  @Test
  void testResolveMpptTemperatureWithChannel() {
    Optional<ResolvedMetric> ch1 = registry.resolve(160, "module/1/Tmp");
    assertTrue(ch1.isPresent());
    assertEquals("frodo_sunspec_mppt_temperature_celsius", ch1.get().metricName());
    assertEquals(Map.of("channel", "1"), ch1.get().tags());
  }

  // ========== DC from inverter models (no channel tag) ==========

  @Test
  void testResolveDcCurrentFromInverterModel() {
    Optional<ResolvedMetric> resolved = registry.resolve(113, "DCA");
    assertTrue(resolved.isPresent());
    assertEquals("frodo_sunspec_dc_current_amperes", resolved.get().metricName());
    assertTrue(resolved.get().tags().isEmpty(),
      "DC current from inverter model should have no channel tag");
  }

  // ========== Nameplate model 120 ==========

  @Test
  void testResolveNameplateRating() {
    Optional<ResolvedMetric> resolved = registry.resolve(120, "WRtg");
    assertTrue(resolved.isPresent());
    assertEquals("frodo_sunspec_rating_power_watts", resolved.get().metricName());
  }

  @Test
  void testResolveReactivePowerRatingWithQuadrant() {
    Optional<ResolvedMetric> q1 = registry.resolve(120, "VArRtgQ1");
    assertTrue(q1.isPresent());
    assertEquals("frodo_sunspec_rating_reactive_power_vars", q1.get().metricName());
    assertEquals(Map.of("quadrant", "1"), q1.get().tags());
  }

  // ========== Battery model 124 ==========

  @Test
  void testResolveBatteryChargeState() {
    Optional<ResolvedMetric> resolved = registry.resolve(124, "ChaState");
    assertTrue(resolved.isPresent());
    assertEquals("frodo_sunspec_battery_charge_state_ratio", resolved.get().metricName());
  }

  // ========== Unknown field ==========

  @Test
  void testResolveUnknownField() {
    Optional<ResolvedMetric> resolved = registry.resolve(999, "UnknownField");
    assertTrue(resolved.isEmpty(), "Unknown field should return empty");
  }

  @Test
  void testResolveKnownModelUnknownField() {
    Optional<ResolvedMetric> resolved = registry.resolve(113, "NotARealField");
    assertTrue(resolved.isEmpty(), "Unknown field in known model should return empty");
  }

  // ========== Line voltage tags ==========

  @Test
  void testResolveLineVoltageWithLineTags() {
    Optional<ResolvedMetric> ab = registry.resolve(113, "PPVphAB");
    assertTrue(ab.isPresent());
    assertEquals("frodo_sunspec_ac_voltage_line_volts", ab.get().metricName());
    assertEquals(Map.of("line", "AB"), ab.get().tags());
  }

  // ========== MPPT DC voltage (split from inverter) ==========

  @Test
  void testResolveMpptDcVoltageWithChannel() {
    Optional<ResolvedMetric> ch1 = registry.resolve(160, "module/1/DCV");
    assertTrue(ch1.isPresent());
    assertEquals("frodo_sunspec_mppt_dc_voltage_volts", ch1.get().metricName());
    assertEquals(Map.of("channel", "1"), ch1.get().tags());
  }

  @Test
  void testResolveDcVoltageFromInverterModel() {
    Optional<ResolvedMetric> resolved = registry.resolve(113, "DCV");
    assertTrue(resolved.isPresent());
    assertEquals("frodo_sunspec_dc_voltage_volts", resolved.get().metricName());
    assertTrue(resolved.get().tags().isEmpty(),
      "DC voltage from inverter model should have no channel tag");
  }

  // ========== Tag key consistency validation ==========

  @Test
  void testTagKeyConsistencyPassesForValidMapping() {
    // The real mapping should pass validation (no exception during setUp)
    assertTrue(registry.isLoaded(),
      "Registry should load without tag key consistency errors");
  }

  @Test
  void testAllMetricsHaveConsistentTagKeys() {
    // Verify programmatically that each metric's fields all have the same tag key set
    for (MetricMetadata metric : registry.getAllMetrics()) {
      java.util.Set<String> expectedKeys = null;
      for (MetricMetadata.FieldMapping fm : metric.fields()) {
        java.util.Set<String> keys = new java.util.TreeSet<>(fm.tags().keySet());
        if (expectedKeys == null) {
          expectedKeys = keys;
        } else {
          assertEquals(expectedKeys, keys,
            "Metric '" + metric.metricName() + "' field '" + fm.field() +
              "' has different tag keys than other fields in the same metric");
        }
      }
    }
  }
}
