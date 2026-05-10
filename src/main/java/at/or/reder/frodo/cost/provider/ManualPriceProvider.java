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

import at.or.reder.frodo.cost.spi.EnergyPriceProviderSpi;
import at.or.reder.frodo.cost.spi.PriceDirection;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * No-op {@link EnergyPriceProviderSpi} for manual price management.
 *
 * <p>Supports both {@link PriceDirection#IMPORT} and {@link PriceDirection#EXPORT}.
 * Never fetches prices automatically — users enter prices via the REST API
 * ({@code POST /api/cost-control/prices/import} or {@code POST /api/cost-control/prices/export}).</p>
 *
 * <p>Use this provider when:
 * <ul>
 *   <li>Prices are fixed (tariff windows cover all relevant time slots)</li>
 *   <li>No automatic price source is available for a direction</li>
 *   <li>Prices are entered manually or imported from a CSV file</li>
 * </ul>
 */
@ApplicationScoped
public class ManualPriceProvider implements EnergyPriceProviderSpi {

  private static final String PROVIDER_ID = "MANUAL";
  private static final Set<PriceDirection> SUPPORTED = Set.of(
    PriceDirection.IMPORT, PriceDirection.EXPORT);

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayName() {
    return "Manual (no automatic fetch)";
  }

  @Override
  public boolean isAutoFetchSupported() {
    return false;
  }

  @Override
  public Set<PriceDirection> getSupportedDirections() {
    return SUPPORTED;
  }

  /**
   * Always returns an empty list — prices must be entered via REST API.
   *
   * @param direction IMPORT or EXPORT
   * @param from      range start (unused)
   * @param to        range end (unused)
   * @return empty list
   */
  @Override
  public List<HourlyPrice> fetchPrices(PriceDirection direction, LocalDateTime from, LocalDateTime to) {
    return Collections.emptyList();
  }
}
