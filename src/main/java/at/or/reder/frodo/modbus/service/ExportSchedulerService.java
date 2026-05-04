package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.gpio.GpioService;
import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.ExportBlockStrategy;
import at.or.reder.frodo.modbus.entity.ExportScheduleEntity;
import at.or.reder.frodo.modbus.entity.GpioDeviceAssignmentEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.entity.PriceControlEntity;
import at.or.reder.frodo.modbus.model.DeviceType;
import at.or.reder.frodo.modbus.repository.ExportScheduleRepository;
import at.or.reder.frodo.modbus.repository.GpioDeviceAssignmentRepository;
import at.or.reder.frodo.modbus.repository.MarketPriceRepository;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.repository.PriceControlRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import at.or.reder.frodo.solarapi.SolarApiMetricsService;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  MarketPriceRepository marketPriceRepository;

  @Inject
  SunSpecService sunSpecService;

  @Inject
  PriceControlRepository priceControlRepository;

  @Inject
  SolarApiMetricsService solarApiMetricsService;

  @Inject
  GpioService gpioService;

  @Inject
  GpioDeviceAssignmentRepository gpioAssignmentRepository;

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

    // Build set of device IDs that have an explicit per-device schedule so that
    // the global price-control path can skip them to avoid double-applying limits.
    Set<Long> scheduledDeviceIds = new HashSet<>();
    for (ExportScheduleEntity s : schedules) {
      scheduledDeviceIds.add(s.deviceId);
    }

    // Apply per-device schedules
    if (!schedules.isEmpty()) {
      LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
      LOG.debugf("Export schedule check: %d active schedule(s), time=%s", schedules.size(), now);
      for (ExportScheduleEntity schedule : schedules) {
        if (shuttingDown) break;
        applyScheduleIfChanged(schedule, now);
      }
    }

    // Apply global price-controlled export limiting to all enabled inverter devices
    // that do not have their own per-device schedule.
    if (!shuttingDown) {
      PriceControlEntity globalConfig = loadGlobalPriceControl();
      if (globalConfig != null) {
        applyGlobalPriceControl(globalConfig, scheduledDeviceIds);
      }
    }
  }

  /**
   * Called by {@link SolarApiMetricsService} after every successful Solar API scrape
   * (default: every 15 s). Immediately recalculates and re-applies the dynamic power
   * limit for all devices that are <em>currently blocking</em> and whose strategy
   * tracks changing house load in real time:
   * {@link ExportBlockStrategy#ZERO_EXPORT_DYNAMIC}, {@link ExportBlockStrategy#PRICE_CONTROLLED},
   * and the global price-control mechanism.
   *
   * <p>{@link ExportBlockStrategy#FIXED_LIMIT} devices are deliberately skipped because
   * their limit is constant — re-affirming it every 15 s would generate unnecessary
   * Modbus writes. The 1-minute scheduler tick ({@link #checkSchedules()}) already
   * re-affirms fixed limits at a reasonable cadence.</p>
   *
   * <p>Only devices where {@code lastApplied == true} (currently blocking) are processed.
   * Re-enable transitions are handled exclusively by {@link #checkSchedules()} to
    * avoid duplicate Modbus writes.</p>
   */
  @ActivateRequestContext
  public void onSolarDataUpdated() {
    if (shuttingDown || !hibernateEnabled || !modbusEnabled || !sunSpecService.isWriteEnabled()) {
      return;
    }

    List<ExportScheduleEntity> schedules = loadEnabledSchedules();

    Set<Long> scheduledDeviceIds = new HashSet<>();
    for (ExportScheduleEntity s : schedules) {
      scheduledDeviceIds.add(s.deviceId);
    }

    // Recalculate dynamic limits for currently-blocking per-device schedules
    for (ExportScheduleEntity schedule : schedules) {
      if (shuttingDown) break;
      Long deviceId = schedule.deviceId;

      // Only recalculate if currently blocking; re-enable handled by 1-min tick
      if (!Boolean.TRUE.equals(lastApplied.get(deviceId))) {
        continue;
      }
      if (manuallyEnabled.contains(deviceId)) {
        continue;
      }
      // FIXED_LIMIT: constant limit; re-affirmation at 1-min cadence is sufficient
      if (schedule.strategy == ExportBlockStrategy.FIXED_LIMIT) {
        continue;
      }
      // PRICE_CONTROLLED_GPIO: uses GPIO pins, not Modbus — skip Solar API recalculation
      if (schedule.strategy == ExportBlockStrategy.PRICE_CONTROLLED_GPIO) {
        continue;
      }

      if (schedule.strategy == ExportBlockStrategy.PRICE_CONTROLLED) {
        applyPriceControlledBlock(schedule);
      } else {
        // ZERO_EXPORT_DYNAMIC
        applyDynamicBlock(schedule);
      }
    }

    if (shuttingDown) return;

    // Global price-control: recalculate for currently-blocking non-scheduled inverters
    PriceControlEntity globalConfig = loadGlobalPriceControl();
    if (globalConfig == null || !globalConfig.enabled) {
      return;
    }

    var priceOpt = marketPriceRepository.findCurrent();
    if (priceOpt.isEmpty() || !shouldBlockForPrice(priceOpt.get().priceCt)) {
      return;
    }

    double priceCt     = priceOpt.get().priceCt;
    int toleranceWatts = globalConfig.exportToleranceWatts;
    List<ModbusDeviceEntity> inverters = loadEnabledInverters();

    for (ModbusDeviceEntity device : inverters) {
      if (shuttingDown) break;
      Long deviceId = device.id;

      if (scheduledDeviceIds.contains(deviceId)) continue;
      if (manuallyEnabled.contains(deviceId)) continue;
      // Only recalculate if currently blocking; re-enable handled by 1-min tick
      if (!Boolean.TRUE.equals(lastApplied.get(deviceId))) continue;

      String strategyLabel = String.format(
        "GLOBAL_PRICE_CONTROLLED, price=%.4f ct/kWh, tol=%d W",
        priceCt, Integer.valueOf(toleranceWatts));
      applyDynamicLimitWithTolerance(deviceId, device, toleranceWatts, strategyLabel);
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

  /**
   * Loads the global price-control singleton in a short read-only transaction.
   * Returns {@code null} if the feature has never been configured (treat as disabled).
   */
  @Transactional
  PriceControlEntity loadGlobalPriceControl() {
    return priceControlRepository.findSingleton().orElse(null);
  }

  /**
   * Loads enabled inverter candidates in a short read-only transaction.
   *
   * <p>Includes devices explicitly typed as {@link DeviceType#INVERTER} as well as
   * root-level devices with no type set (manually configured before automatic
   * type-detection was introduced). See
   * {@link at.or.reder.frodo.modbus.repository.ModbusDeviceRepository#listEnabledInverterCandidates()}.</p>
   *
   * @return list of enabled inverter candidates, sorted by ID
   */
  @Transactional
  List<ModbusDeviceEntity> loadEnabledInverters() {
    return deviceRepository.listEnabledInverterCandidates();
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
   * Determines whether export should be blocked based on the current market price.
   *
   * <p>Export is blocked whenever the price is strictly negative — i.e. the grid
   * operator is paying producers to consume. When the price is zero or positive the
   * inverter may export freely.</p>
   *
   * <p>Note: the allowed export <em>amount</em> during a negative-price period is
   * controlled separately by {@code exportToleranceWatts} in
   * {@link at.or.reder.frodo.modbus.entity.ExportScheduleEntity}, which sets a small
   * Watt cap rather than a hard zero cutoff.</p>
   *
   * @param priceCt current market price in ct/kWh (may be negative)
   * @return {@code true} if export should be capped (price is negative)
   */
  public static boolean shouldBlockForPrice(double priceCt) {
    return priceCt < 0;
  }

  /**
   * Applies the grid-import dead-band to suppress noise near zero-export.
   *
   * <p>Small positive grid imports ({@code 0 < gridW < toleranceWatts}) are
   * treated as zero so the controller does not chase measurement noise.
   * Negative values (grid export) and large positive imports are used as-is.</p>
   *
   * @param gridW          grid power in W (positive = import, negative = export)
   * @param toleranceWatts dead-band threshold in W (same as export tolerance)
   * @return effective grid power after dead-band suppression
   */
  static double computeEffectiveGridW(double gridW, int toleranceWatts) {
    return (gridW > 0.0 && gridW < toleranceWatts) ? 0.0 : gridW;
  }

  /**
   * Primary formula: target inverter output to cover load and battery demand
   * plus a small export buffer.
   *
   * <pre>
   *   targetWatts = -P_Load - P_Battery + toleranceWatts
   * </pre>
   * <p>Both {@code loadW} and {@code battW} are negative in the Fronius API when
   * consuming/charging, so their negated sum equals total demand.</p>
   *
   * @param loadW          site load power in W (negative = consuming)
   * @param battW          battery power in W (negative = charging, positive = discharging)
   * @param toleranceWatts additional export buffer in W
   * @return target inverter output in W
   */
  static double computeTargetWatts(double loadW, double battW, int toleranceWatts) {
    return -loadW - battW + toleranceWatts;
  }

  /**
   * Fallback formula: target inverter output estimated from PV output and
   * effective grid power (used when {@code P_Load} is unavailable).
   *
   * <pre>
   *   targetWatts = P_PV + effectiveGridW + toleranceWatts
   * </pre>
   *
   * @param pvW            PV production power in W
   * @param effectiveGridW grid power after dead-band suppression (from {@link #computeEffectiveGridW})
   * @param toleranceWatts additional export buffer in W
   * @return target inverter output in W
   */
  static double computeTargetWattsFallback(double pvW, double effectiveGridW, int toleranceWatts) {
    return pvW + effectiveGridW + toleranceWatts;
  }

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
    // PRICE_CONTROLLED_GPIO uses GPIO pins instead of Modbus — evaluated every tick
    if (schedule.strategy == ExportBlockStrategy.PRICE_CONTROLLED_GPIO) {
      if (manuallyEnabled.contains(schedule.deviceId)) {
        LOG.debugf(
          "Skipping GPIO block for device %d: user has manually re-enabled export",
          schedule.deviceId);
        return;
      }
      applyPriceControlledGpioBlock(schedule);
      return;
    }

    // PRICE_CONTROLLED uses market price instead of a time window — evaluated every tick
    if (schedule.strategy == ExportBlockStrategy.PRICE_CONTROLLED) {
      if (manuallyEnabled.contains(schedule.deviceId)) {
        LOG.debugf(
          "Skipping scheduler block for device %d: user has manually re-enabled export",
          schedule.deviceId);
        return;
      }
      applyPriceControlledBlock(schedule);
      return;
    }

    boolean shouldBlock = isInBlockWindow(now, schedule.blockFrom, schedule.enableFrom);
    Boolean wasBlocked = lastApplied.get(schedule.deviceId);

    if (!shouldBlock) {
      // Block window ended — clear manual override so the next window starts fresh
      manuallyEnabled.remove(schedule.deviceId);
      // Re-enable: only write on transition (blocked or unknown → enabled)
      if (Boolean.FALSE.equals(wasBlocked)) {
        return; // already enabled, nothing to do
      }
      applyReEnable(schedule.deviceId);
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
   *
   * @param deviceId the inverter device ID to re-enable
   */
  private void applyReEnable(Long deviceId) {
    ModbusDeviceEntity device = loadDevice(deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping re-enable for device %d: not found or disabled", deviceId);
      return;
    }
    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      sunSpecService.setPowerLimit(address, 0, false);
      lastApplied.put(deviceId, false);
      LOG.infof("Schedule applied: device=%d (%s), export=ENABLED",
        deviceId, device.name);
    } catch (Exception ex) {
      // Do not update cache — will retry on next tick
      LOG.errorf(ex, "Failed to re-enable export for device %d (%s): %s",
        deviceId, device.name, ex.getMessage());
    }
  }

  /**
   * Computes the dynamic zero-export limit from Fronius Solar API site data and
   * writes it to the inverter. Delegates to
   * {@link #applyDynamicLimitWithTolerance} with {@code toleranceWatts = 0}.
   *
   * <p>See {@link #applyDynamicLimitWithTolerance} for full formula documentation.</p>
   */
  private void applyDynamicBlock(ExportScheduleEntity schedule) {
    ModbusDeviceEntity device = loadDevice(schedule.deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping block for device %d: not found or disabled", schedule.deviceId);
      return;
    }
    applyDynamicLimitWithTolerance(schedule.deviceId, device, 0, "ZERO_EXPORT_DYNAMIC");
  }

  /**
   * Shared helper: computes the dynamic inverter power limit from Fronius Solar API
   * site data and writes it to the inverter. Called every scheduler tick while a
   * block window is active.
   *
   * <p><b>Primary formula</b> (used when {@code P_Load} is available):</p>
   * <pre>
   *   targetWatts = -(P_Load + P_Battery) + toleranceWatts
   * </pre>
   * <p>Both {@code P_Load} and {@code P_Battery} are negative in the Fronius API
   * when consuming/charging, so their negated sum is the total demand the inverter
   * must cover, and adding {@code toleranceWatts} allows a small controlled export
   * on top:</p>
   * <ul>
   *   <li>{@code -P_Load}: house consumption in W (P_Load is negative = consuming)</li>
   *   <li>{@code -P_Battery}: battery charging demand in W (negative when charging,
   *       so negating adds that demand; positive when discharging, negating subtracts
   *       it — the battery covers that portion of the load)</li>
   *   <li>{@code +toleranceWatts}: allowed export buffer (0 = strict zero-export;
   *       &gt;0 = small intentional export, e.g. 50 W for price-controlled mode)</li>
   * </ul>
   *
   * <p><b>Fallback formula</b> (used when {@code P_Load} is null):</p>
   * <pre>
   *   targetWatts = P_PV + effectiveGridW + toleranceWatts
   * </pre>
    * where {@code effectiveGridW = 0} when {@code 0 &lt; P_Grid &lt; toleranceWatts}
    * (dead-band on grid import to suppress noise near zero-export).
   *
   * <p>If the Solar API has not delivered data yet, the tick is skipped with an
   * error log.</p>
   *
   * @param deviceId       the inverter device ID (used for caching and logging)
   * @param device         the loaded, enabled inverter device
   * @param toleranceWatts additional watts added on top of the computed target
   *                       (0 = strict zero-export; &gt;0 = small allowed export buffer)
   * @param strategyLabel  label embedded in the log line (e.g. "ZERO_EXPORT_DYNAMIC"
   *                       or "PRICE_CONTROLLED, price=−0.05 ct/kWh, tol=50 W")
   */
  private void applyDynamicLimitWithTolerance(Long deviceId,
                                              ModbusDeviceEntity device,
                                              int toleranceWatts,
                                              String strategyLabel) {
    PowerFlowRealtimeData solarData = solarApiMetricsService.getLastData();
    if (solarData == null || solarData.getSite() == null) {
      LOG.errorf(
        "No Solar API data available for device %d (%s) — "
          + "enable frodo.solar-api.enabled=true and verify the inverter host is reachable. "
          + "Skipping dynamic zero-export limit.",
        deviceId, device.name);
      return;
    }

    PowerFlowRealtimeData.SiteData site = solarData.getSite();
    Double gridW = site.getGridPowerWatts();
    if (gridW == null) {
      LOG.errorf("Solar API P_Grid unavailable for device %d (%s) — skipping tick.",
        deviceId, device.name);
      return;
    }

    Double loadW    = site.getLoadPowerWatts();
    Double batteryW = site.getBatteryPowerWatts();
    double pvW      = site.getPVPowerWatts() != null ? site.getPVPowerWatts() : 0.0;

    // dead-band: small grid imports (< toleranceWatts) are treated as zero to avoid
    // chasing noise near zero-export. Exports (negative P_Grid) are always used as-is.
    double effectiveGridW = computeEffectiveGridW(gridW, toleranceWatts);

    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      final int limitPct;
      final String formulaDesc;

      if (loadW != null) {
        // Primary: target = -(P_Load + P_Battery) + toleranceWatts
        // Both are negative in the Fronius API when consuming/charging,
        // so their negated sum is the total watts the inverter must produce.
        // When battery is discharging (battW > 0), negating it subtracts the
        // discharge contribution — the battery covers that portion of the load.
        double battW       = batteryW != null ? batteryW : 0.0;
        double targetWatts = computeTargetWatts(loadW, battW, toleranceWatts);
        limitPct    = sunSpecService.computeLimitPctFromWatts(address, targetWatts);
        formulaDesc = String.format(
          "PRIMARY -(P_Load(%.1f)+P_Batt(%.1f))+tol(%d)=%.1f W [P_Grid=%.1f W eff=%.1f W, P_PV=%.1f W]",
          loadW, battW, Integer.valueOf(toleranceWatts), targetWatts, gridW, effectiveGridW, pvW);
      } else {
        // Fallback: houseLoad estimate from PV output + effective grid power
        double targetWatts = computeTargetWattsFallback(pvW, effectiveGridW, toleranceWatts);
        limitPct    = sunSpecService.computeLimitPctFromWatts(address, targetWatts);
        formulaDesc = String.format(
          "FALLBACK P_PV(%.1f)+P_Grid_eff(%.1f)+tol(%d)=%.1f W [P_Grid=%.1f W, P_Battery=%s W]",
          pvW, effectiveGridW, Integer.valueOf(toleranceWatts), targetWatts, gridW,
          batteryW != null ? String.format("%.1f", batteryW) : "n/a");
      }

      sunSpecService.setPowerLimit(address, limitPct, true);
      lastApplied.put(deviceId, true);
      LOG.infof(
        "Schedule applied: device=%d (%s), export=BLOCKED (%s, %s → %d%%)",
        deviceId, device.name, strategyLabel, formulaDesc, Integer.valueOf(limitPct));
    } catch (Exception ex) {
      // Do not update cache — will retry on next tick
      LOG.errorf(ex, "Failed to apply dynamic export limit for device %d (%s): %s",
        deviceId, device.name, ex.getMessage());
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

  /**
   * Applies price-controlled export blocking based on aWATTar AT market prices.
   *
   * <p>Every scheduler tick while inside the block window, the current market price
   * is fetched from the database. If the price is negative, the dynamic limit is
   * computed via {@link #applyDynamicLimitWithTolerance} using
   * {@link at.or.reder.frodo.modbus.entity.ExportScheduleEntity#exportToleranceWatts}
   * as the export buffer on top of house load + battery demand — so the inverter
   * covers all consumption and may export up to {@code exportToleranceWatts} extra
   * (default 50 W). When the price is zero or positive, export is re-enabled fully.</p>
   *
   * <p><b>Logic:</b></p>
   * <pre>
   *   if priceCt &lt; 0  → targetWatts = -(P_Load + P_Battery) + exportToleranceWatts
   *   if priceCt &ge; 0  → re-enable export (WMaxLim_Ena = 0)
   * </pre>
   */
  private void applyPriceControlledBlock(ExportScheduleEntity schedule) {
    ModbusDeviceEntity device = loadDevice(schedule.deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping price-controlled block for device %d: not found or disabled",
        schedule.deviceId);
      return;
    }

    var priceOpt = marketPriceRepository.findCurrent();
    if (priceOpt.isEmpty()) {
      LOG.warnf(
        "No market price available for device %d (%s) — skipping price-controlled block. "
          + "Verify the market price scheduler is running.",
        schedule.deviceId, device.name);
      return;
    }

    double priceCt = priceOpt.get().priceCt;

    if (!shouldBlockForPrice(priceCt)) {
      // Price is zero or positive — clear manual override and re-enable on transition only
      manuallyEnabled.remove(schedule.deviceId);
      if (!Boolean.FALSE.equals(lastApplied.get(schedule.deviceId))) {
        applyReEnable(schedule.deviceId);
      }
      return;
    }

    // Price is negative — use dynamic load+battery demand plus a small export buffer
    int toleranceWatts = schedule.exportToleranceWatts != null ? schedule.exportToleranceWatts : 50;
    String strategyLabel = String.format(
      "PRICE_CONTROLLED, price=%.4f ct/kWh, tol=%d W", priceCt, Integer.valueOf(toleranceWatts));
    applyDynamicLimitWithTolerance(schedule.deviceId, device, toleranceWatts, strategyLabel);
  }

  /**
   * Applies price-controlled export blocking using GPIO pins instead of Modbus.
   *
   * <p>Logic:</p>
   * <ol>
   *   <li>Load GPIO pair assignment for this device — if absent, fall back to Modbus</li>
   *   <li>Check pair availability — if unavailable, fall back to Modbus</li>
   *   <li>Check external override switch — if active, skip all control</li>
   *   <li>Fetch market price, determine if export should be blocked</li>
   *   <li>Call {@code gpioService.setBlockState()} (no-op if manual override active)</li>
   * </ol>
   */
  private void applyPriceControlledGpioBlock(ExportScheduleEntity schedule) {
    ModbusDeviceEntity device = loadDevice(schedule.deviceId);
    if (device == null || !device.enabled) {
      LOG.debugf("Skipping GPIO block for device %d: not found or disabled", schedule.deviceId);
      return;
    }

    // Resolve GPIO pair assignment
    Optional<GpioDeviceAssignmentEntity> assignmentOpt = loadGpioAssignment(schedule.deviceId);
    if (assignmentOpt.isEmpty()) {
      LOG.warnf(
        "Device %d (%s) uses PRICE_CONTROLLED_GPIO but has no GPIO pair assigned "
          + "— falling back to Modbus throttling",
        schedule.deviceId, device.name);
      applyPriceControlledBlock(schedule);
      return;
    }
    String pairName = assignmentOpt.get().gpioPairName;

    if (!gpioService.isPairAvailable(pairName)) {
      LOG.warnf(
        "GPIO pair '%s' for device %d (%s) is unavailable "
          + "— falling back to Modbus throttling",
        pairName, schedule.deviceId, device.name);
      applyPriceControlledBlock(schedule);
      return;
    }

    // Check external override switch
    try {
      if (gpioService.isExternalModeActive(pairName)) {
        LOG.infof(
          "External override active on pair '%s' for device %d (%s) "
            + "— skipping all control (GPIO + Modbus)",
          pairName, schedule.deviceId, device.name);
        lastApplied.put(schedule.deviceId, null);
        return;
      }
    } catch (IOException e) {
      LOG.errorf(e, "Failed to read GPIO input for pair '%s': %s — treating external mode as inactive",
        pairName, e.getMessage());
      // Continue — treat external mode as inactive and proceed
    }

    // Fetch price
    var priceOpt = marketPriceRepository.findCurrent();
    if (priceOpt.isEmpty()) {
      LOG.warnf("No market price available for device %d (%s) — skipping GPIO block",
        schedule.deviceId, device.name);
      return;
    }

    double priceCt = priceOpt.get().priceCt;
    boolean shouldBlock = shouldBlockForPrice(priceCt);

    try {
      gpioService.setBlockState(pairName, shouldBlock);
      lastApplied.put(schedule.deviceId, shouldBlock);
      LOG.infof(
        "GPIO applied: pair='%s' device=%d (%s) export=%s price=%.4f ct/kWh",
        pairName, schedule.deviceId, device.name,
        shouldBlock ? "BLOCKED" : "ENABLED", priceCt);
    } catch (IOException e) {
      LOG.errorf(e,
        "GPIO setBlockState failed for pair '%s' device %d (%s): %s — falling back to Modbus",
        pairName, schedule.deviceId, device.name, e.getMessage());
      applyPriceControlledBlock(schedule); // fallback
    }
  }

  /**
   * Loads the GPIO pair assignment for a device in a short read-only transaction.
   */
  @Transactional
  Optional<GpioDeviceAssignmentEntity> loadGpioAssignment(Long deviceId) {
    return gpioAssignmentRepository.findByDeviceId(deviceId);
  }

  /**
   * Applies the global price-controlled export limit to all enabled inverter devices
   * that do not have an explicit per-device schedule configured.
   *
   * <p>Called at the end of every scheduler tick. This method is a no-op when:</p>
   * <ul>
   *   <li>{@link PriceControlEntity#enabled} is {@code false}</li>
   *   <li>No current market price is available in the database</li>
   *   <li>No enabled inverter devices exist</li>
   * </ul>
   *
   * <p>Devices present in {@code scheduledDeviceIds} are skipped to avoid conflicting
   * with their per-device schedule logic.</p>
   *
   * @param config             the global price-control configuration (never null)
   * @param scheduledDeviceIds set of device IDs that have an explicit per-device schedule
   */
  private void applyGlobalPriceControl(PriceControlEntity config, Set<Long> scheduledDeviceIds) {
    if (!config.enabled) {
      return;
    }

    var priceOpt = marketPriceRepository.findCurrent();
    if (priceOpt.isEmpty()) {
      LOG.debug("Global price control: no current market price — skipping tick");
      return;
    }

    double priceCt      = priceOpt.get().priceCt;
    boolean shouldBlock = shouldBlockForPrice(priceCt);
    int toleranceWatts  = config.exportToleranceWatts;

    List<ModbusDeviceEntity> inverters = loadEnabledInverters();
    if (inverters.isEmpty()) {
      return;
    }

    LOG.debugf(
      "Global price control: price=%.4f ct/kWh, block=%b, tolerance=%d W, inverters=%d",
      priceCt, Boolean.valueOf(shouldBlock), Integer.valueOf(toleranceWatts),
      Integer.valueOf(inverters.size()));

    for (ModbusDeviceEntity device : inverters) {
      if (shuttingDown) break;
      Long deviceId = device.id;

      // Skip devices with their own per-device schedule (avoid double-applying)
      if (scheduledDeviceIds.contains(deviceId)) {
        continue;
      }

      if (manuallyEnabled.contains(deviceId)) {
        LOG.debugf(
          "Global price control: skipping device %d — user has manually re-enabled export",
          deviceId);
        continue;
      }

      if (!shouldBlock) {
        // Price is zero or positive — clear manual override and re-enable on transition only
        manuallyEnabled.remove(deviceId);
        if (!Boolean.FALSE.equals(lastApplied.get(deviceId))) {
          applyReEnable(deviceId);
        }
      } else {
        // Price is negative — apply dynamic load+battery demand limit
        String strategyLabel = String.format(
          "GLOBAL_PRICE_CONTROLLED, price=%.4f ct/kWh, tol=%d W",
          priceCt, Integer.valueOf(toleranceWatts));
        applyDynamicLimitWithTolerance(deviceId, device, toleranceWatts, strategyLabel);
      }
    }
  }
}
