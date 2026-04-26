package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.MarketPriceResponse;
import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import at.or.reder.frodo.modbus.service.AwattarClient;
import at.or.reder.frodo.modbus.service.MarketPriceSchedulerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * REST resource for aWATTar AT market price data.
 *
 * <p>Provides access to stored market prices and triggers manual refresh.</p>
 */
@Path("/market-prices")
@Tag(name = "Market Prices", description = "aWATTar AT market price data")
public class MarketPriceResource {

  private static final Logger LOG = Logger.getLogger(MarketPriceResource.class);
  private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  @Inject
  MarketPriceRepository marketPriceRepository;

  @Inject
  AwattarClient awattarClient;

  @Inject
  MarketPriceSchedulerService marketPriceSchedulerService;

  /**
   * Returns all stored market prices.
   *
   * @return list of market prices
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get all market prices")
  public List<MarketPriceResponse> getAllPrices() {
    return marketPriceRepository.listAll().stream()
      .map(this::toResponse)
      .toList();
  }

  /**
   * Returns the most recent market prices.
   *
   * @param limit max entries to return
   * @return list of recent prices
   */
  @GET
  @Path("/recent/{limit}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get recent market prices")
  public List<MarketPriceResponse> getRecentPrices(@PathParam("limit") int limit) {
    return marketPriceRepository.listRecent(Math.max(1, Math.min(limit, 48))).stream()
      .map(this::toResponse)
      .toList();
  }

  /**
   * Returns the current market price.
   *
   * @return current price, or 404 if not available
   */
  @GET
  @Path("/current")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get current market price")
  public MarketPriceResponse getCurrentPrice() {
    return marketPriceRepository.findCurrent()
      .map(this::toResponse)
      .orElseThrow(NotFoundException::new);
  }

  /**
   * Returns the market price for a specific hour.
   *
   * @param startTime start time in ISO format
   * @return price entry, or 404 if not found
   */
  @GET
  @Path("/{startTime}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get market price for specific hour")
  public MarketPriceResponse getPriceForHour(@PathParam("startTime") String startTime) {
    LocalDateTime time = LocalDateTime.parse(startTime, DT_FMT);
    return marketPriceRepository.findByStartTime(time)
      .map(this::toResponse)
      .orElseThrow(NotFoundException::new);
  }

  /**
   * Triggers an immediate fetch of market prices from aWATTar AT.
   *
   * <p>Useful to populate or refresh prices without waiting for the next
   * scheduled run (every hour at minute 55).</p>
   *
   * @return the current price after the refresh, or 204 No Content if no price
   *         applies to the current hour yet (aWATTar may only provide future hours)
   */
  @POST
  @Path("/refresh")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Force-refresh market prices from aWATTar AT")
  public MarketPriceResponse refresh() {
    marketPriceSchedulerService.refreshNow();
    return marketPriceRepository.findCurrent()
      .map(this::toResponse)
      .orElse(null);
  }

  private MarketPriceResponse toResponse(at.or.reder.frodo.modbus.entity.MarketPriceEntity entity) {
    return new MarketPriceResponse(
      entity.startTime.format(DT_FMT),
      entity.endTime.format(DT_FMT),
      entity.priceCt,
      entity.createdAt.toString());
  }
}