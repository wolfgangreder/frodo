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

package at.or.reder.frodo.cost.provider;

import at.or.reder.frodo.TimeUtil;
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
import java.time.LocalDateTime;
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
   * <p>aWATTar AT interprets all epoch-ms values in Europe/Vienna local time (CET/CEST).
   * Request parameters are converted from UTC to Vienna-local-epoch ms before the call;
   * response timestamps are converted from Vienna-local-epoch ms back to UTC
   * {@link LocalDateTime} for storage.</p>
   *
   * @param direction must be {@link PriceDirection#EXPORT}
   * @param from      range start (inclusive, UTC)
   * @param to        range end (exclusive, UTC)
   * @return list of hourly prices in ct/kWh, with UTC timestamps
   * @throws UnsupportedOperationException if direction is not EXPORT
   */
  @Override
  public List<HourlyPrice> fetchPrices(PriceDirection direction, LocalDateTime from, LocalDateTime to) {
    if (direction != PriceDirection.EXPORT) {
      throw new UnsupportedOperationException(
        PROVIDER_ID + " only supports EXPORT; requested: " + direction);
    }

    // from/to are UTC; convert to epoch ms for the aWATTar request.
    long startMs = TimeUtil.toEpochMs(from);
    long endMs = TimeUtil.toEpochMs(to);

    LOG.debugf("Fetching aWATTar export prices from %s to %s (UTC)", from, to);
    try {
      MarketDataResponse response = restClient.getMarketData(startMs, endMs);
      if (response == null || response.data() == null || response.data().isEmpty()) {
        LOG.warnf("aWATTar returned no data for range %s – %s", from, to);
        return Collections.emptyList();
      }

      List<HourlyPrice> prices = response.data().stream()
        .map(mp -> {
          // aWATTar returns standard UTC epoch ms
          LocalDateTime start = TimeUtil.fromEpochMs(mp.startTimestamp());
          LocalDateTime end = TimeUtil.fromEpochMs(mp.endTimestamp());
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
