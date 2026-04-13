package at.or.reder.frodo.solarapi;

import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import at.or.reder.frodo.solarapi.model.SolarApiResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * HTTP client wrapper for Fronius Solar API endpoints.
 *
 * <p>Provides async access to Solar API endpoints for device discovery
 * and metrics collection. Delegates to the MicroProfile REST Client
 * {@link SolarApiRestClient} for HTTP communication.</p>
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
 * @see SolarApiRestClient
 */
@ApplicationScoped
public class SolarApiClient {

  private static final Logger LOG = Logger.getLogger(SolarApiClient.class);

  @ConfigProperty(name = "frodo.solar-api.host", defaultValue = "localhost")
  String solarApiHost;

  @ConfigProperty(name = "frodo.solar-api.port", defaultValue = "80")
  int solarApiPort;

  @ConfigProperty(name = "frodo.solar-api.enabled", defaultValue = "false")
  boolean solarApiEnabled;

  @Inject
  @RestClient
  SolarApiRestClient restClient;

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
   */
  public Uni<SolarApiResponse<PowerFlowRealtimeData>> getPowerFlowRealtimeData() {
    if (!solarApiEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("Solar API is disabled (frodo.solar-api.enabled=false)")
      );
    }

    LOG.debugf("Fetching power flow data from %s", getBaseUrl());

    return restClient.getPowerFlowRealtimeData()
      .onItem().invoke(response -> {
        LOG.debugf("Received power flow data (version: %s, has Ohmpilots: %s)",
          response.getData() != null ? response.getData().getVersion() : "unknown",
          response.getData() != null && response.getData().hasOhmpilots());
      })
      .onFailure().invoke(ex -> {
        LOG.errorf(ex, "Failed to fetch power flow data from %s", getBaseUrl());
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
