package at.or.reder.frodo.solarapi;

import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import at.or.reder.frodo.solarapi.model.SolarApiResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * HTTP client for Fronius Solar API endpoints.
 *
 * <p>Provides async access to Solar API endpoints for device discovery
 * and metrics collection. Uses JAX-RS Client for HTTP communication.</p>
 *
 * <p><b>Base URL:</b> {@code http://{host}:{port}/solar_api/v1/}</p>
 *
 * <p><b>Key Endpoints:</b></p>
 * <ul>
 *   <li>{@code GetPowerFlowRealtimeData.fcgi} - Unified power flow data (all devices)</li>
 *   <li>{@code GetOhmPilotRealtimeData.cgi} - Ohmpilot-specific data</li>
 *   <li>{@code GetActiveDeviceInfo.cgi?DeviceClass={class}} - Device discovery</li>
 * </ul>
 *
 * <p><b>API Reference:</b> {@code refdoc/solar_api.pdf}</p>
 *
 * @see PowerFlowRealtimeData
 */
@ApplicationScoped
public class SolarApiClient {

  private static final Logger LOG = Logger.getLogger(SolarApiClient.class);

  @ConfigProperty(name = "frodo.solar-api.host", defaultValue = "localhost")
  String solarApiHost;

  @ConfigProperty(name = "frodo.solar-api.port", defaultValue = "80")
  int solarApiPort;

  @ConfigProperty(name = "frodo.solar-api.timeout-seconds", defaultValue = "5")
  int timeoutSeconds;

  @ConfigProperty(name = "frodo.solar-api.enabled", defaultValue = "false")
  boolean solarApiEnabled;

  @Inject
  Client httpClient;

  /**
   * Fetches power flow realtime data from the Solar API.
   *
   * <p>Returns unified data for all devices: inverters, site metrics,
   * Ohmpilot devices, and secondary meters.</p>
   *
   * <p><b>Endpoint:</b> {@code GET /solar_api/v1/GetPowerFlowRealtimeData.fcgi}</p>
   *
   * @return async response with power flow data
   * @throws IllegalStateException if Solar API is disabled
   * @throws IOException           if HTTP request fails
   */
  public Uni<SolarApiResponse<PowerFlowRealtimeData>> getPowerFlowRealtimeData() {
    if (!solarApiEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Solar API is disabled (frodo.solar-api.enabled=false)")
      );
    }

    String url = buildUrl("/solar_api/v1/GetPowerFlowRealtimeData.fcgi");
    LOG.debugf("Fetching power flow data from %s", url);

    return Uni.createFrom().item(() -> {
        return httpClient.target(url)
          .request(MediaType.APPLICATION_JSON)
          .get(new GenericType<SolarApiResponse<PowerFlowRealtimeData>>() {
          });
      }).onItem().transform(response -> {
      LOG.debugf("Received power flow data (version: %s, has Ohmpilots: %s)",
        response.getData() != null ? response.getData().getVersion() : "unknown",
        response.getData() != null && response.getData().hasOhmpilots());
      return response;
    }).onFailure().invoke(ex -> {
      LOG.errorf(ex, "Failed to fetch power flow data from %s", url);
    });
  }

  /**
   * Checks if the Solar API is enabled and reachable.
   *
   * <p>Attempts to fetch power flow data and checks for successful response.</p>
   *
   * @return async result indicating Solar API availability
   */
  public Uni<Boolean> checkHealth() {
    if (!solarApiEnabled) {
      return Uni.createFrom().item(false);
    }

    return getPowerFlowRealtimeData()
      .map(response -> response != null && response.isSuccess())
      .onFailure().recoverWithItem(false);
  }

  /**
   * Builds the full URL for a Solar API endpoint.
   *
   * @param path endpoint path (e.g. "/solar_api/v1/GetPowerFlowRealtimeData.fcgi")
   * @return full URL
   */
  private String buildUrl(String path) {
    return String.format("http://%s:%d%s", solarApiHost, solarApiPort, path);
  }

  /**
   * Gets the configured Solar API base URL.
   *
   * @return base URL (e.g. "http://localhost:80")
   */
  public String getBaseUrl() {
    return String.format("http://%s:%d", solarApiHost, solarApiPort);
  }

  /**
   * Checks if Solar API integration is enabled.
   *
   * @return true if enabled via configuration
   */
  public boolean isEnabled() {
    return solarApiEnabled;
  }
}
