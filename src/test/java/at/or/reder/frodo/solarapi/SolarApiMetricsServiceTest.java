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

package at.or.reder.frodo.solarapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for package-private helpers in {@link SolarApiMetricsService}.
 *
 * <p>No CDI / Quarkus wiring needed — tests call static helper directly.</p>
 */
class SolarApiMetricsServiceTest {

  // --- nanToNull ---

  @Test
  void nanToNull_withNaN_returnsNull() {
    assertNull(SolarApiMetricsService.nanToNull(Double.NaN));
  }

  @Test
  void nanToNull_withNull_returnsNull() {
    assertNull(SolarApiMetricsService.nanToNull(null));
  }

  @Test
  void nanToNull_withZero_returnsZero() {
    Double result = SolarApiMetricsService.nanToNull(0.0);
    assertNotNull(result);
    assertEquals(0.0, result);
  }

  @Test
  void nanToNull_withPositiveValue_returnsSameValue() {
    Double result = SolarApiMetricsService.nanToNull(42.5);
    assertNotNull(result);
    assertEquals(42.5, result);
  }

  @Test
  void nanToNull_withNegativeValue_returnsSameValue() {
    Double result = SolarApiMetricsService.nanToNull(-100.0);
    assertNotNull(result);
    assertEquals(-100.0, result);
  }

  @Test
  void nanToNull_withPositiveInfinity_returnsPositiveInfinity() {
    Double result = SolarApiMetricsService.nanToNull(Double.POSITIVE_INFINITY);
    assertNotNull(result);
    assertEquals(Double.POSITIVE_INFINITY, result);
  }

  @Test
  void nanToNull_withNegativeInfinity_returnsNegativeInfinity() {
    Double result = SolarApiMetricsService.nanToNull(Double.NEGATIVE_INFINITY);
    assertNotNull(result);
    assertEquals(Double.NEGATIVE_INFINITY, result);
  }
}
