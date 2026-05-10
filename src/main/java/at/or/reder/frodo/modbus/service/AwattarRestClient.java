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

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.service.model.MarketDataResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client interface for aWATTar AT market data API.
 *
 * <p>This interface defines the aWATTar API endpoints using standard JAX-RS
 * annotations. Uses the classic (blocking) REST client backed by
 * {@code quarkus-rest-client-jackson}.</p>
 *
 * <p><b>Base URL:</b> {@code https://api.awattar.at}</p>
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>{@code GET /v1/marketdata} - Fetch hourly market prices</li>
 * </ul>
 *
 * <p><b>Reference:</b> {@code https://www.awattar.at/services/api}</p>
 *
 * @see AwattarClient
 */
@Path("/v1")
@RegisterRestClient(configKey = "awattar-api")
public interface AwattarRestClient {

  /**
   * Fetches market data for the next 24 hours.
   *
   * <p><b>Endpoint:</b> {@code GET /v1/marketdata}</p>
   *
   * @return market price list
   */
  @GET
  @Path("/marketdata")
  @Produces(MediaType.APPLICATION_JSON)
  MarketDataResponse getMarketData();

  /**
   * Fetches market data for a specific time window.
   *
   * <p><b>Endpoint:</b> {@code GET /v1/marketdata?start={start}&end={end}}</p>
   *
   * @param start start time in epoch milliseconds
   * @param end   end time in epoch milliseconds
   * @return market price list
   */
  @GET
  @Path("/marketdata")
  @Produces(MediaType.APPLICATION_JSON)
  MarketDataResponse getMarketData(
    @QueryParam("start") long start,
    @QueryParam("end") long end
  );
}
