# Liveness / Readiness Probe Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the kubelet from restarting the frodo pod when the Firebird database is temporarily unreachable, by ensuring the liveness probe tests only JVM responsiveness and never touches the database.

**Architecture:** Add an explicit `@Liveness`-annotated health check that answers immediately without any DB or external-system access. Demote the existing trivial `FrodoHealthCheck` from `@Readiness` to `@Liveness` (it already does the right thing). Reduce the Agroal connection acquisition timeout so a DB outage stops blocking worker threads within seconds rather than 30 s, preventing the thread starvation that makes even the liveness endpoint time out. Finally, tighten the Helm / kubernetes probe configuration to add an explicit `timeoutSeconds` and raise `failureThreshold` so transient JVM GC pauses don't kill the pod.

**Tech Stack:** Java 25, Quarkus 3.38.x, SmallRye Health (MicroProfile Health 4), Agroal JDBC pool, Helm 3, k3s / Kubernetes.

---

## Background

### Why the pod dies when Firebird goes down

| Step | What happens |
|------|--------------|
| DB unreachable | Agroal tries to validate connections. Each attempt blocks a worker thread for up to `acquisition-timeout` (currently 30 s). |
| Thread exhaustion | Readiness probes pile up. All Vert.x worker threads are waiting on Agroal. |
| Liveness probe times out | The HTTP request to `/frodo/q/health/live` gets no thread to serve it within the default 1-second kubelet timeout. |
| Pod killed | kubelet sees `failureThreshold` (3) consecutive timeouts on the liveness probe and sends SIGKILL. |

### Current health check inventory

| Class | Annotation | Touches DB? |
|-------|-----------|-------------|
| `FrodoHealthCheck` | `@Readiness` | No — returns UP unconditionally |
| `ModbusHealthCheck` | `@Readiness` | Yes — `@Transactional`, calls `ModbusDeviceRepository` |
| `SunSpecHealthCheck` | `@Readiness` | No — reads in-memory cache |
| `CostControlHealthCheck` | `@Readiness` | Yes — calls `HourlyCostRepository`, `EnergyPriceRepository` |
| `GpioHealthCheck` | `@Readiness` | No — calls `GpioService` |
| `SolarApiHealthCheck` | `@Readiness` | No — calls HTTP client |
| Agroal auto-check | `@Readiness` (auto) | Yes — validates JDBC pool |
| *(none)* | `@Liveness` | — |

`/q/health/live` has no `@Liveness` checks today. SmallRye returns an implicit UP, which is correct in theory — but thread exhaustion means the HTTP server never sends that response in time.

### The fix (two parts)

1. **Explicit `@Liveness` check** — `FrodoLivenessCheck` replaces `FrodoHealthCheck`, annotated `@Liveness`. Returns UP instantly. This documents intent and ensures a fast path even if SmallRye's implicit logic ever changes.

2. **Reduce acquisition timeout** — `quarkus.datasource.jdbc.acquisition-timeout` from 30 s to 5 s. Threads fail fast, freeing them to serve HTTP. The DB is still reported DOWN in readiness, but the pod stays alive.

---

## File Map

| Action | Path |
|--------|------|
| **Rename / modify** | `src/main/java/at/or/reder/frodo/health/FrodoHealthCheck.java` → `FrodoLivenessCheck.java` |
| **Delete** | `src/main/java/at/or/reder/frodo/health/FrodoHealthCheck.java` |
| **Modify** | `src/main/resources/application.properties` |
| **Modify** | `helm/frodo/values.yaml` |
| **Modify** | `kubernetes/deployment.yaml` |
| **Create** | `src/test/java/at/or/reder/frodo/health/FrodoLivenessCheckTest.java` |
| **Delete** | `src/test/java/at/or/reder/frodo/health/FrodoHealthCheckTest.java` *(if exists)* |

---

## Task 1 — Create `FrodoLivenessCheck` and remove `FrodoHealthCheck`

**Files:**
- Create: `src/main/java/at/or/reder/frodo/health/FrodoLivenessCheck.java`
- Delete: `src/main/java/at/or/reder/frodo/health/FrodoHealthCheck.java`

- [ ] **Step 1.1 — Write the failing test**

Create `src/test/java/at/or/reder/frodo/health/FrodoLivenessCheckTest.java`:

```java
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
```

