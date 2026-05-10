package at.or.reder.frodo.cost.entity;

import at.or.reder.frodo.cost.spi.FeeAppliesTo;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Monthly fixed/standing charge (e.g. grid connection fee, meter rental).
 *
 * <p>Multiple entries can be active simultaneously — all are summed for a given month.
 * An entry becomes active when {@code validFrom &lt;= first_day_of_month}.
 * Delete the entry by ID to deactivate it (same pattern as {@code GridFeeEntity}).</p>
 */
@Entity
@Table(name = "FroFixedCost")
public class FixedCostEntity extends PanacheEntity {

  /** Date from which this fixed cost is active (e.g. 2026-01-01). */
  @Column(name = "valid_from", nullable = false)
  public LocalDate validFrom;

  /** Which energy flow direction this cost applies to. */
  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  public FeeAppliesTo direction = FeeAppliesTo.BOTH;

  /** Total monthly fixed cost in EUR. */
  @Column(name = "monthly_cost_eur", nullable = false, precision = 15, scale = 4)
  public BigDecimal monthlyCostEur;

  /** Optional description (e.g. "Grid connection fee"). */
  @Column(name = "description", length = 255)
  public String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
