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

package at.or.reder.frodo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class FrodoVersionTest {

  @Inject
  FrodoVersion frodoVersion;

  @Test
  void testVersionLoaded() {
    String version = frodoVersion.getVersion();
    assertNotNull(version);
    assertNotEquals("unknown", version);
  }

  @Test
  void testVersionFormat() {
    String version = frodoVersion.getVersion();
    // Should match pattern like "1.0.0-SNAPSHOT" or "1.0.0"
    assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), 
      "Version should match semantic versioning: " + version);
  }
}