- [ ] **Step 1.2 — Run the test to confirm it fails**

```bash
./gradlew test --tests "at.or.reder.frodo.health.FrodoLivenessCheckTest" 2>&1 | tail -20
```

Expected: compilation error — `FrodoLivenessCheck` does not exist yet.

- [ ] **Step 1.3 — Create `FrodoLivenessCheck`**

Create `src/main/java/at/or/reder/frodo/health/FrodoLivenessCheck.java`:

```java
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
```

- [ ] **Step 1.4 — Delete the old `FrodoHealthCheck`**

```bash
rm src/main/java/at/or/reder/frodo/health/FrodoHealthCheck.java
```

If a test file `src/test/java/at/or/reder/frodo/health/FrodoHealthCheckTest.java` exists, delete it too:

```bash
rm -f src/test/java/at/or/reder/frodo/health/FrodoHealthCheckTest.java
```

- [ ] **Step 1.5 — Run the test to confirm it passes**

```bash
./gradlew test --tests "at.or.reder.frodo.health.FrodoLivenessCheckTest" 2>&1 | tail -20
```

Expected:
```
BUILD SUCCESSFUL
FrodoLivenessCheckTest > alwaysReturnsUp() PASSED
FrodoLivenessCheckTest > checkNameIsLiveness() PASSED
```

- [ ] **Step 1.6 — Run the full test suite**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, zero test failures.

- [ ] **Step 1.7 — Commit**

```bash
git add src/main/java/at/or/reder/frodo/health/FrodoLivenessCheck.java \
        src/test/java/at/or/reder/frodo/health/FrodoLivenessCheckTest.java
git rm src/main/java/at/or/reder/frodo/health/FrodoHealthCheck.java
git rm -f src/test/java/at/or/reder/frodo/health/FrodoHealthCheckTest.java 2>/dev/null || true
git commit -m "feat(health): add @Liveness check, remove trivial @Readiness FrodoHealthCheck

The kubelet liveness probe must never depend on database reachability.
FrodoLivenessCheck is @Liveness and always returns UP, proving the JVM
can handle HTTP. FrodoHealthCheck (trivial @Readiness UP) is removed —
it added nothing to readiness that the other checks don't already cover."
```

---

## Task 2 — Reduce Agroal acquisition timeout

**Files:**
- Modify: `src/main/resources/application.properties`

**Why:** With `acquisition-timeout=30`, each failed DB connection attempt blocks a worker thread for 30 seconds. Three concurrent health-check calls exhaust the small Vert.x worker pool, making the liveness endpoint unresponsive. Dropping to 5 s means threads are freed within one probe `periodSeconds` interval.

- [ ] **Step 2.1 — Update `application.properties`**

Change the existing line:

```
quarkus.datasource.jdbc.acquisition-timeout=30
```

to:

```
quarkus.datasource.jdbc.acquisition-timeout=5
```

Also add the Agroal health check opt-out comment so the intent is clear:

```properties
# Acquisition timeout kept short so a DB outage does not exhaust worker
# threads. The readiness probe will report DOWN; the liveness probe stays UP.
quarkus.datasource.jdbc.acquisition-timeout=5
```

- [ ] **Step 2.2 — Run the full test suite**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL` (the timeout change has no effect in tests because `%test.quarkus.datasource.active=false`).

- [ ] **Step 2.3 — Commit**

```bash
git add src/main/resources/application.properties
git commit -m "fix(datasource): reduce acquisition-timeout from 30s to 5s

A 30-second acquisition timeout means each failed DB connection attempt
blocks a Vert.x worker thread for 30s. When Firebird is unreachable,
concurrent health checks pile up, the thread pool is exhausted, and the
liveness endpoint stops responding — triggering a pod restart that cannot
possibly fix the DB outage.

