package at.or.reder.frodo.cost.provider;

import at.or.reder.frodo.cost.spi.EnergyPriceProviderSpi;
import at.or.reder.frodo.cost.spi.PriceDirection;
import at.or.reder.frodo.modbus.service.AwattarRestClient;
import at.or.reder.frodo.modbus.service.model.MarketDataResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * {@link EnergyPriceProviderSpi} implementation backed by the aWATTar AT spot market.
 *
 * <p>Supports {@link PriceDirection#EXPORT} only — aWATTar publishes hourly spot market prices
 * that are used as feed-in tariffs for grid export. Import prices are not provided.</p>
 *
 * <p>Price conversion: aWATTar API returns EUR/MWh;
 * converted to ct/kWh by dividing by 10.</p>
 */
@ApplicationScoped
public class AwattarPriceProvider implements EnergyPriceProviderSpi {

  private static final Logger LOG = Logger.getLogger(AwattarPriceProvider.class);
  private static final String PROVIDER_ID = "AWATTAR";
  private static final Set<PriceDirection> SUPPORTED = Set.of(PriceDirection.EXPORT);

  @Inject
  @RestClient
  AwattarRestClient restClient;

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayName() {
    return "aWATTar AT (spot market)";
  }

  @Override
  public boolean isAutoFetchSupported() {
    return true;
  }

  @Override
  public Set<PriceDirection> getSupportedDirections() {
    return SUPPORTED;
  }

  /**
   * Fetches hourly export prices from the aWATTar AT API.
   *
   * @param direction must be {@link PriceDirection#EXPORT}
   * @param from      range start (inclusive, UTC)
   * @param to        range end (exclusive, UTC)
   * @return list of hourly prices in ct/kWh
   * @throws UnsupportedOperationException if direction is not EXPORT
   */
  @Override
  public List<HourlyPrice> fetchPrices(PriceDirection direction, LocalDateTime from, LocalDateTime to) {
    if (direction != PriceDirection.EXPORT) {
      throw new UnsupportedOperationException(
        PROVIDER_ID + " only supports EXPORT; requested: " + direction);
    }

    ZoneId utc = ZoneId.of("UTC");
    long startMs = from.atZone(utc).toInstant().toEpochMilli();
    long endMs = to.atZone(utc).toInstant().toEpochMilli();

    LOG.debugf("Fetching aWATTar export prices from %s to %s", from, to);
    try {
      MarketDataResponse response = restClient.getMarketData(startMs, endMs);
      if (response == null || response.data() == null || response.data().isEmpty()) {
        LOG.warnf("aWATTar returned no data for range %s – %s", from, to);
        return Collections.emptyList();
      }

      List<HourlyPrice> prices = response.data().stream()
        .map(mp -> {
          LocalDateTime start = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(mp.startTimestamp()), utc);
          LocalDateTime end = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(mp.endTimestamp()), utc);
          // EUR/MWh → ct/kWh: divide by 10
          BigDecimal priceCt = BigDecimal.valueOf(mp.marketPrice())
            .divide(BigDecimal.TEN, 5, RoundingMode.HALF_UP);
          return new HourlyPrice(start, end, priceCt);
        })
        .toList();

      LOG.debugf("aWATTar returned %d price entries", prices.size());
      return prices;
    } catch (Exception ex) {
      LOG.errorf(ex, "Failed to fetch aWATTar prices for range %s – %s", from, to);
      throw ex;
    }
  }
}
