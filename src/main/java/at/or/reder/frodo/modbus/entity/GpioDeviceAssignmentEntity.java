package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Assigns a GPIO pair (from application.properties) to a Modbus device.
 *
 * <p>When a device uses the {@code PRICE_CONTROLLED_GPIO} export strategy
 * the scheduler looks up this entity to determine which GPIO pair to use.
 * Both {@code deviceId} and {@code gpioPairName} are unique — one device
 * maps to exactly one pair, and each pair is used by at most one device.</p>
 *
 * <p>The {@code gpioPairName} must match a key in
 * {@code frodo.gpio.pairs.*}; there is intentionally no FK constraint to
 * configuration (configuration lives outside the DB).  If the named pair
 * is not configured at runtime the scheduler falls back to Modbus.</p>
 */
@Entity
@Table(
  name = "FroGpioAssignment",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_FroGpioAssignment_device", columnNames = "device_id"),
    @UniqueConstraint(name = "uk_FroGpioAssignment_pair", columnNames = "gpio_pair_name")
  }
)
public class GpioDeviceAssignmentEntity extends PanacheEntity {

  /** FK to FroModbusDevice.id */
  @Column(name = "device_id", nullable = false)
  public Long deviceId;

  /**
   * Name of the GPIO pair as declared in {@code frodo.gpio.pairs.<name>.*}.
   * Max 64 characters to match practical config key lengths.
   */
  @Column(name = "gpio_pair_name", nullable = false, length = 64)
  public String gpioPairName;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  @PreUpdate
  protected void onWrite() {
    updatedAt = Instant.now();
  }
}
