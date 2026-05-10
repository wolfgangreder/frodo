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

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides access to the Frodo application version.
 * Version is read from version.properties which is generated during build.
 */
@ApplicationScoped
public class FrodoVersion {

  private static final Logger LOG = Logger.getLogger(FrodoVersion.class);
  private static final String VERSION_FILE = "/version.properties";
  private static final String UNKNOWN_VERSION = "unknown";

  private final String version;

  /**
   * Initializes and loads the version from version.properties.
   */
  public FrodoVersion() {
    this.version = loadVersion();
  }

  /**
   * Returns the application version.
   *
   * @return version string (e.g., "1.0.0-SNAPSHOT")
   */
  public String getVersion() {
    return version;
  }

  /**
   * Loads version from version.properties file.
   *
   * @return version string or "unknown" if file cannot be read
   */
  private String loadVersion() {
    try (InputStream input = getClass().getResourceAsStream(VERSION_FILE)) {
      if (input == null) {
        LOG.warnf("Version file not found: %s", VERSION_FILE);
        return UNKNOWN_VERSION;
      }

      Properties props = new Properties();
      props.load(input);
      String ver = props.getProperty("version", UNKNOWN_VERSION);
      LOG.infof("Loaded application version: %s", ver);
      return ver;
    } catch (IOException e) {
      LOG.errorf(e, "Failed to load version from %s", VERSION_FILE);
      return UNKNOWN_VERSION;
    }
  }
}
