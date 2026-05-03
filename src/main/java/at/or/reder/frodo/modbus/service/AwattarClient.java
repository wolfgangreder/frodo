package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.service.model.MarketDataResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
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
   * Fetches market data for the next 24 hours from aWATTar AT (blocking).
   *
   * <p><b>Endpoint:</b> {@code GET https://api.awattar.at/v1/marketdata}</p>
   *
   * @return market price response
   * @throws IllegalStateException if aWATTar is disabled
   * @throws RuntimeException      if the HTTP call fails
   */
  public MarketDataResponse getMarketData() {
    if (!awattarEnabled) {
      throw new IllegalStateException("aWATTar is disabled (frodo.awattar.enabled=false)");
    }

    LOG.debugf("Fetching market data from %s%s", BASE_URL, RESOURCE_PATH);
    try {
      MarketDataResponse response = restClient.getMarketData();
      List<MarketDataResponse.MarketPrice> prices = response != null ? response.data() : null;
      LOG.debugf("Received market data: %d price entries", prices != null ? prices.size() : 0);
      return response;
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to fetch market data from %s%s", BASE_URL, RESOURCE_PATH);
      throw ex;
    }
  }

  /**
   * Fetches market data for a specific time window (blocking).
   *
   * @param start start time in epoch milliseconds
   * @param end   end time in epoch milliseconds
   * @return market price response
   * @throws IllegalStateException if aWATTar is disabled
   * @throws RuntimeException      if the HTTP call fails
   */
  public MarketDataResponse getMarketData(long start, long end) {
    if (!awattarEnabled) {
      throw new IllegalStateException("aWATTar is disabled (frodo.awattar.enabled=false)");
    }

    LOG.debugf("Fetching market data from %s to %s",
      Instant.ofEpochMilli(start), Instant.ofEpochMilli(end));
    try {
      return restClient.getMarketData(start, end);
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to fetch market data for time range %d-%d", start, end);
      throw ex;
    }
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
