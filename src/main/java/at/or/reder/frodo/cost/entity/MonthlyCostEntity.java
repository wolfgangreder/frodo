package at.or.reder.frodo.cost.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pre-calculated monthly cost summary.
 *
 * <p>Updated in real-time after every hourly cost insert.
 * The {@code yearMonth} key uses format {@code "yyyy-MM"} (e.g. {@code "2026-05"}).</p>
 */
@Entity
@Table(
  name = "FroMonthlyCost",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_FroMonthlyCost_month",
    columnNames = {"year_month"}
  )
)
public class MonthlyCostEntity extends PanacheEntity {

  /** Year-month key in format {@code "yyyy-MM"} (e.g. {@code "2026-05"}). */
  @Column(name = "year_month", nullable = false, length = 7)
  public String yearMonth;

  /** Total kWh imported from grid for this month. */
  @Column(name = "total_import_kwh", nullable = false, precision = 15, scale = 6)
  public BigDecimal totalImportKwh;

  /** Total kWh exported to grid for this month. */
  @Column(name = "total_export_kwh", nullable = false, precision = 15, scale = 6)
  public BigDecimal totalExportKwh;

  /** Total import cost in EUR for this month. */
  @Column(name = "total_import_cost_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal totalImportCostEur;

  /** Total export income in EUR for this month. */
  @Column(name = "total_export_income_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal totalExportIncomeEur;

  /** Total grid fee amount in EUR for this month. */
  @Column(name = "total_fee_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal totalFeeEur;

  /** Fixed/standing charge in EUR for this month (from {@code FroFixedCost}). */
  @Column(name = "fixed_cost_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal fixedCostEur;

  /**
   * Net cost in EUR:
   * {@code totalImportCostEur - totalExportIncomeEur + totalFeeEur + fixedCostEur}.
   */
  @Column(name = "net_cost_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal netCostEur;

  /** Count of {@code FroHourlyCost} rows summed into this month. */
  @Column(name = "hours_calculated", nullable = false)
  public int hoursCalculated;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
