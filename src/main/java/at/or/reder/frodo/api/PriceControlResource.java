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

package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.PriceControlRequest;
import at.or.reder.frodo.api.dto.PriceControlResponse;
import at.or.reder.frodo.modbus.entity.PriceControlEntity;
import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import at.or.reder.frodo.modbus.repository.PriceControlRepository;
import at.or.reder.frodo.modbus.service.ExportSchedulerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for the global price-controlled export setting.
 *
 * <p>When enabled, the export scheduler automatically limits grid export on
 * all inverter devices that have no per-device schedule configured, whenever
 * the aWATTar AT market price is negative.</p>
 */
@Path("/price-control")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Price Control", description = "Global aWATTar AT price-controlled export limiting")
public class PriceControlResource {

  @Inject
  PriceControlRepository priceControlRepository;

  @Inject
  MarketPriceRepository marketPriceRepository;

  /**
   * Returns the current global price-control setting together with the live
   * market price so the UI can show the current blocking state.
   *
   * @return current setting; {@code enabled=false} when never configured
   */
  @GET
  @Operation(
    summary = "Get global price-control setting",
    description = "Returns the current global price-control configuration and the live "
      + "aWATTar AT market price status."
  )
  @APIResponses(@APIResponse(
    responseCode = "200",
    description = "Current setting",
    content = @Content(schema = @Schema(implementation = PriceControlResponse.class))
  ))
  public PriceControlResponse getSetting() {
    PriceControlEntity entity = priceControlRepository.findSingleton().orElse(null);
    boolean enabled = entity != null && entity.enabled;
    int toleranceWatts = entity != null ? entity.exportToleranceWatts : 50;
    return toResponse(enabled, toleranceWatts);
  }

  /**
   * Creates or replaces the global price-control setting.
   *
   * @param request new configuration
   * @return updated setting
   */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "Set global price-control setting",
    description = "Enables or disables global price-controlled export limiting. "
      + "Takes effect on the next scheduler tick (within one minute)."
  )
  @APIResponses(@APIResponse(
    responseCode = "200",
    description = "Updated setting",
    content = @Content(schema = @Schema(implementation = PriceControlResponse.class))
  ))
  public PriceControlResponse setSetting(PriceControlRequest request) {
    int toleranceWatts = request.exportToleranceWatts() != null
      ? Math.max(0, request.exportToleranceWatts())
      : 50;
    priceControlRepository.save(request.enabled(), toleranceWatts);
    return toResponse(request.enabled(), toleranceWatts);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private PriceControlResponse toResponse(boolean enabled, int toleranceWatts) {
    BigDecimal currentPriceCt = marketPriceRepository.findCurrent()
      .map(p -> p.priceCt)
      .orElse(null);
    boolean currentlyBlocking = enabled
      && currentPriceCt != null
      && ExportSchedulerService.shouldBlockForPrice(currentPriceCt);
    return new PriceControlResponse(enabled, toleranceWatts, currentPriceCt, currentlyBlocking);
  }
}
