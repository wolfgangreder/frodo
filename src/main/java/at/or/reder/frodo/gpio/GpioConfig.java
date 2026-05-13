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

package at.or.reder.frodo.gpio;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;

/**
 * Configuration for GPIO-based export control on Raspberry Pi.
 *
 * <p>GPIO pairs are named by arbitrary keys in {@code application.properties}:
 * <pre>
 *   frodo.gpio.pairs.relay1.output-pin=17
 *   frodo.gpio.pairs.relay1.input-pin=27
 *   frodo.gpio.pairs.relay2.output-pin=22
 *   frodo.gpio.pairs.relay2.input-pin=23
 * </pre>
 *
 * <p>SmallRye Config maps these to environment variables as
 * {@code FRODO_GPIO_PAIRS_RELAY1_OUTPUT_PIN}, etc.</p>
 */
@ConfigMapping(prefix = "frodo.gpio")
public interface GpioConfig {

  @WithDefault("false")
  boolean enabled();

  @WithDefault("/dev/gpiochip0")
  String chipDevice();

  @WithDefault("frodo-export-control")
  String consumerLabel();

  Map<String, GpioPairConfig> pairs();

  interface GpioPairConfig {

    int outputPin();

    @WithDefault("HIGH")
    String outputBlockLevel();

    int inputPin();

    @WithDefault("HIGH")
    String inputActiveLevel();

    @WithDefault("PULL_DOWN")
    String inputBias();
  }
}
