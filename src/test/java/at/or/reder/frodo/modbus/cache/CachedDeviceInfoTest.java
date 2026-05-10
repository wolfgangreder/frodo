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

package at.or.reder.frodo.modbus.cache;

import at.or.reder.frodo.modbus.model.DeviceIdentification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CachedDeviceInfo}.
 */
class CachedDeviceInfoTest {

  private static final DeviceIdentification SAMPLE_IDENTIFICATION = new DeviceIdentification(
    "Test Vendor",
    "TEST-123",
    "1.0.0",
    "https://example.com",
    "Test Product",
    "Model X",
    "UserApp",
    Map.of(),
    Instant.now()
  );

  @Test
  void testOf_WithValidTtl_CreatesInstance() {
    Duration ttl = Duration.ofMinutes(60);
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);

    assertNotNull(cached);
    assertEquals(SAMPLE_IDENTIFICATION, cached.identification());
    assertNotNull(cached.cachedAt());
    assertNotNull(cached.expiresAt());
  }

  @Test
  void testOf_WithNegativeTtl_ThrowsException() {
    Duration negativeTtl = Duration.ofMinutes(-1);

    assertThrows(IllegalArgumentException.class, () ->
      CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, negativeTtl)
    );
  }

  @Test
  void testIsExpired_WhenNotExpired_ReturnsFalse() {
    Duration ttl = Duration.ofMinutes(60);
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);

    assertFalse(cached.isExpired());
  }

  @Test
  void testIsExpired_WhenExpired_ReturnsTrue() throws InterruptedException {
    Duration ttl = Duration.ofMillis(50);
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);

    Thread.sleep(100);

    assertTrue(cached.isExpired());
  }

  @Test
  void testAge_ReturnsCorrectDuration() throws InterruptedException {
    Duration ttl = Duration.ofMinutes(60);
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);

    Thread.sleep(100);

    Duration age = cached.age();
    assertTrue(age.toMillis() >= 100, "Age should be at least 100ms");
    assertTrue(age.toMillis() < 200, "Age should be less than 200ms");
  }

  @Test
  void testTimeToExpiration_WhenNotExpired_ReturnsPositiveDuration() {
    Duration ttl = Duration.ofMinutes(60);
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);

    Duration timeToExpiration = cached.timeToExpiration();
    assertTrue(timeToExpiration.toSeconds() > 0, "Time to expiration should be positive");
    assertTrue(timeToExpiration.toMinutes() <= 60, "Time to expiration should be <= 60 minutes");
  }

  @Test
  void testTimeToExpiration_WhenExpired_ReturnsNegativeDuration() throws InterruptedException {
    Duration ttl = Duration.ofMillis(50);
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);

    Thread.sleep(100);

    Duration timeToExpiration = cached.timeToExpiration();
    assertTrue(timeToExpiration.isNegative(), "Time to expiration should be negative when expired");
  }

  @Test
  void testExpiresAt_IsCorrectlyCalculated() {
    Duration ttl = Duration.ofMinutes(60);
    Instant before = Instant.now();
    CachedDeviceInfo cached = CachedDeviceInfo.of(SAMPLE_IDENTIFICATION, ttl);
    Instant after = Instant.now();

    Instant expectedExpiry = before.plus(ttl);
    Instant actualExpiry = cached.expiresAt();

    assertTrue(actualExpiry.isAfter(expectedExpiry) || actualExpiry.equals(expectedExpiry));
    assertTrue(actualExpiry.isBefore(after.plus(ttl).plusMillis(10)));
  }
}
