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

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link FrodoLivenessCheck}.
 *
 * <p>The liveness check must never touch the database or any external system.
 * It must always return UP so the kubelet does not kill the pod during a
 * transient DB outage.</p>
 */
class FrodoLivenessCheckTest {

  private FrodoLivenessCheck check;

  @BeforeEach
  void setUp() {
    check = new FrodoLivenessCheck();
  }

  @Test
  void alwaysReturnsUp() {
    HealthCheckResponse response = check.call();

    assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
  }

  @Test
  void checkNameIsLiveness() {
    HealthCheckResponse response = check.call();

    assertEquals("frodo-liveness", response.getName());
  }
}
