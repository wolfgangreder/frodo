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

import at.or.reder.frodo.gpio.GpioConfig;
import at.or.reder.frodo.gpio.GpioPairStatus;
import at.or.reder.frodo.gpio.GpioService;
import at.or.reder.frodo.gpio.GpioStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness health check for GPIO export control.
 *
 * <p>Reports UP when GPIO is disabled (not required) or when all
 * configured pairs are initialised. Reports DOWN when GPIO is enabled
 * but the system or any pair failed to initialise.</p>
 */
@Readiness
@ApplicationScoped
public class GpioHealthCheck implements HealthCheck {

  @Inject
  GpioService gpioService;

  @Inject
  GpioConfig gpioConfig;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named("GPIO Export Control");

    if (!gpioConfig.enabled()) {
      return builder.up().withData("enabled", false).build();
    }

    GpioStatus status = gpioService.getStatus();
    builder.withData("enabled", true)
      .withData("platform", status.platform())
      .withData("isRaspberryPi", status.isRaspberryPi())
      .withData("pairs", status.pairs().size());

    long unavailable = status.pairs().stream().filter(p -> !p.available()).count();
    if (!status.available() || unavailable > 0) {
      builder.down()
        .withData("unavailablePairs", unavailable)
        .withData("error", status.errorMessage() != null
          ? status.errorMessage() : unavailable + " pair(s) failed to initialise");
    } else {
      builder.up();
      for (GpioPairStatus p : status.pairs()) {
        builder.withData("pair." + p.name() + ".outputPin", p.outputPin());
        builder.withData("pair." + p.name() + ".inputPin", p.inputPin());
        builder.withData("pair." + p.name() + ".externalModeActive", p.externalModeActive());
      }
    }

    return builder.build();
  }
}
