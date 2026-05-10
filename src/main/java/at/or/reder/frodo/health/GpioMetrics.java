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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Registers Micrometer gauges for GPIO export control.
 *
 * <p>One set of gauges per configured pair, tagged with {@code pair=<name>}.
 * Gauges return {@code -1} when a pair is unavailable.</p>
 */
@ApplicationScoped
public class GpioMetrics {

  @Inject
  GpioService gpioService;

  @Inject
  GpioConfig gpioConfig;

  @Inject
  void registerMetrics(MeterRegistry registry) {
    if (!gpioConfig.enabled()) {
      return;
    }

    // System-level gauge
    Gauge.builder("frodo_gpio_available", gpioService,
        s -> s.isAvailable() ? 1.0 : 0.0)
      .description("GPIO system availability")
      .register(registry);

    // Per-pair gauges
    for (String pairName : gpioConfig.pairs().keySet()) {
      String tag = pairName;

      Gauge.builder("frodo_gpio_pair_output_state", gpioService, s ->
          s.getStatus().pairs().stream()
            .filter(p -> p.name().equals(tag)).findFirst()
            .map(p -> p.outputPinState() == null ? -1.0 : (p.outputPinState() ? 1.0 : 0.0))
            .orElse(-1.0))
        .description("GPIO output pin state (1=HIGH, 0=LOW, -1=unavailable)")
        .tag("pair", pairName)
        .register(registry);

      Gauge.builder("frodo_gpio_pair_input_state", gpioService, s ->
          s.getStatus().pairs().stream()
            .filter(p -> p.name().equals(tag)).findFirst()
            .map(p -> p.inputPinState() == null ? -1.0 : (p.inputPinState() ? 1.0 : 0.0))
            .orElse(-1.0))
        .description("GPIO input pin state (1=HIGH, 0=LOW, -1=unavailable)")
        .tag("pair", pairName)
        .register(registry);

      Gauge.builder("frodo_gpio_pair_external_mode", gpioService, s ->
          s.getStatus().pairs().stream()
            .filter(p -> p.name().equals(tag)).findFirst()
            .map(p -> p.externalModeActive() ? 1.0 : 0.0)
            .orElse(0.0))
        .description("External override switch active")
        .tag("pair", pairName)
        .register(registry);

      Gauge.builder("frodo_gpio_pair_manual_override", gpioService, s ->
          s.getStatus().pairs().stream()
            .filter(p -> p.name().equals(tag)).findFirst()
            .map(p -> p.outputManualOverride() ? 1.0 : 0.0)
            .orElse(0.0))
        .description("Manual test override active")
        .tag("pair", pairName)
        .register(registry);
    }
  }
}
