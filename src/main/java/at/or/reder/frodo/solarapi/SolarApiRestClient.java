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
