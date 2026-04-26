package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Persistent daily recurring schedule for automatic grid export control.
 *
 * <p>Each row defines a single time window per device during which the inverter's
 * grid export should be blocked (WMaxLim_Ena = 1, WMaxLimPct = 0) and outside
 * which export is re-enabled (WMaxLim_Ena = 0).</p>
 *
 * <p>At most one schedule exists per device (unique constraint on {@code deviceId}).
 * The {@link at.or.reder.frodo.modbus.service.ExportSchedulerService} checks this
 * table every minute and applies the correct state via SunSpec Model 123.</p>
 *
 * <p>Two blocking strategies are supported:</p>
 * <ul>
 *   <li>{@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC} — closed-loop: reads the Smart
 *       Meter every tick and computes a limit that prevents net grid export
 *       (Nulleinspeisung).  Requires a Smart Meter child device.</li>
 *   <li>{@link ExportBlockStrategy#FIXED_LIMIT} — static watt cap: writes a constant
 *       {@code WMaxLimPct} derived from {@link #limitWatts}.  No Smart Meter needed;
 *       allows a small, controlled grid feed-in.</li>
 * </ul>
 *
 * <p>Window semantics:</p>
 * <ul>
 *   <li>If {@code blockFrom} &lt; {@code enableFrom}: blocked when
 *       {@code blockFrom} &le; now &lt; {@code enableFrom} (same-day window).</li>
 *   <li>If {@code blockFrom} &gt; {@code enableFrom}: blocked when
 *       now &ge; {@code blockFrom} OR now &lt; {@code enableFrom} (crosses midnight).</li>
 *   <li>If {@code blockFrom} == {@code enableFrom}: always blocked.</li>
 * </ul>
 */
@Entity
@Table(
  name = "FroExportSchedule",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroExpSched_device",
    columnNames = {"device_id"}
  )
)
public class ExportScheduleEntity extends PanacheEntity {

  /**
   * ID of the device this schedule belongs to.
   * Matches {@link ModbusDeviceEntity#id}.
   */
  @Column(name = "device_id", nullable = false)
  public Long deviceId;

  /**
   * Whether this schedule is currently active.
   * Inactive schedules are ignored by the scheduler.
   */
  @Column(nullable = false)
  public boolean enabled = true;

  /**
   * Start of the daily block window (inclusive).
   * When the clock reaches this time, export is blocked.
   */
  @Column(name = "block_from", nullable = false)
  public LocalTime blockFrom;

  /**
   * End of the daily block window (exclusive).
   * When the clock reaches this time, export is re-enabled.
   */
  @Column(name = "enable_from", nullable = false)
  public LocalTime enableFrom;

  /**
   * Blocking strategy applied while the window is active.
   * Defaults to {@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC}.
   */
  @Column(name = "strategy", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  public ExportBlockStrategy strategy = ExportBlockStrategy.ZERO_EXPORT_DYNAMIC;

  /**
   * Fixed power cap in Watts used by the {@link ExportBlockStrategy#FIXED_LIMIT} strategy.
   * Must be a positive integer when {@link #strategy} is {@code FIXED_LIMIT}; ignored
   * for {@code ZERO_EXPORT_DYNAMIC}.
   */
  @Column(name = "limit_watts")
  public Integer limitWatts;

  /**
   * Timestamp when this schedule was first created.
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  /**
   * Timestamp when this schedule was last modified.
   */
  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  /** JPA lifecycle: set timestamps on insert. */
  @jakarta.persistence.PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  /** JPA lifecycle: update timestamp on update. */
  @jakarta.persistence.PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
