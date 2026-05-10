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

package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.MetricsDataEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Repository for {@link MetricsDataEntity} persistence operations.
 *
 * <p>Provides data access methods for historical metrics data,
 * including time-range queries, aggregation, and retention cleanup.</p>
 *
 * <p>Uses Panache repository pattern for simplified JPA operations.
 * All write operations are transactional.</p>
 */
@ApplicationScoped
public class MetricsDataRepository implements PanacheRepository<MetricsDataEntity> {

  /**
   * Finds metrics data for a device within a time range.
   *
   * @param deviceId device ID
   * @param from     start of time range (inclusive)
   * @param to       end of time range (inclusive)
   * @param limit    maximum number of results
   * @return list of data points ordered by recorded_at descending
   */
  public List<MetricsDataEntity> findByDeviceAndTimeRange(
    Long deviceId, Instant from, Instant to, int limit
  ) {
    return find(
      "device.id = ?1 and recordedAt >= ?2 and recordedAt <= ?3 order by recordedAt desc",
      deviceId, from, to
    ).page(0, limit).list();
  }

  /**
   * Finds metrics data for a specific parameter within a time range.
   *
   * @param parameterId parameter ID
   * @param from        start of time range (inclusive)
   * @param to          end of time range (inclusive)
   * @param limit       maximum number of results
   * @return list of data points ordered by recorded_at descending
   */
  public List<MetricsDataEntity> findByParameterAndTimeRange(
    Long parameterId, Instant from, Instant to, int limit
  ) {
    return find(
      "parameter.id = ?1 and recordedAt >= ?2 and recordedAt <= ?3 order by recordedAt desc",
      parameterId, from, to
    ).page(0, limit).list();
  }

  /**
   * Finds the latest metrics data for a device.
   *
   * @param deviceId device ID
   * @param limit    maximum number of results
   * @return list of latest data points ordered by recorded_at descending
   */
  public List<MetricsDataEntity> findLatestByDevice(Long deviceId, int limit) {
    return find("device.id = ?1 order by recordedAt desc", deviceId)
      .page(0, limit).list();
  }

  /**
   * Finds aggregated (daily) metrics data for a specific device, model, and field.
   *
   * <p>Returns rows of [date, aggregatedValue, sampleCount] grouped by day.
   * The aggregation function can be AVG, MIN, MAX, or SUM.</p>
   *
   * @param deviceId    device ID
   * @param modelId     SunSpec model ID
   * @param fieldName   field name
   * @param from        start of time range
   * @param to          end of time range
   * @param aggregation aggregation function (AVG, MIN, MAX, SUM)
   * @return list of Object[] rows: [java.sql.Date, Double, Long]
   */
  @SuppressWarnings("unchecked")
  public List<Object[]> findAggregatedByDeviceAndField(
    Long deviceId, int modelId, String fieldName,
    Instant from, Instant to, String aggregation
  ) {
    // Validate aggregation function to prevent SQL injection
    String agg = switch (aggregation.toUpperCase()) {
      case "AVG", "MIN", "MAX", "SUM" -> aggregation.toUpperCase();
      default -> "AVG";
    };

    String sql = """
      SELECT
        CAST(d.recorded_at AS DATE) as bucket,
        %s(d.value_numeric) as agg_value,
        COUNT(*) as sample_count
      FROM FroMetricsData d
      WHERE d.device_id = ?1
        AND d.sunspec_model_id = ?2
        AND d.field_name = ?3
        AND d.recorded_at >= ?4
        AND d.recorded_at <= ?5
        AND d.value_numeric IS NOT NULL
      GROUP BY CAST(d.recorded_at AS DATE)
      ORDER BY bucket DESC
      """.formatted(agg);

    return getEntityManager()
      .createNativeQuery(sql)
      .setParameter(1, deviceId)
      .setParameter(2, modelId)
      .setParameter(3, fieldName)
      .setParameter(4, from)
      .setParameter(5, to)
      .getResultList();
  }

  /**
   * Deletes metrics data older than the specified cutoff time for a device.
   *
   * @param deviceId   device ID
   * @param cutoffTime cutoff time (data older than this is deleted)
   * @return number of deleted records
   */
  @Transactional
  public int deleteOlderThan(Long deviceId, Instant cutoffTime) {
    return (int) delete("device.id = ?1 and recordedAt < ?2", deviceId, cutoffTime);
  }

  /**
   * Batch-persists a list of metrics data points.
   *
   * @param dataPoints list of data points to persist
   */
  @Transactional
  public void persistAll(List<MetricsDataEntity> dataPoints) {
    for (MetricsDataEntity point : dataPoints) {
      persist(point);
    }
  }
}
