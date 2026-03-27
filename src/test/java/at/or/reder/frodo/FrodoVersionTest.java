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
