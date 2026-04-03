package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity for storing historical metrics data points.
 *
 * <p>Each row represents a single scraped value: one field from one
 * SunSpec model on one device at a specific point in time. Values
 * are stored as either numeric (most common for metrics) or string.</p>
 *
 * <p>Uses Panache for simplified persistence operations. The {@code id}
 * field is inherited from {@link PanacheEntity}.</p>
 */
@Entity
@Table(
  name = "FroMetricsData",
  indexes = {
    @Index(name = "idx_FroMetricsData_device_time", columnList = "device_id, recorded_at DESC"),
    @Index(name = "idx_FroMetricsData_param_time", columnList = "parameter_id, recorded_at DESC"),
    @Index(name = "idx_FroMetricsData_time", columnList = "recorded_at DESC")
  }
)
public class MetricsDataEntity extends PanacheEntity {

  /**
   * The device this data point belongs to.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id", nullable = false)
  public ModbusDeviceEntity device;

  /**
   * The parameter definition this data point corresponds to.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parameter_id", nullable = false)
  public MetricsParameterEntity parameter;

  /**
   * Timestamp when this value was recorded.
   */
  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;

  /**
   * Numeric value (most common for SunSpec metrics like power, voltage, etc.).
   */
  @Column(name = "value_numeric")
  public Double valueNumeric;

  /**
   * String value (rare, used for non-numeric fields like serial numbers).
   */
  @Column(name = "value_string", length = 255)
  public String valueString;

  /**
   * SunSpec model ID (denormalized for efficient querying without joins).
   */
  @Column(name = "sunspec_model_id", nullable = false)
  public int sunspecModelId;

  /**
   * Field name (denormalized for efficient querying without joins).
   */
  @Column(name = "field_name", nullable = false, length = 100)
  public String fieldName;
}
