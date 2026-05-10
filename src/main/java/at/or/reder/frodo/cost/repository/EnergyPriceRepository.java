package at.or.reder.frodo.cost.repository;

import at.or.reder.frodo.cost.entity.EnergyPriceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link EnergyPriceEntity} — hourly raw provider prices per direction.
 */
@ApplicationScoped
public class EnergyPriceRepository implements PanacheRepository<EnergyPriceEntity> {

  /**
   * Finds the price row for a specific hour start.
   *
   * @param startTime exact hour start
   * @return price entity if found
   */
  public Optional<EnergyPriceEntity> findByStartTime(LocalDateTime startTime) {
    return find("startTime", startTime).firstResultOptional();
  }

  /**
   * Finds the price row that covers the given point in time.
   *
   * @param time point in time (UTC)
   * @return price entity covering that hour, or empty
   */
  public Optional<EnergyPriceEntity> findForTime(LocalDateTime time) {
    return find("startTime <= ?1 and endTime > ?1", time).firstResultOptional();
  }

  /**
   * Lists the most recent N price rows ordered by start_time descending.
   *
   * @param limit max rows (capped by caller)
   * @return list of recent price entities
   */
  public List<EnergyPriceEntity> listRecent(int limit) {
    return find("order by startTime desc")
      .page(io.quarkus.panache.common.Page.of(0, limit))
      .list();
  }

  /**
   * Creates or updates the import-direction columns of a price row.
   *
   * @param startTime hour start
   * @param endTime   hour end
   * @param priceCt   import price in ct/kWh
   * @param source    provider id
   * @return the persisted entity
   */
  @Transactional
  public EnergyPriceEntity upsertImport(
      LocalDateTime startTime, LocalDateTime endTime, BigDecimal priceCt, String source) {
    EnergyPriceEntity e = findByStartTime(startTime).orElseGet(EnergyPriceEntity::new);
    e.startTime = startTime;
    e.endTime = endTime;
    e.priceImportCt = priceCt;
    e.importSource = source;
    e.updatedAt = Instant.now();
    persist(e);
    return e;
  }

  /**
   * Creates or updates the export-direction columns of a price row.
   *
   * @param startTime hour start
   * @param endTime   hour end
   * @param priceCt   export price in ct/kWh
   * @param source    provider id
   * @return the persisted entity
   */
  @Transactional
  public EnergyPriceEntity upsertExport(
      LocalDateTime startTime, LocalDateTime endTime, BigDecimal priceCt, String source) {
    EnergyPriceEntity e = findByStartTime(startTime).orElseGet(EnergyPriceEntity::new);
    e.startTime = startTime;
    e.endTime = endTime;
    e.priceExportCt = priceCt;
    e.exportSource = source;
    e.updatedAt = Instant.now();
    persist(e);
    return e;
  }

  /**
   * Deletes price rows older than the given cutoff.
   *
   * @param before cutoff time (rows with endTime before this are deleted)
   * @return number of deleted rows
   */
  @Transactional
  public int deleteExpired(LocalDateTime before) {
    return (int) delete("endTime < ?1", before);
  }
}
