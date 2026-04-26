package at.or.reder.frodo.modbus.service.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from aWATTar AT market data API.
 *
 * <p>Example response:</p>
 * <pre>
 * {
 *   "object": "list",
 *   "data": [
 *     {
 *       "start_timestamp": 1428591600000,
 *       "end_timestamp": 1428595200000,
 *       "marketprice": 42.09,
 *       "unit": "Eur/MWh"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Reference:</b> {@code https://www.awattar.at/services/api}</p>
 */
public record MarketDataResponse(
  @JsonProperty("object") String object,
  @JsonProperty("data") List<MarketPrice> data
) {

  /**
   * Individual market price entry for one hour.
   */
  public record MarketPrice(
    @JsonProperty("start_timestamp") long startTimestamp,
    @JsonProperty("end_timestamp") long endTimestamp,
    @JsonProperty("marketprice") double marketPrice,
    @JsonProperty("unit") String unit
  ) {
    /**
     * Gets the start time as epoch milliseconds.
     *
     * @return start timestamp
     */
    public long getStartTimestamp() {
      return startTimestamp;
    }

    /**
     * Gets the end time as epoch milliseconds.
     *
     * @return end timestamp
     */
    public long getEndTimestamp() {
      return endTimestamp;
    }

    /**
     * Gets the market price in EUR/MWh.
     *
     * @return price
     */
    public double getMarketPrice() {
      return marketPrice;
    }
  }
}