5s is long enough for normal latency variation and short enough to keep
the server responsive during an outage."
```

---

## Task 3 — Update Helm probe configuration

**Files:**
- Modify: `helm/frodo/values.yaml`
- Modify: `kubernetes/deployment.yaml`

**Why:**
- `timeoutSeconds` defaults to 1 second. A single GC pause can exceed 1 s on a Raspberry Pi 5 with 8 GB heap. Adding `timeoutSeconds: 5` gives headroom.
- Raising `failureThreshold` from 3 to 5 means 50 s of consecutive failures are needed before a kill. One slow GC cycle (≤30 s) no longer kills the pod.
- The readiness probe keeps `failureThreshold: 3` (15 s) so traffic stops quickly when the app is genuinely not ready.

- [ ] **Step 3.1 — Update `helm/frodo/values.yaml`**

Replace the `livenessProbe` block:

```yaml
# Before:
livenessProbe:
  httpGet:
    path: /frodo/q/health/live
    port: 8082
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
```

with:

```yaml
# After:
livenessProbe:
  httpGet:
    path: /frodo/q/health/live
    port: 8082
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 5
```

Leave `readinessProbe` unchanged:

```yaml
readinessProbe:
  httpGet:
    path: /frodo/q/health/ready
    port: 8082
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 3
```

- [ ] **Step 3.2 — Update `kubernetes/deployment.yaml`**

In the `livenessProbe` section of `kubernetes/deployment.yaml`, add `timeoutSeconds: 5` and change `failureThreshold: 3` to `failureThreshold: 5`:

```yaml
          livenessProbe:
            httpGet:
              path: /frodo/q/health/live
              port: 8082
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 5
          readinessProbe:
            httpGet:
              path: /frodo/q/health/ready
              port: 8082
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 3
```

- [ ] **Step 3.3 — Regenerate `frodo.yaml` in `automatix_env`**

If `helm/frodo` is the chart source for the deployed `frodo.yaml` in `../automatix_env/`:

```bash
helm template frodo helm/frodo \
  -f ../automatix_env/frodo-config.yaml \
  > ../automatix_env/frodo.yaml
```

Verify the generated YAML contains `timeoutSeconds: 5` and `failureThreshold: 5`:

```bash
grep -A8 "livenessProbe" ../automatix_env/frodo.yaml
```

Expected output:
```yaml
          livenessProbe:
            failureThreshold: 5
            httpGet:
              path: /frodo/q/health/live
              port: 8082
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
```

- [ ] **Step 3.4 — Commit**

```bash
git add helm/frodo/values.yaml kubernetes/deployment.yaml
git commit -m "fix(k8s): liveness probe — add timeoutSeconds=5, failureThreshold=5

The default 1s timeout is too tight for a JVM on a Raspberry Pi under
GC pressure. 5s gives headroom without masking a genuine hang. Raising
failureThreshold to 5 (50s total) means one slow GC burst does not kill
the pod, while still catching a genuinely stuck process within a minute."
```

```bash
# In automatix_env repo (separate commit there):
cd ../automatix_env
git add frodo.yaml
git commit -m "fix(frodo): regenerate after liveness probe tuning"
```

---

## Verification

After deploying to k3s, confirm the separation works:

```bash
# Check liveness endpoint directly (should return UP even during DB outage)
kubectl exec -n default <frodo-pod> -- \
  wget -qO- http://localhost:8082/frodo/q/health/live | python3 -m json.tool

# Expected:
# { "status": "UP", "checks": [{"name": "frodo-liveness", "status": "UP"}] }

# Check readiness endpoint (should return DOWN when DB is unreachable)
kubectl exec -n default <frodo-pod> -- \
  wget -qO- http://localhost:8082/frodo/q/health/ready | python3 -m json.tool

# Expected when DB is down:
# { "status": "DOWN", "checks": [..., {"name": "...", "status": "DOWN"}] }
```

To simulate a DB outage:

```bash
# On automatix host — temporarily stop Firebird
sudo systemctl stop firebird3.0

# Watch frodo pod — it should stay Running, not be restarted
kubectl get pods -n default -w | grep frodo

# Restart Firebird
sudo systemctl start firebird3.0
```

After the fix, the frodo pod should stay `Running` throughout. Readiness will flip to `0/1` while the DB is down (no new traffic) then recover. Restart count must not increase.

---

## Self-Review Checklist

- [x] **Spec coverage**: All three root issues addressed — explicit liveness, thread starvation, probe tuning.
- [x] **No placeholders**: All code blocks are complete and compilable.
- [x] **Type consistency**: `FrodoLivenessCheck` used consistently in test and source.
- [x] **automatix_env**: `frodo.yaml` regeneration included in Task 3.
- [x] **Existing tests**: Full test suite run after each task; no regressions expected since `FrodoHealthCheck` had no tests.
