package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.service.model.MarketDataResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * HTTP client for aWATTar AT market data API.
 *
 * <p>Provides access to hourly stock exchange prices from
 * {@code https://api.awattar.at/v1/marketdata}.</p>
 *
 * <p><b>API Usage:</b></p>
 * <ul>
 *   <li>Returns prices for the next 24 hours by default</li>
 *   <li>Prices are in EUR/MWh</li>
 *   <li>Time window defined by start_timestamp and end_timestamp (epoch milliseconds)</li>
 * </ul>
 *
 * <p><b>Reference:</b> {@code https://www.awattar.at/services/api}</p>
 */
@ApplicationScoped
public class AwattarClient {

  private static final Logger LOG = Logger.getLogger(AwattarClient.class);

  private static final String BASE_URL = "https://api.awattar.at";
  private static final String RESOURCE_PATH = "/v1/marketdata";

  @ConfigProperty(name = "frodo.awattar.enabled", defaultValue = "false")
  boolean awattarEnabled;

  @Inject
  @RestClient
  AwattarRestClient restClient;

  /**
   * Fetches market data for the next 24 hours from aWATTar AT.
   *
   * <p><b>Endpoint:</b> {@code GET https://api.awattar.at/v1/marketdata}</p>
   *
   * @return async response with market price list
   */
  public Uni<MarketDataResponse> getMarketData() {
    if (!awattarEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("aWATTar is disabled (frodo.awattar.enabled=false)")
      );
    }

    LOG.debugf("Fetching market data from %s%s", BASE_URL, RESOURCE_PATH);

    return restClient.getMarketData()
      .onItem().invoke(response -> {
        List<MarketDataResponse.MarketPrice> prices = response.data();
        LOG.debugf("Received market data: %d price entries", prices != null ? prices.size() : 0);
      })
      .onFailure().invoke(ex -> {
        LOG.errorf(ex, "Failed to fetch market data from %s%s", BASE_URL, RESOURCE_PATH);
      });
  }

  /**
   * Fetches market data for a specific time window.
   *
   * @param start start time in epoch milliseconds
   * @param end   end time in epoch milliseconds
   * @return async response with market price list
   */
  public Uni<MarketDataResponse> getMarketData(long start, long end) {
    if (!awattarEnabled) {
      return Uni.createFrom().failure(
        new IllegalStateException("aWATTar is disabled (frodo.awattar.enabled=false)")
      );
    }

    LOG.debugf("Fetching market data from %s to %s",
      Instant.ofEpochMilli(start), Instant.ofEpochMilli(end));

    return restClient.getMarketData(start, end)
      .onFailure().invoke(ex -> {
        LOG.errorf(ex, "Failed to fetch market data for time range %d-%d", start, end);
      });
  }

  /**
   * Checks if aWATTar integration is enabled.
   *
   * @return true if enabled via configuration
   */
  public boolean isEnabled() {
    return awattarEnabled;
  }

  /**
   * Gets the configured base URL.
   *
   * @return base URL
   */
  public String getBaseUrl() {
    return BASE_URL;
  }
}