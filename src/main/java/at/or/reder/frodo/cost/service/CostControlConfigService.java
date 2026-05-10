package at.or.reder.frodo.cost.service;

import at.or.reder.frodo.cost.entity.CostControlConfigEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Service providing access to the DB-backed cost control runtime configuration.
 *
 * <p>Configuration is stored in {@code FroCostControlConfig} (always row id=1).
 * Falls back to {@code application.properties} defaults on first access if the
 * DB row has not been seeded yet.</p>
 *
 * <p>Extends {@link PanacheRepository} to use Panache's static EntityManager
 * access — avoids direct {@code @Inject EntityManager} which fails when
 * Hibernate ORM is disabled in the test profile.</p>
 */
@ApplicationScoped
public class CostControlConfigService implements PanacheRepository<CostControlConfigEntity> {

  private static final Logger LOG = Logger.getLogger(CostControlConfigService.class);

  @ConfigProperty(name = "frodo.cost-control.price.import.provider", defaultValue = "MANUAL")
  String defaultImportProvider;

  @ConfigProperty(name = "frodo.cost-control.price.export.provider", defaultValue = "AWATTAR")
  String defaultExportProvider;

  @ConfigProperty(name = "frodo.cost-control.price.import.fetch-cron", defaultValue = "0 55 * * * ?")
  String defaultImportCron;

  @ConfigProperty(name = "frodo.cost-control.price.export.fetch-cron", defaultValue = "0 55 * * * ?")
  String defaultExportCron;

  @ConfigProperty(name = "frodo.cost-control.integration.sample-interval-seconds", defaultValue = "15")
  int defaultSampleIntervalSeconds;

  @ConfigProperty(name = "frodo.cost-control.integration.dead-band-watts", defaultValue = "10.0")
  double defaultDeadBandWatts;

  @ConfigProperty(name = "frodo.cost-control.retention.hourly-days", defaultValue = "365")
  int defaultRetentionHourlyDays;

  @ConfigProperty(name = "frodo.cost-control.retention.monthly-years", defaultValue = "10")
  int defaultRetentionMonthlyYears;

  /**
   * Loads the current configuration from DB.
   * Returns defaults if DB row is absent (should not happen after Liquibase seed).
   *
   * @return current config entity
   */
  public CostControlConfigEntity load() {
    CostControlConfigEntity cfg = getEntityManager().find(CostControlConfigEntity.class, 1L);
    if (cfg == null) {
      LOG.warn("FroCostControlConfig row missing — using property defaults");
      cfg = buildDefaults();
    }
    return cfg;
  }

  /**
   * Updates the configuration in DB.
   *
   * @param updated entity with new values (id must be 1)
   * @return the merged entity
   */
  @Transactional
  public CostControlConfigEntity save(CostControlConfigEntity updated) {
    updated.id = 1L;
    updated.updatedAt = Instant.now();
    return getEntityManager().merge(updated);
  }

  // ---- helpers -----------------------------------------------------------

  private CostControlConfigEntity buildDefaults() {
    CostControlConfigEntity cfg = new CostControlConfigEntity();
    cfg.id = 1L;
    cfg.importProviderId = defaultImportProvider;
    cfg.exportProviderId = defaultExportProvider;
    cfg.importFetchCron = defaultImportCron;
    cfg.exportFetchCron = defaultExportCron;
    cfg.sampleIntervalSeconds = defaultSampleIntervalSeconds;
    cfg.deadBandWatts = defaultDeadBandWatts;
    cfg.retentionHourlyDays = defaultRetentionHourlyDays;
    cfg.retentionMonthlyYears = defaultRetentionMonthlyYears;
    cfg.updatedAt = Instant.now();
    return cfg;
  }
}
