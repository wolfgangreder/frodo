package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity representing a single SunSpec parameter selected for metrics scraping.
 *
 * <p>Each parameter belongs to a {@link MetricsConfigEntity} and identifies
 * a specific field within a SunSpec model (e.g. "W" in model 113) that
 * should be collected during each scrape cycle.</p>
 *
 * <p>Uses Panache for simplified persistence operations. The {@code id}
 * field is inherited from {@link PanacheEntity}.</p>
 */
@Entity
@Table(name = "FroMetricsParameter")
public class MetricsParameterEntity extends PanacheEntity {

  /**
   * The metrics config this parameter belongs to.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "config_id", nullable = false)
  public MetricsConfigEntity config;

  /**
   * SunSpec model ID this parameter belongs to (e.g. 113, 124, 160).
   */
  @Column(name = "sunspec_model_id", nullable = false)
  public int sunspecModelId;

  /**
   * Field name within the SunSpec model (e.g. "W", "WH", "ChaState").
   */
  @Column(name = "field_name", nullable = false, length = 100)
  public String fieldName;

  /**
   * Whether this parameter is enabled for collection.
   */
  @Column(nullable = false)
  public boolean enabled = true;

  /**
   * Aggregation mode controlling how scraped values are reduced before DB writes.
   *
   * <p>Defaults to {@link AggregationMode#MINUTE_AVERAGE} to preserve the
   * original 1-minute averaging behaviour.</p>
   */
  @Column(name = "aggregation_mode", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  public AggregationMode aggregationMode = AggregationMode.MINUTE_AVERAGE;

  /**
   * Optional custom Prometheus metric name. If null, a default name
   * is generated as {@code frodo_sunspec_{modelId}_{fieldName}}.
   */
  @Column(name = "custom_metric_name", length = 100)
  public String customMetricName;
}
