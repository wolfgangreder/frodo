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

import io.smallrye.health.checks.UrlHealthCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Health check for Fronius Solar API availability.
 *
 * <p>Monitors the Solar API endpoint and reports readiness status.
 * If Solar API is disabled, the health check reports UP with a note.</p>
 *
 * <p><b>Health Endpoint:</b> {@code GET /q/health/ready}</p>
 *
 * @see SolarApiClient
 */
@Readiness
@ApplicationScoped
public class SolarApiHealthCheck implements HealthCheck {

  @Inject
  SolarApiClient solarApiClient;

  @ConfigProperty(name = "frodo.solar-api.enabled", defaultValue = "false")
  boolean solarApiEnabled;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named("Fronius Solar API");

    if (!solarApiEnabled) {
      return builder
        .up()
        .withData("enabled", false)
        .withData("note", "Solar API integration is disabled")
        .build();
    }

    try {
      boolean healthy = solarApiClient.checkHealth().await().indefinitely();

      if (healthy) {
        return builder
          .up()
          .withData("enabled", true)
          .withData("baseUrl", solarApiClient.getBaseUrl())
          .withData("status", "reachable")
          .build();
      } else {
        return builder
          .down()
          .withData("enabled", true)
          .withData("baseUrl", solarApiClient.getBaseUrl())
          .withData("status", "unreachable or error response")
          .build();
      }
    } catch (Exception e) {
      return builder
        .down()
        .withData("enabled", true)
        .withData("baseUrl", solarApiClient.getBaseUrl())
        .withData("status", "error")
        .withData("error", e.getMessage())
        .build();
    }
  }
}
