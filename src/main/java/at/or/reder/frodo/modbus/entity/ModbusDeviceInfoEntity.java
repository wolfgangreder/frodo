package at.or.reder.frodo.modbus.entity;

import at.or.reder.frodo.modbus.model.DeviceIdentification;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Map;

/**
 * Entity representing cached device identification information.
 *
 * <p>This entity stores the result of a Modbus Read Device Identification
 * (FC 0x2B/0x0E) request, including basic info (vendor, product, revision)
 * and optional extended fields. It also tracks read success/failure status
 * for monitoring and diagnostics.</p>
 *
 * <p>One-to-one relationship with {@link ModbusDeviceEntity}. Uses Panache
 * for simplified persistence operations.</p>
 */
@Entity
@Table(
  name = "FroModbusDeviceInfo",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroDeviceInfo_device",
    columnNames = {"device_id"}
  )
)
public class ModbusDeviceInfoEntity extends PanacheEntity {

  /**
   * Reference to the parent device configuration.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id", nullable = false)
  public ModbusDeviceEntity device;

  /**
   * Vendor name (Modbus Object ID 0x00, mandatory).
   */
  @Column(name = "vendor_name", length = 255)
  public String vendorName;

  /**
   * Product code (Modbus Object ID 0x01, mandatory).
   */
  @Column(name = "product_code", length = 255)
  public String productCode;

  /**
   * Firmware revision (Modbus Object ID 0x02, mandatory).
   */
  @Column(length = 255)
  public String revision;

  /**
   * Vendor URL (Modbus Object ID 0x03, optional).
   */
  @Column(name = "vendor_url", length = 500)
  public String vendorUrl;

  /**
   * Product name (Modbus Object ID 0x04, optional).
   */
  @Column(name = "product_name", length = 255)
  public String productName;

  /**
   * Model name (Modbus Object ID 0x05, optional).
   */
  @Column(name = "model_name", length = 255)
  public String modelName;

  /**
   * User application name (Modbus Object ID 0x06, optional).
   */
  @Column(name = "user_app_name", length = 255)
  public String userAppName;

  /**
   * Device conformity level (Basic=1, Regular=2, Extended=3).
   */
  @Column(name = "conformity_level")
  public Integer conformityLevel;

  /**
   * Timestamp of the last successful or failed read attempt.
   */
  @Column(name = "last_read_at")
  public Instant lastReadAt;

  /**
   * Whether the last read attempt succeeded.
   */
  @Column(name = "last_read_success")
  public Boolean lastReadSuccess;

  /**
   * Error message from the last failed read attempt.
   */
  @Column(name = "last_error_message", length = 1000)
  public String lastErrorMessage;

  /**
   * Total number of read attempts (successful + failed).
   */
  @Column(name = "read_attempt_count")
  public int readAttemptCount = 0;

  /**
   * Timestamp when this entity was created.
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  /**
   * Timestamp when this entity was last updated.
   */
  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  /**
   * Updates this entity from a {@link DeviceIdentification} read from the device.
   *
   * @param identification the device identification data
   * @param success        whether the read was successful
   * @param errorMessage   error message if the read failed, null if successful
   */
  public void updateFrom(DeviceIdentification identification, boolean success, String errorMessage) {
    if (identification != null) {
      this.vendorName = identification.vendorName();
      this.productCode = identification.productCode();
      this.revision = identification.majorMinorRevision();
      this.vendorUrl = identification.vendorUrl();
      this.productName = identification.productName();
      this.modelName = identification.modelName();
      this.userAppName = identification.userApplicationName();
    }
    this.lastReadAt = Instant.now();
    this.lastReadSuccess = success;
    this.lastErrorMessage = errorMessage;
    this.readAttemptCount++;
  }

  /**
   * Converts this entity to a {@link DeviceIdentification} domain model.
   *
   * @return DeviceIdentification with cached data and entity's lastReadAt timestamp
   */
  public DeviceIdentification toDeviceIdentification() {
    return new DeviceIdentification(
      vendorName,
      productCode,
      revision,
      vendorUrl,
      productName,
      modelName,
      userAppName,
      Map.of(), // additionalObjects not stored in database for now
      lastReadAt != null ? lastReadAt : createdAt
    );
  }

  /**
   * JPA lifecycle callback: set createdAt and updatedAt before persist.
   */
  @jakarta.persistence.PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  /**
   * JPA lifecycle callback: update updatedAt before update.
   */
  @jakarta.persistence.PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
