package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import at.or.reder.frodo.modbus.entity.ExportScheduleEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.repository.ExportScheduleRepository;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import at.or.reder.frodo.solarapi.SolarApiMetricsService;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled service that enforces daily recurring grid-export block windows.
 *
 * <p>Every minute this service:</p>
 * <ol>
 *   <li>Loads all enabled {@link ExportScheduleEntity} rows from the database.</li>
 *   <li>For each schedule, determines whether the current wall-clock time falls
 *       inside the configured block window.</li>
 *   <li>When inside the block window: applies the configured blocking strategy
 *       every tick — either dynamic zero-export (reads grid power from the
 *       Fronius Solar API, requires {@code frodo.solar-api.enabled=true}) or a
 *       fixed watt-cap limit (no Solar API needed).  Written every tick because
 *       the dynamic variant tracks changing house load, and the fixed variant
 *       re-affirms the limit for reliability.</li>
 *   <li>When outside the block window: re-enables export by writing
 *       {@code WMaxLim_Ena = 0}, but only on the first tick after the transition
 *       (transition-only to avoid unnecessary Modbus writes).</li>
 * </ol>
 *
 * <p>The in-memory {@code lastApplied} cache tracks whether the export is
 * currently blocked per device. A {@code null} entry means the state is unknown
 * (JVM restart), causing the service to apply the correct state on the next tick.</p>
 *
 * <p>Block-window semantics (see also {@link #isInBlockWindow}):</p>
 * <ul>
 *   <li>{@code blockFrom} &lt; {@code enableFrom}: same-day window
 *       (e.g. 11:00–15:00).</li>
 *   <li>{@code blockFrom} &gt; {@code enableFrom}: crosses midnight
 *       (e.g. 22:00–06:00).</li>
 *   <li>{@code blockFrom} == {@code enableFrom}: always blocked.</li>
 * </ul>
 *
 * <p>Requires {@code frodo.modbus.write-enabled=true}. When write is disabled the
 * scheduler logs a warning and skips all devices for that tick.</p>
 *
 * <p>If the Solar API is not enabled or has not yet delivered data for an inverter
 * configured with {@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC}, the dynamic
 * limit computation is skipped (logged as error).
 * {@link ExportBlockStrategy#FIXED_LIMIT} schedules do not use the Solar API;
 * they convert a configurable watt cap to a percentage of WMax (hard cap).</p>
 *
 * <p><b>Manual override:</b> When the user manually disables the block via the
 * REST endpoint ({@link #notifyManualOverride}), the device is added to an
 * in-memory set and the scheduler will skip re-applying the block until the
 * block window ends or a schedule change is saved. This prevents the scheduler
 * from overriding a deliberate user action within the same block window.</p>
 */
@ApplicationScoped
public class ExportSchedulerService {

  private static final Logger LOG = Logger.getLogger(ExportSchedulerService.class);

  /** Default fixed power cap used when no per-device {@code limitWatts} is set. */
  private static final int DEFAULT_FIXED_LIMIT_WATTS = 500;

  private volatile boolean shuttingDown = false;

  /**
   * Last written blocked-state per device. {@code true} = export is blocked,
   * {@code false} = export is enabled. Absent = state unknown (apply on next tick).
   */
  private final Map<Long, Boolean> lastApplied = new ConcurrentHashMap<>();

  /**
   * Devices for which the user has manually re-enabled export while inside a
   * block window. The scheduler skips these devices (does not re-apply the block)
   * until the block window ends or the schedule is changed (which calls
   * {@link #invalidateCache}).
   */
  private final Set<Long> manuallyEnabled = ConcurrentHashMap.newKeySet();

  @Inject
  ExportScheduleRepository scheduleRepository;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @Inject
  SunSpecService sunSpecService;

  @Inject
  SolarApiMetricsService solarApiMetricsService;

  @ConfigProperty(name = "quarkus.hibernate-orm.enabled", defaultValue = "true")
  boolean hibernateEnabled;

  @ConfigProperty(name = "frodo.modbus.enabled", defaultValue = "true")
  boolean modbusEnabled;

  // ========== Lifecycle ==========

  void onStop(@Observes ShutdownEvent event) {
    shuttingDown = true;
    LOG.info("Shutdown received, stopping export schedule service");
  }

  // ========== Scheduler ==========

  /**
   * Runs every minute, starting 45 seconds after startup, and applies the
   * correct export-block state to all devices with an active schedule.
   *
   * <p>Database reads are performed in short, dedicated transactions
   * ({@link #loadEnabledSchedules()} and {@link #loadDevice(Long)}) so that
   * no DB connection is held open during the potentially slow Modbus I/O.</p>
   */
  @Scheduled(every = "1m", delayed = "45s", identity = "export-schedule-check")
  void checkSchedules() {
    if (shuttingDown) {
      LOG.debug("Skipping export schedule check: shutting down");
      return;
    }
    if (!hibernateEnabled) {
      LOG.debug("Skipping export schedule check: Hibernate ORM disabled");
      return;
    }
    if (!modbusEnabled) {
      LOG.debug("Skipping export schedule check: Modbus disabled");
      return;
    }
    if (!sunSpecService.isWriteEnabled()) {
      LOG.debug("Skipping export schedule check: write operations disabled");
      return;
    }

    // Transaction 1: read schedule rows only — closed before any Modbus I/O
    List<ExportScheduleEntity> schedules = loadEnabledSchedules();
    if (schedules.isEmpty()) {
      return;
    }

    // Truncate to minute for stable comparison throughout the loop
    LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
    LOG.debugf("Export schedule check: %d active schedule(s), time=%s", schedules.size(), now);

    for (ExportScheduleEntity schedule : schedules) {
      if (shuttingDown) break;
      applyScheduleIfChanged(schedule, now);
    }
  }

  /**
   * Loads all enabled schedules in a short read-only transaction.
   * The returned entities are detached; their eagerly-loaded fields remain accessible.
   */
  @Transactional
  List<ExportScheduleEntity> loadEnabledSchedules() {
    return scheduleRepository.listEnabled();
  }

  /**
   * Loads a single device by ID in a short read-only transaction.
   * Returns {@code null} if the device does not exist.
   */
  @Transactional
  ModbusDeviceEntity loadDevice(Long deviceId) {
    return deviceRepository.findById(deviceId);
  }

  /**
   * Loads the first enabled Smart Meter for the given parent device in a short
   * read-only transaction. Returns {@code null} if none is found.
   *
   * <p>Kept for potential future use; not called by the scheduler since
   * {@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC} now uses Solar API data.</p>
   *
   * @param parentDeviceId the inverter device ID
   * @return the Smart Meter entity, or null
   */
  @Transactional
  ModbusDeviceEntity loadSmartMeter(Long parentDeviceId) {
    return deviceRepository.findSmartMeterForDevice(parentDeviceId).orElse(null);
  }

  // ========== Package-private helpers (called by REST resource) ==========

  /**
   * Clears the cached last-applied state for a device.
   *
   * <p>Call this after a schedule is created, updated, or deleted so the next
   * scheduler tick re-evaluates and applies the correct state immediately.</p>
   *
   * @param deviceId the device whose cached state should be invalidated
   */
  public void invalidateCache(Long deviceId) {
    lastApplied.remove(deviceId);
    manuallyEnabled.remove(deviceId); // schedule change clears manual override
  }

  /**
   * Notifies the scheduler that the user has manually toggled the power limit
   * for a device via the REST API.
   *
   * <p>When {@code blocked} is {@code false} (user re-enabled export), the device
   * is added to the manual-override set so the scheduler will not re-apply the
   * block until the block window ends or the schedule is changed.</p>
   *
   * <p>When {@code blocked} is {@code true} (user manually activated the block),
   * the device is removed from the manual-override set and {@code lastApplied} is
   * updated so the scheduler treats it as already blocked.</p>
   *
   * @param deviceId device whose state was changed by the user
   * @param blocked  {@code true} if the user activated the block, {@code false} if disabled
   */
  public void notifyManualOverride(Long deviceId, boolean blocked) {
    if (blocked) {
      manuallyEnabled.remove(deviceId);
      lastApplied.put(deviceId, true);
    } else {
      manuallyEnabled.add(deviceId);
      lastApplied.put(deviceId, false);
    }
  }

  // ========== Static helpers ==========

  /**
   * Determines whether the given time falls inside a block window.
   *
   * <p>Three cases:</p>
   * <ul>
   *   <li>{@code blockFrom == enableFrom}: always in window.</li>
   *   <li>{@code blockFrom < enableFrom}: in window when
   *       {@code blockFrom <= now < enableFrom} (same-day).</li>
   *   <li>{@code blockFrom > enableFrom}: in window when
   *       {@code now >= blockFrom || now < enableFrom} (crosses midnight).</li>
   * </ul>
   *
   * @param now        current time (seconds ignored; truncate before calling)
   * @param blockFrom  inclusive start of the block window
   * @param enableFrom exclusive end of the block window
   * @return {@code true} if {@code now} is inside the block window
   */
  public static boolean isInBlockWindow(LocalTime now, LocalTime blockFrom, LocalTime enableFrom) {
    if (blockFrom.equals(enableFrom)) {
      return true; // degenerate: entire day is blocked
    }
    if (blockFrom.isBefore(enableFrom)) {
      // Same-day window: [blockFrom, enableFrom)
      return !now.isBefore(blockFrom) && now.isBefore(enableFrom);
    } else {
      // Crosses midnight: [blockFrom, 24:00) ∪ [00:00, enableFrom)
      return !now.isBefore(blockFrom) || now.isBefore(enableFrom);
    }
  }

  // ========== Private ==========

  private void applyScheduleIfChanged(ExportScheduleEntity schedule, LocalTime now) {
    boolean shouldBlock = isInBlockWindow(now, schedule.blockFrom, schedule.enableFrom);
    Boolean wasBlocked = lastApplied.get(schedule.deviceId);

    if (!shouldBlock) {
      // Block window ended — clear manual override so the next window starts fresh
      manuallyEnabled.remove(schedule.deviceId);
      // Re-enable: only write on transition (blocked or unknown → enabled)
      if (Boolean.FALSE.equals(wasBlocked)) {
        return; // already enabled, nothing to do
      }
      applyReEnable(schedule);
    } else {
      // Inside block window: skip if the user has manually re-enabled export
      if (manuallyEnabled.contains(schedule.deviceId)) {
        LOG.debugf(
          "Skipping scheduler block for device %d: user has manually re-enabled export",
          schedule.deviceId);
        return;
      }
      // Block: write every tick (dynamic tracks changing load; fixed re-affirms limit)
      if (schedule.strategy == ExportBlockStrategy.FIXED_LIMIT) {
        applyFixedLimitBlock(schedule);
      } else {
        applyDynamicBlock(schedule);
      }
    }
  }

  /**
   * Deactivates the power limit ({@code WMaxLim_Ena = 0}) to re-enable grid export.
   * Only called on the first tick after a transition from blocked to unblocked.
   */
  private void applyReEnable(ExportScheduleEntity schedule) {
    ModbusDeviceEntity device = loadDevice(schedule.deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping re-enable for device %d: not found or disabled", schedule.deviceId);
      return;
    }
    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      sunSpecService.setPowerLimit(address, 0, false);
      lastApplied.put(schedule.deviceId, false);
      LOG.infof("Schedule applied: device=%d (%s), export=ENABLED",
        schedule.deviceId, device.name);
    } catch (Exception ex) {
      // Do not update cache — will retry on next tick
      LOG.errorf(ex, "Failed to re-enable export for device %d (%s): %s",
        schedule.deviceId, device.name, ex.getMessage());
    }
  }

  /**
   * Computes the dynamic zero-export limit from Fronius Solar API site data and
   * writes it to the inverter. Called every scheduler tick while the block window
   * is active.
   *
   * <p><b>Primary formula</b> (used when {@code P_Load} is available):</p>
   * <pre>
   *   targetWatts = -P_Load + (P_Battery ?? 0)
   * </pre>
   * <ul>
   *   <li>{@code -P_Load} = house consumption (P_Load is typically negative)</li>
   *   <li>{@code +P_Battery} positive = battery charging → inverter must cover more;
   *       negative = battery discharging → inverter needs to cover less</li>
   * </ul>
   *
   * <p><b>Fallback formula</b> (used when {@code P_Load} is null):</p>
   * <pre>
   *   houseLoad = P_PV + effectiveGridW
   * </pre>
   * where {@code effectiveGridW = 0} when {@code 0 &lt; P_Grid &lt; 100 W}
   * (100 W dead-band on grid import to suppress noise near zero-export).
   *
   * <p>If the Solar API has not delivered data yet, the tick is skipped with an
   * error log.</p>
   */
  private void applyDynamicBlock(ExportScheduleEntity schedule) {
    ModbusDeviceEntity device = loadDevice(schedule.deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping block for device %d: not found or disabled", schedule.deviceId);
      return;
    }

    PowerFlowRealtimeData solarData = solarApiMetricsService.getLastData();
    if (solarData == null || solarData.getSite() == null) {
      LOG.errorf(
        "No Solar API data available for device %d (%s) — "
          + "enable frodo.solar-api.enabled=true and verify the inverter host is reachable. "
          + "Skipping dynamic zero-export limit.",
        schedule.deviceId, device.name);
      return;
    }

    PowerFlowRealtimeData.SiteData site = solarData.getSite();
    Double gridW = site.getGridPowerWatts();
    if (gridW == null) {
      LOG.errorf("Solar API P_Grid unavailable for device %d (%s) — skipping tick.",
        schedule.deviceId, device.name);
      return;
    }

    Double loadW    = site.getLoadPowerWatts();
    Double batteryW = site.getBatteryPowerWatts();
    double pvW      = site.getPVPowerWatts() != null ? site.getPVPowerWatts() : 0.0;

    // 100 W dead-band: small grid imports (< 100 W) are treated as zero to avoid
    // chasing noise near zero-export. Exports (negative P_Grid) are always used as-is.
    double effectiveGridW = (gridW > 0.0 && gridW < 100.0) ? 0.0 : gridW;

    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      final int limitPct;
      final String formulaDesc;

      if (loadW != null) {
        // Primary: target = house consumption + battery charging demand
        double battW       = batteryW != null ? batteryW : 0.0;
        double targetWatts = -loadW + battW;
        limitPct    = sunSpecService.computeLimitPctFromWatts(address, targetWatts);
        formulaDesc = String.format(
          "PRIMARY -P_Load(%.1f)+P_Batt(%.1f)=%.1f W [P_Grid=%.1f W eff=%.1f W, P_PV=%.1f W]",
          loadW, battW, targetWatts, gridW, effectiveGridW, pvW);
      } else {
        // Fallback: houseLoad estimate from PV output + effective grid power
        limitPct    = sunSpecService.computeZeroExportLimitPct(address, effectiveGridW, pvW);
        formulaDesc = String.format(
          "FALLBACK P_PV(%.1f)+P_Grid_eff(%.1f) [P_Grid=%.1f W, P_Battery=%s W]",
          pvW, effectiveGridW, gridW,
          batteryW != null ? String.format("%.1f", batteryW) : "n/a");
      }

      sunSpecService.setPowerLimit(address, limitPct, true);
      lastApplied.put(schedule.deviceId, true);
      LOG.infof(
        "Schedule applied: device=%d (%s), export=BLOCKED (ZERO_EXPORT_DYNAMIC, %s → %d%%)",
        schedule.deviceId, device.name, formulaDesc, Integer.valueOf(limitPct));
    } catch (Exception ex) {
      // Do not update cache — will retry on next tick
      LOG.errorf(ex, "Failed to apply dynamic export limit for device %d (%s): %s",
        schedule.deviceId, device.name, ex.getMessage());
    }
  }

  /**
   * Writes a fixed watt-cap limit to halt most inverter output (configurable block).
   *
   * <p>Uses {@link ExportScheduleEntity#limitWatts} if set; otherwise falls back to
   * {@link #DEFAULT_FIXED_LIMIT_WATTS} (500 W). The watt value is converted to a
   * percentage of the inverter's rated power (WMax from Model 120) so the inverter
   * is allowed to produce just enough to cover the cap.  Written every tick to
   * re-affirm the limit after an inverter restart or communication hiccup.</p>
   */
  private void applyFixedLimitBlock(ExportScheduleEntity schedule) {
    ModbusDeviceEntity device = loadDevice(schedule.deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping fixed-limit block for device %d: not found or disabled",
        schedule.deviceId);
      return;
    }

    int limitWatts = schedule.limitWatts != null ? schedule.limitWatts : DEFAULT_FIXED_LIMIT_WATTS;
    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      int limitPct = sunSpecService.computeFixedLimitPct(address, limitWatts);
      sunSpecService.setPowerLimit(address, limitPct, true);
      lastApplied.put(schedule.deviceId, true);
      LOG.infof(
        "Schedule applied: device=%d (%s), export=BLOCKED (FIXED_LIMIT, %d W → %d%%)",
        schedule.deviceId, device.name, Integer.valueOf(limitWatts), Integer.valueOf(limitPct));
    } catch (Exception ex) {
      // Do not update cache — will retry on next tick
      LOG.errorf(ex, "Failed to apply fixed-limit export for device %d (%s): %s",
        schedule.deviceId, device.name, ex.getMessage());
    }
  }
}
