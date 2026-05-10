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
