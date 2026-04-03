package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing per-device metrics scraping configuration.
 *
 * <p>Each device can have at most one metrics config (one-to-one).
 * The config controls the scraping interval, whether scraping is
 * enabled, and which SunSpec parameters to collect. It also tracks
 * the last scrape status for monitoring.</p>
 *
 * <p>Uses Panache for simplified persistence operations. The {@code id}
 * field is inherited from {@link PanacheEntity}.</p>
 */
@Entity
@Table(
  name = "FroMetricsConfig",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroMetricsConfig_device",
    columnNames = {"device_id"}
  )
)
public class MetricsConfigEntity extends PanacheEntity {

  /**
   * The device this metrics config belongs to.
   */
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id", nullable = false)
  public ModbusDeviceEntity device;

  /**
   * Interval in seconds between scrape operations (5-300).
   */
  @Column(name = "scrape_interval_seconds", nullable = false)
  public int scrapeIntervalSeconds = 30;

  /**
   * Whether metrics scraping is enabled for this device.
   */
  @Column(nullable = false)
  public boolean enabled = true;

  /**
   * Whether scraped values should be persisted to the database.
   */
  @Column(name = "store_to_database", nullable = false)
  public boolean storeToDatabase = true;

  /**
   * Number of days to retain historical metrics data (1-3650).
   */
  @Column(name = "retention_days", nullable = false)
  public int retentionDays = 365;

  /**
   * Timestamp of the last scrape attempt.
   */
  @Column(name = "last_scrape_time")
  public Instant lastScrapeTime;

  /**
   * Status of the last scrape attempt.
   */
  @Column(name = "last_scrape_status", length = 20)
  @Enumerated(EnumType.STRING)
  public ScrapeStatus lastScrapeStatus;

  /**
   * Error message from the last failed scrape, if any.
   */
  @Column(name = "last_error_message", length = 500)
  public String lastErrorMessage;

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
   * The SunSpec parameters configured for scraping.
   */
  @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  public List<MetricsParameterEntity> parameters = new ArrayList<>();

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
