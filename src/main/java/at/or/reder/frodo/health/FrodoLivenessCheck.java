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

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness health check for the frodo application.
 *
 * <p>Answers the single question the kubelet liveness probe needs:
 * <em>"Is the JVM still running and able to handle HTTP requests?"</em></p>
 *
 * <p><strong>This check must never touch the database or any external
 * system.</strong> Database connectivity is a <em>readiness</em> concern.
 * When the Firebird database is unreachable, the readiness probe reports
 * DOWN (stopping new traffic), but this check stays UP so the kubelet
 * does not kill and restart the pod. Restarting does not help when the DB
 * is down — it only adds JVM startup pressure and risks a crash-loop.</p>
 *
 * <p>Exposed at: {@code GET /frodo/q/health/live}</p>
 */
@Liveness
@ApplicationScoped
public class FrodoLivenessCheck implements HealthCheck {

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("frodo-liveness")
      .up()
      .build();
  }
}
