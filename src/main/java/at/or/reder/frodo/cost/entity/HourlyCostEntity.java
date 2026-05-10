package at.or.reder.frodo.cost.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Hourly cost and income calculation, including the effective prices and sources used.
 *
 * <p>Persisted immediately after each hourly energy integration completes.
 * The {@code *PriceSource} columns provide an audit trail showing whether
 * the effective price came from a tariff window or a price provider.</p>
 */
@Entity
@Table(
  name = "FroHourlyCost",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroHourlyCost_hour",
    columnNames = {"hour_start"}
  )
)
public class HourlyCostEntity extends PanacheEntity {

  /** Start of the calendar hour (UTC). */
  @Column(name = "hour_start", nullable = false)
  public LocalDateTime hourStart;

  /** End of the calendar hour (UTC). */
  @Column(name = "hour_end", nullable = false)
  public LocalDateTime hourEnd;

  /** kWh imported from grid this hour. */
  @Column(name = "import_kwh", nullable = false, precision = 15, scale = 6)
  public BigDecimal importKwh;

  /** kWh exported to grid this hour. */
  @Column(name = "export_kwh", nullable = false, precision = 15, scale = 6)
  public BigDecimal exportKwh;

  /** Effective import price in ct/kWh (from tariff window or provider). */
  @Column(name = "price_import_ct", nullable = false, precision = 15, scale = 5)
  public BigDecimal priceImportCt;

  /** Effective export price in ct/kWh (from tariff window or provider). */
  @Column(name = "price_export_ct", nullable = false, precision = 15, scale = 5)
  public BigDecimal priceExportCt;

  /**
   * Source of effective import price.
   * Either {@code "TARIFF_WINDOW"} or a provider ID (e.g. {@code "AWATTAR"}, {@code "MANUAL"}).
   */
  @Column(name = "import_price_source", nullable = false, length = 20)
  public String importPriceSource;

  /**
   * Source of effective export price.
   * Either {@code "TARIFF_WINDOW"} or a provider ID.
   */
  @Column(name = "export_price_source", nullable = false, length = 20)
  public String exportPriceSource;

  /** Total import cost in EUR: {@code importKwh * priceImportCt / 100}. */
  @Column(name = "import_cost_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal importCostEur;

  /** Total export income in EUR: {@code exportKwh * priceExportCt / 100}. */
  @Column(name = "export_income_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal exportIncomeEur;

  /** Sum of all active grid fee amounts for this hour in EUR. */
  @Column(name = "fee_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal feeEur;

  /** Net cost: {@code importCostEur - exportIncomeEur + feeEur}. */
  @Column(name = "net_cost_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal netCostEur;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
