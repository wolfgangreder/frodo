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

package at.or.reder.frodo.cost.spi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SPI for hourly energy price providers.
 *
 * <p>Implementations are CDI {@code @ApplicationScoped} beans registered in the container.
 * The scheduler selects the active provider per direction at runtime by matching
 * {@link #getProviderId()} against the value stored in {@code FroCostControlConfig}.</p>
 *
 * <p>Each provider is direction-specific: declare which directions are supported via
 * {@link #getSupportedDirections()}. A provider that only supports export
 * (e.g. the aWATTar spot market) returns {@code Set.of(PriceDirection.EXPORT)}.</p>
 */
public interface EnergyPriceProviderSpi {

  /**
   * Unique string identifier used in configuration and DB.
   * Must be stable across restarts (e.g. {@code "AWATTAR"}, {@code "MANUAL"}).
   */
  String getProviderId();

  /** Human-readable display name shown in the UI provider selector. */
  String getDisplayName();

  /**
   * Whether this provider fetches prices automatically on a schedule.
   * Returns {@code false} for the {@code MANUAL} provider (user enters prices via REST).
   */
  boolean isAutoFetchSupported();

  /**
   * Directions this provider can supply prices for.
   * The scheduler skips a direction if the configured provider does not include it.
   */
  Set<PriceDirection> getSupportedDirections();

  /**
   * Fetch hourly prices for the given direction and time range.
   *
   * <p>Called only when {@link #isAutoFetchSupported()} returns {@code true}.
   * The scheduler typically requests {@code from = now-truncated-to-hour},
   * {@code to = from + 48h}.</p>
   *
   * @param direction IMPORT or EXPORT
   * @param from      range start (inclusive, UTC)
   * @param to        range end (exclusive, UTC)
   * @return list of hourly price records; empty when no data is available
   * @throws UnsupportedOperationException if direction is not in {@link #getSupportedDirections()}
   */
  List<HourlyPrice> fetchPrices(PriceDirection direction, LocalDateTime from, LocalDateTime to);

  /**
   * One hour of price data for a single direction.
   *
   * @param startTime hour start (UTC)
   * @param endTime   hour end (UTC)
   * @param priceCt   price in ct/kWh for the specified direction
   */
  record HourlyPrice(
    LocalDateTime startTime,
    LocalDateTime endTime,
    BigDecimal priceCt
  ) {}
}
