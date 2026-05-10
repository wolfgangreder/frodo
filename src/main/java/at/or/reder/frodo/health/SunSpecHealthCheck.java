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

package at.or.reder.frodo.health;

import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Readiness health check for SunSpec protocol support.
 *
 * <p>Evaluates SunSpec discovery cache status to determine whether
 * SunSpec-capable devices are available and responsive.</p>
 *
 * <p><b>Health Criteria (from Stage 6 plan):</b></p>
 * <ul>
 *   <li>DOWN if discovery is required but no device supports SunSpec (no cached discoveries)</li>
 *   <li>DOWN if all cached discoveries have expired beyond {@code max-cache-age-hours}</li>
 *   <li>UP if at least one device has a valid (non-expired) SunSpec discovery</li>
 *   <li>UP (degraded) if discovery is not required and no devices have been discovered</li>
 * </ul>
 *
 * <p><b>Protocol References:</b> Fronius Gen24 register maps in
 * {@code refdoc/gen24-modbus-api-external-docs/}</p>
 */
@Readiness
@ApplicationScoped
public class SunSpecHealthCheck implements HealthCheck {

  private static final Logger LOG = Logger.getLogger(SunSpecHealthCheck.class);
  private static final String HEALTH_CHECK_NAME = "sunspec-discovery";

  @Inject
  SunSpecService sunSpecService;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "false")
  boolean modbusEnabled;

  @ConfigProperty(name = "frodo.sunspec.health.discovery-required", defaultValue = "false")
  boolean discoveryRequired;

  @ConfigProperty(name = "frodo.sunspec.health.max-cache-age-hours", defaultValue = "24")
  int maxCacheAgeHours;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named(HEALTH_CHECK_NAME);

    if (!modbusEnabled) {
      LOG.debugf("SunSpec health check: Modbus disabled");
      return builder.up()
        .withData("reason", "Modbus is disabled, SunSpec check skipped")
        .withData("modbus.enabled", false)
        .build();
    }

    int cacheSize = sunSpecService.getDiscoveryCacheSize();
    Set<String> cachedDeviceKeys = sunSpecService.getCachedDeviceKeys();

    builder.withData("modbus.enabled", true)
      .withData("discovery.cached.count", cacheSize)
      .withData("discovery.required", discoveryRequired);

    // No discoveries cached
    if (cacheSize == 0) {
      if (discoveryRequired) {
        LOG.debugf("SunSpec health check: DOWN - discovery required but no cached discoveries");
        return builder.down()
          .withData("reason", "No SunSpec device discovered (discovery is required)")
          .build();
      }
      LOG.debugf("SunSpec health check: UP - no discoveries cached, but not required");
      return builder.up()
        .withData("reason", "No SunSpec discoveries cached (not required)")
        .build();
    }

    // Check cache age for each device
    int validCount = 0;
    int expiredCount = 0;
    Duration maxAge = Duration.ofHours(maxCacheAgeHours);

    for (String deviceKey : cachedDeviceKeys) {
      SunSpecDiscoveryResult discovery = sunSpecService.getCachedDiscovery(deviceKey)
        .orElse(null);
      if (discovery != null) {
        Duration age = Duration.between(discovery.discoveryTime(), Instant.now());
        long ageHours = age.toHours();

        builder.withData("device." + deviceKey + ".models", discovery.modelCount())
          .withData("device." + deviceKey + ".age.hours", ageHours);

        if (age.compareTo(maxAge) > 0) {
          expiredCount++;
          builder.withData("device." + deviceKey + ".expired", true);
        } else {
          validCount++;
          builder.withData("device." + deviceKey + ".expired", false);
        }
      }
    }

    builder.withData("discovery.valid.count", validCount)
      .withData("discovery.expired.count", expiredCount);

    // All discoveries expired
    if (validCount == 0 && expiredCount > 0) {
      LOG.debugf("SunSpec health check: DOWN - all %d cached discoveries expired", expiredCount);
      return builder.down()
        .withData("reason", String.format("All %d SunSpec discoveries expired (max age: %d hours)",
          expiredCount, maxCacheAgeHours))
        .build();
    }

    LOG.debugf("SunSpec health check: UP (valid=%d, expired=%d)", validCount, expiredCount);
    return builder.up().build();
  }
}
