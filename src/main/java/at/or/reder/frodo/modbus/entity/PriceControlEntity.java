package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Global price-controlled export setting (singleton — at most one row).
 *
 * <p>When {@link #enabled} is {@code true} the export scheduler applies a
 * dynamic power limit to every enabled inverter that does not have its own
 * per-device {@link ExportScheduleEntity}.  The limit is recomputed every
 * scheduler tick from the current house load and battery demand:
 * <pre>
 *   targetWatts = -(P_Load + P_Battery) + exportToleranceWatts
 * </pre>
 * while the aWATTar AT market price is negative; when the price is zero or
 * positive, export is fully re-enabled.</p>
 *
 * <p>Unlike the per-device {@link ExportScheduleEntity} with strategy
 * {@code PRICE_CONTROLLED}, this setting requires no schedule configuration
 * and applies to all inverter devices automatically.</p>
 */
@Entity
@Table(name = "FroPriceControl")
public class PriceControlEntity extends PanacheEntity {

  /**
   * Whether global price-controlled export limiting is active.
   */
  @Column(nullable = false)
  public boolean enabled = false;

  /**
   * Allowed grid export above load + battery demand when the market price is
   * negative.  {@code 0} = strict zero-export; default 50 W = small buffer.
   */
  @Column(name = "export_tolerance_watts", nullable = false)
  public int exportToleranceWatts = 50;

  /**
   * Timestamp of the last change.
   */
  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  /** JPA lifecycle: set timestamp on insert and update. */
  @jakarta.persistence.PrePersist
  @jakarta.persistence.PreUpdate
  protected void onWrite() {
    updatedAt = Instant.now();
  }
}
