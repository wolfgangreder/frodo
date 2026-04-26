package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import at.or.reder.frodo.modbus.entity.ExportScheduleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ExportScheduleEntity} persistence operations.
 *
 * <p>Provides upsert semantics so callers never need to distinguish between
 * insert and update — the schedule is always created or replaced atomically.</p>
 */
@ApplicationScoped
public class ExportScheduleRepository implements PanacheRepository<ExportScheduleEntity> {

  /**
   * Finds the schedule for a specific device.
   *
   * @param deviceId the device ID
   * @return Optional containing the schedule, or empty if none configured
   */
  public Optional<ExportScheduleEntity> findByDeviceId(Long deviceId) {
    return find("deviceId", deviceId).firstResultOptional();
  }

  /**
   * Lists all currently enabled schedules (across all devices).
   *
   * @return list of enabled schedule entities
   */
  public List<ExportScheduleEntity> listEnabled() {
    return list("enabled", true);
  }

  /**
   * Creates or updates the schedule for a device.
   *
   * <p>If a schedule already exists for {@code deviceId}, it is updated in-place.
   * Otherwise a new entity is created.</p>
   *
   * @param deviceId    target device ID
   * @param enabled     whether this schedule is active
   * @param blockFrom   time of day to start blocking export
   * @param enableFrom  time of day to re-enable export
   * @param strategy    blocking strategy ({@code null} defaults to
   *                    {@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC})
   * @param limitWatts  fixed watt cap (required when strategy is
   *                    {@link ExportBlockStrategy#FIXED_LIMIT}, otherwise ignored)
   * @param exportToleranceWatts max allowed grid export in Watts for PRICE_CONTROLLED strategy
   * @return the persisted entity
   */
  @Transactional
  public ExportScheduleEntity upsert(Long deviceId, boolean enabled,
    LocalTime blockFrom, LocalTime enableFrom,
    ExportBlockStrategy strategy, Integer limitWatts, Integer exportToleranceWatts) {

    ExportScheduleEntity entity = findByDeviceId(deviceId)
      .orElseGet(ExportScheduleEntity::new);

    entity.deviceId   = deviceId;
    entity.enabled    = enabled;
    entity.blockFrom  = blockFrom;
    entity.enableFrom = enableFrom;
    entity.strategy   = strategy != null ? strategy : ExportBlockStrategy.ZERO_EXPORT_DYNAMIC;
    entity.limitWatts = limitWatts;
    entity.exportToleranceWatts = exportToleranceWatts != null ? exportToleranceWatts : 50;
    persist(entity);
    return entity;
  }

  /**
   * Deletes the schedule for a device (if one exists).
   *
   * @param deviceId the device ID
   * @return {@code true} if a schedule was deleted, {@code false} if none existed
   */
  @Transactional
  public boolean deleteByDeviceId(Long deviceId) {
    return delete("deviceId", deviceId) > 0;
  }
}
