package at.or.reder.frodo.solarapi;

import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import at.or.reder.frodo.solarapi.model.SolarApiResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client interface for Fronius Solar API.
 *
 * <p>This interface defines the Solar API endpoints using standard JAX-RS
 * annotations. Quarkus generates the implementation at build time.</p>
 *
 * <p><b>Base URL:</b> Configured via {@code quarkus.rest-client.solar-api.url}</p>
 *
 * <p><b>Key Endpoints:</b></p>
 * <ul>
 *   <li>{@code GetPowerFlowRealtimeData.fcgi} - Unified power flow data (all devices)</li>
 * </ul>
 *
 * <p><b>API Reference:</b> {@code refdoc/solar_api.pdf}</p>
 *
 * @see SolarApiClient
 * @see PowerFlowRealtimeData
 */
@Path("/solar_api/v1")
@RegisterRestClient(configKey = "solar-api")
public interface SolarApiRestClient {

  /**
   * Fetches power flow realtime data from the Solar API.
   *
   * <p>Returns unified data for all devices: inverters, site metrics,
   * Ohmpilot devices, and secondary meters.</p>
   *
   * <p><b>Endpoint:</b> {@code GET /solar_api/v1/GetPowerFlowRealtimeData.fcgi}</p>
   *
   * @return async response with power flow data
   */
  @GET
  @Path("/GetPowerFlowRealtimeData.fcgi")
  @Produces(MediaType.APPLICATION_JSON)
  Uni<SolarApiResponse<PowerFlowRealtimeData>> getPowerFlowRealtimeData();
}
