package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import at.or.reder.frodo.modbus.entity.MetricsDataEntity;
import at.or.reder.frodo.modbus.entity.MetricsParameterEntity;
import at.or.reder.frodo.modbus.entity.ScrapeStatus;
import at.or.reder.frodo.modbus.metrics.MetricMetadata.ResolvedMetric;
import at.or.reder.frodo.modbus.metrics.MetricMetadataRegistry;
import at.or.reder.frodo.modbus.repository.MetricsConfigRepository;
import at.or.reder.frodo.modbus.repository.MetricsDataRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelRegistry;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Service that manages periodic metrics scraping for configured devices.
 *
 * <p>For each device with an enabled metrics config, this service schedules
 * a periodic task that reads SunSpec model data at the configured interval.
 * Scraped values are:</p>
 * <ul>
 *   <li>Published as Prometheus gauges via Micrometer (for Grafana dashboards)</li>
 *   <li>Optionally persisted to the database for long-term storage</li>
 * </ul>
 *
 * <p>Timer lifecycle is managed per-device: scheduling, rescheduling (on
 * config change), and cancellation (on config disable or device delete).</p>
 */
@ApplicationScoped
public class MetricsScrapingService {

  private static final Logger LOG = Logger.getLogger(MetricsScrapingService.class);

  @Inject
  MetricsConfigRepository configRepository;

  @Inject
  MetricsDataRepository dataRepository;

  @Inject
  SunSpecService sunSpecService;

  @Inject
  MeterRegistry meterRegistry;

  @Inject
  MetricMetadataRegistry metadataRegistry;

  @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "true")
  boolean datasourceActive;

  /**
   * Thread pool for scheduled scraping tasks.
   */
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
    Thread t = new Thread(r, "metrics-scraper");
    t.setDaemon(true);
    return t;
  });

  /**
   * Map of device ID to scheduled future handle.
   */
  private final Map<Long, ScheduledFuture<?>> scheduledTimers = new ConcurrentHashMap<>();

  /**
   * Gauge value cache: deviceId -> fieldKey -> current value.
   * Uses AtomicReference for thread-safe gauge updates.
   */
  private final Map<Long, Map<String, AtomicReference<Double>>> gaugeValues = new ConcurrentHashMap<>();

  /**
   * Tracks registered gauges per device: deviceId -> (gaugeKey -> Meter.Id).
   * Used to unregister stale gauges from the MeterRegistry when a device
   * is rescheduled or cancelled.
   */
  private final Map<Long, Map<String, Meter.Id>> registeredGauges = new ConcurrentHashMap<>();

  /**
   * Per-minute accumulator for sub-minute scrape intervals.
   *
   * <p>When {@code scrapeIntervalSeconds < 60}, scraped values are collected here
   * instead of being written to the DB immediately. On the first scrape in a new
   * minute, the previous minute's accumulated values are averaged and persisted.</p>
   *
   * <p>Structure: deviceId → (modelId_fieldName → accumulator for current minute).</p>
   */
  private final Map<Long, Map<String, FieldAccumulator>> minuteAccumulators = new ConcurrentHashMap<>();

  /**
   * Flag to prevent scrape tasks from executing during shutdown.
   */
  private volatile boolean shuttingDown = false;

  /**
   * On application startup, initialize scraping for all enabled configs.
   */
  void onStart(@Observes StartupEvent event) {
    if (!datasourceActive) {
      LOG.debug("Skipping metrics scraping initialization: datasource inactive");
      return;
    }
    LOG.info("Initializing metrics scraping service");
    try {
      List<MetricsConfigEntity> enabledConfigs = configRepository.findAllEnabled();
      for (MetricsConfigEntity config : enabledConfigs) {
        scheduleDeviceScraping(config);
      }
      LOG.infof("Metrics scraping initialized for %d device(s)", enabledConfigs.size());
    } catch (Exception e) {
      LOG.warnf(e, "Failed to initialize metrics scraping (database may not be available)");
    }
  }

  /**
   * On application shutdown, cancel all scheduled timers, unregister gauges,
   * and shut down the executor.
   */
  void onStop(@Observes ShutdownEvent event) {
    LOG.info("Shutting down metrics scraping service");
    shuttingDown = true;
    scheduledTimers.forEach((deviceId, future) -> {
      future.cancel(true);
      LOG.debugf("Cancelled scraping timer for device %d", deviceId);
    });
    scheduledTimers.clear();

    // Unregister all Micrometer gauges
    int totalRemoved = 0;
    for (Map.Entry<Long, Map<String, Meter.Id>> deviceEntry : registeredGauges.entrySet()) {
      for (Meter.Id meterId : deviceEntry.getValue().values()) {
        if (meterRegistry.remove(meterId) != null) {
          totalRemoved++;
        }
      }
    }
    registeredGauges.clear();
    gaugeValues.clear();
    minuteAccumulators.clear();
    if (totalRemoved > 0) {
      LOG.debugf("Removed %d Micrometer gauge(s) during shutdown", totalRemoved);
    }

    scheduler.shutdownNow();
  }

  /**
   * Schedules (or reschedules) periodic scraping for a device.
   *
   * <p>If scraping is already scheduled for this device, the existing timer
   * is cancelled before creating a new one. If the config is disabled,
   * scraping is cancelled without scheduling a new timer.</p>
   *
   * @param config the metrics config entity (must have parameters loaded)
   */
  public void scheduleDeviceScraping(MetricsConfigEntity config) {
    Long deviceId = config.device.id;

    // Cancel existing timer if any
    cancelDeviceScraping(deviceId);

    if (!config.enabled) {
      LOG.infof("Metrics scraping disabled for device %d", deviceId);
      return;
    }

    long enabledParams = config.parameters.stream().filter(p -> p.enabled).count();
    if (enabledParams == 0) {
      LOG.infof("No enabled parameters for device %d, skipping scrape scheduling", deviceId);
      return;
    }

    // Discover which models are actually present on the device so we only
    // register gauges for available models (prevents phantom metrics in
    // /q/metrics for models that don't exist on this device).
    DeviceAddress address = DeviceAddress.fromEntity(config.device);
    Set<Integer> availableModelIds = discoverAvailableModels(address, deviceId);

    // Register Prometheus gauges for enabled parameters (filtered by availability)
    registerGauges(config, availableModelIds);

    // Schedule periodic scraping
    long intervalSeconds = config.scrapeIntervalSeconds;
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
      () -> scrapeDevice(deviceId),
      intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

    scheduledTimers.put(deviceId, future);
    long actualParams = availableModelIds != null
      ? config.parameters.stream()
          .filter(p -> p.enabled && availableModelIds.contains(p.sunspecModelId))
          .count()
      : enabledParams;
    LOG.infof("Scheduled metrics scraping for device %d (%s) every %d seconds (%d parameters, %d available after filtering)",
      deviceId, config.device.name, config.scrapeIntervalSeconds, enabledParams, actualParams);
  }

  /**
   * Cancels scheduled scraping for a device and removes all its Micrometer gauges.
   *
   * <p>This ensures that stale gauges (for models no longer available or for
   * disabled parameters) are removed from {@code /q/metrics} when a device
   * is rescheduled or disabled.</p>
   *
   * @param deviceId device ID
   */
  public void cancelDeviceScraping(Long deviceId) {
    ScheduledFuture<?> existing = scheduledTimers.remove(deviceId);
    if (existing != null) {
      existing.cancel(false);
      LOG.debugf("Cancelled scraping timer for device %d", deviceId);
    }

    // Unregister all Micrometer gauges for this device
    Map<String, Meter.Id> deviceMeterIds = registeredGauges.remove(deviceId);
    if (deviceMeterIds != null && !deviceMeterIds.isEmpty()) {
      int removed = 0;
      for (Map.Entry<String, Meter.Id> entry : deviceMeterIds.entrySet()) {
        Meter meter = meterRegistry.remove(entry.getValue());
        if (meter != null) {
          removed++;
        }
      }
      LOG.debugf("Removed %d Micrometer gauge(s) for device %d", removed, deviceId);
    }

    // Clean up gauge value references
    gaugeValues.remove(deviceId);

    // Discard any pending minute-accumulator state for this device.
    // Incomplete minute windows are not persisted — they would represent
    // a partial average and could distort the time-series.
    minuteAccumulators.remove(deviceId);
  }

  /**
   * Returns whether scraping is currently scheduled for a device.
   *
   * @param deviceId device ID
   * @return true if a timer is active
   */
  public boolean isScrapingActive(Long deviceId) {
    return scheduledTimers.containsKey(deviceId);
  }

  /**
   * Returns the number of devices with active scraping timers.
   *
   * @return count of active scraping timers
   */
  public int getActiveScrapingCount() {
    return scheduledTimers.size();
  }

  // ========== Internal Methods ==========

  /**
   * Registers Micrometer gauges for enabled parameters that are available on the device.
   *
   * <p>Uses the {@link MetricMetadataRegistry} to resolve semantic metric
   * names and tags (phase, channel, line, etc.) from the mapping JSON.
   * Falls back to legacy naming when no semantic mapping exists.</p>
   *
   * <p>Only registers gauges for models that were discovered on the device.
   * This prevents phantom metrics in {@code /q/metrics} for models that
   * don't exist (e.g. Float gauges on an Int+SF device, or three-phase
   * meter gauges on a single-phase meter).</p>
   *
   * <p>Reuses existing {@link AtomicReference} instances for gauge values
   * when they already exist (i.e., when rescheduling after a config update).
   * This is critical because Micrometer holds a reference to the original
   * AtomicReference — creating a new one would leave Micrometer reading
   * the old (stale) reference while the scraper updates the new one.</p>
   *
   * @param config            the metrics config entity with parameters
   * @param availableModelIds set of model IDs present on the device, or null to skip filtering
   */
  private void registerGauges(MetricsConfigEntity config, Set<Integer> availableModelIds) {
    Long deviceId = config.device.id;
    String deviceName = config.device.name;

    Map<String, AtomicReference<Double>> deviceGauges =
      gaugeValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());

    for (MetricsParameterEntity param : config.parameters) {
      if (!param.enabled) {
        continue;
      }

      // Skip parameters for models not present on the device
      if (availableModelIds != null && !availableModelIds.contains(param.sunspecModelId)) {
        LOG.debugf("Skipping gauge registration for model %d (%s) on device %d: not present on device",
          param.sunspecModelId, SunSpecConstants.modelName(param.sunspecModelId), deviceId);
        continue;
      }

      String fieldKey = param.sunspecModelId + "_" + param.fieldName;

      // Resolve semantic metric name and tags from the mapping
      Optional<ResolvedMetric> resolved =
        metadataRegistry.resolve(param.sunspecModelId, param.fieldName);

      String metricName = buildMetricName(param, resolved.orElse(null));
      String description = resolved
        .map(ResolvedMetric::description)
        .orElse("SunSpec " + param.fieldName + " from model " + param.sunspecModelId);

      // Reuse existing AtomicReference if present, so Micrometer and
      // the scraper always share the same reference
      AtomicReference<Double> gaugeValue =
        deviceGauges.computeIfAbsent(fieldKey, k -> new AtomicReference<>(Double.NaN));

      // Register gauge only once per unique combination of metric name + tags
      String gaugeKey = metricName + "|" + deviceId + "|" + fieldKey;
      Map<String, Meter.Id> deviceMeterIds =
        registeredGauges.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());
      if (!deviceMeterIds.containsKey(gaugeKey)) {
        Gauge.Builder<?> builder = Gauge.builder(metricName, gaugeValue, AtomicReference::get)
          .tag("device_id", String.valueOf(deviceId))
          .tag("device_name", deviceName)
          .tag("model_id", String.valueOf(param.sunspecModelId))
          .tag("model_name", SunSpecModelRegistry.get(param.sunspecModelId)
            .map(def -> def.name())
            .orElse("unknown"))
          .description(description);

        // Add semantic tags (phase, channel, line, quadrant, location)
        if (resolved.isPresent()) {
          for (Map.Entry<String, String> tag : resolved.get().tags().entrySet()) {
            builder = builder.tag(tag.getKey(), tag.getValue());
          }
        } else {
          // Legacy fallback: keep field tag for unmapped parameters
          builder = builder.tag("field", param.fieldName);
        }

        Gauge gauge = builder.register(meterRegistry);
        deviceMeterIds.put(gaugeKey, gauge.getId());
      }
    }
  }

  /**
   * Builds the Prometheus metric name for a parameter.
   *
   * <p>Priority order:</p>
   * <ol>
   *   <li>Custom name from {@link MetricsParameterEntity#customMetricName}</li>
   *   <li>Semantic name from {@link MetricMetadataRegistry} (e.g. {@code frodo_sunspec_ac_power_watts})</li>
   *   <li>Legacy fallback: {@code frodo_sunspec_{modelId}_{fieldName}}</li>
   * </ol>
   */
  private String buildMetricName(MetricsParameterEntity param, ResolvedMetric resolved) {
    if (param.customMetricName != null && !param.customMetricName.isBlank()) {
      return param.customMetricName;
    }
    if (resolved != null) {
      return resolved.metricName();
    }
    // Legacy fallback: frodo_sunspec_{modelId}_{fieldName}
    return String.format("frodo_sunspec_%d_%s",
      param.sunspecModelId,
      param.fieldName.toLowerCase().replace("/", "_"));
  }

  /**
   * Performs a scrape for a single device.
   *
   * <p>Reloads the config fresh from the database on each invocation to
   * avoid stale parameter references that could cause FK constraint violations
   * when the config has been updated between scrape cycles.</p>
   *
   * <p>Groups enabled parameters by SunSpec model ID for efficient reads,
   * then reads each model and updates gauge values. Optionally persists
   * data points to the database.</p>
   *
   * <p>All model reads are performed sequentially on the scraping thread.
   * Status is updated once after all models have been read.</p>
   */
  private void scrapeDevice(Long deviceId) {
    if (shuttingDown) {
      return;
    }

    try {
      // Reload config fresh from DB to get current parameter IDs
      MetricsConfigEntity config;
      try {
        config = loadConfigForScrape(deviceId);
      } catch (Exception e) {
        LOG.warnf(e, "Failed to load metrics config for device %d, skipping scrape", deviceId);
        return;
      }

      if (config == null || !config.enabled) {
        return;
      }

      DeviceAddress address = DeviceAddress.fromEntity(config.device);
      Instant scrapeTime = Instant.now();

      // Group enabled parameters by model ID
      Map<Integer, List<MetricsParameterEntity>> paramsByModel = config.parameters.stream()
        .filter(p -> p.enabled)
        .collect(Collectors.groupingBy(p -> p.sunspecModelId));

      if (paramsByModel.isEmpty()) {
        return;
      }

      // Run SunSpec discovery to determine which models are actually present
      // on the device, then filter out configured models that don't exist.
      // This prevents noisy warnings and false partial-failure status when
      // users have selected models from the static registry fallback that
      // don't match the device (e.g. Float models on an Int+SF device,
      // or three-phase meter models on a single-phase meter).
      Set<Integer> availableModelIds = discoverAvailableModels(address, deviceId);
      if (availableModelIds != null) {
        Map<Integer, List<MetricsParameterEntity>> filteredParams = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : paramsByModel.entrySet()) {
          int modelId = entry.getKey();
          if (availableModelIds.contains(modelId)) {
            filteredParams.put(modelId, entry.getValue());
          } else {
            LOG.debugf("Skipping model %d (%s) for device %d: not present on device",
              modelId, SunSpecConstants.modelName(modelId), deviceId);
          }
        }
        paramsByModel = filteredParams;

        if (paramsByModel.isEmpty()) {
          LOG.warnf("No configured models are available on device %d after discovery filtering", deviceId);
          try {
            updateScrapeStatus(config.id, ScrapeStatus.FAILED,
              "None of the configured models are available on this device");
          } catch (Exception e) {
            LOG.warnf(e, "Failed to update scrape status for device %d", deviceId);
          }
          return;
        }
      }
      // If discovery failed (availableModelIds == null), proceed with all
      // configured models and let individual readModel calls fail gracefully.

      boolean anySuccess = false;
      boolean anyFailed = false;
      String firstError = null;

      for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : paramsByModel.entrySet()) {
        int modelId = entry.getKey();
        List<MetricsParameterEntity> params = entry.getValue();

        try {
          SunSpecModelData modelData = sunSpecService.readModel(address, modelId);
          processModelReadSuccess(config, deviceId, modelId, params, modelData, scrapeTime);
          anySuccess = true;
        } catch (Exception error) {
          LOG.warnf("Failed to scrape model %d from device %d: %s",
            modelId, deviceId, error.getMessage());
          anyFailed = true;
          if (firstError == null) {
            firstError = error.getMessage();
          }
        }
      }

      // Update scrape status once after all models are done
      // Mark SUCCESS if at least one model was read successfully.
      // Only mark FAILED if ALL models failed.
      try {
        if (anySuccess) {
          String warning = anyFailed
            ? "Partial success; some models failed: " + firstError
            : null;
          updateScrapeStatus(config.id, ScrapeStatus.SUCCESS, warning);
        } else {
          updateScrapeStatus(config.id, ScrapeStatus.FAILED, firstError);
        }
      } catch (Exception e) {
        LOG.warnf(e, "Failed to update scrape status for device %d", deviceId);
      }
    } catch (Exception e) {
      // Top-level catch: prevents any uncaught exception from killing
      // the ScheduledExecutorService task permanently.
      // Without this, a single failure (e.g., LazyInitializationException)
      // would silently stop all future scrapes for this device.
      LOG.errorf(e, "Unexpected error during metrics scrape for device %d", deviceId);
    }
  }

  /**
   * Processes a successful model read: updates gauges and optionally persists data.
   *
   * <p>When {@code scrapeIntervalSeconds < 60}, numeric values are accumulated
   * in a per-minute buffer instead of being written to the database immediately.
   * On the first scrape that falls into a new calendar minute, the previous
   * minute's accumulated values are averaged and flushed as a single DB row.
   * This limits write amplification while preserving Prometheus gauge fidelity.</p>
   *
   * <p>When {@code scrapeIntervalSeconds >= 60}, data is persisted immediately
   * on every scrape (existing behaviour — at most one write per minute anyway).</p>
   *
   * <p>String-typed values cannot be averaged; the last observed value within
   * the minute is persisted instead.</p>
   *
   * <p>Incomplete minute windows (e.g. at shutdown or config change) are
   * discarded — no partial averages are written.</p>
   */
  private void processModelReadSuccess(MetricsConfigEntity config, Long deviceId, int modelId,
                                        List<MetricsParameterEntity> params,
                                        SunSpecModelData modelData, Instant scrapeTime) {
    List<MetricsDataEntity> dataPoints = new ArrayList<>();
    boolean useAccumulator = config.storeToDatabase && config.scrapeIntervalSeconds < 60;
    Instant currentMinute = useAccumulator ? scrapeTime.truncatedTo(ChronoUnit.MINUTES) : null;
    Map<String, FieldAccumulator> deviceAccumulators = useAccumulator
      ? minuteAccumulators.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
      : null;

    for (MetricsParameterEntity param : params) {
      Object value = modelData.values().get(param.fieldName);

      // Update Prometheus gauge (always, regardless of DB interval)
      if (value instanceof Number numValue) {
        String fieldKey = modelId + "_" + param.fieldName;
        Map<String, AtomicReference<Double>> deviceGauges = gaugeValues.get(deviceId);
        if (deviceGauges != null) {
          AtomicReference<Double> gauge = deviceGauges.get(fieldKey);
          if (gauge != null) {
            gauge.set(numValue.doubleValue());
          }
        }
      }

      if (config.storeToDatabase && value != null) {
        if (useAccumulator) {
          // Sub-minute interval: accumulate values within the current calendar minute.
          // When the minute changes, flush the previous minute's average to the DB.
          String accKey = modelId + "_" + param.fieldName;
          FieldAccumulator acc = deviceAccumulators.get(accKey);

          if (acc != null && !acc.minuteBucket.equals(currentMinute)) {
            // Minute boundary crossed — flush previous bucket
            MetricsDataEntity flushed = buildFromAccumulator(acc, config, param, modelId);
            if (flushed != null) {
              dataPoints.add(flushed);
            }
            acc = null;
          }

          if (acc == null) {
            acc = new FieldAccumulator(currentMinute);
            deviceAccumulators.put(accKey, acc);
          }
          acc.add(value);
        } else {
          // Interval >= 60 s: persist every scrape directly (no buffering needed)
          MetricsDataEntity dataPoint = new MetricsDataEntity();
          dataPoint.device = config.device;
          dataPoint.parameter = param;
          dataPoint.recordedAt = scrapeTime;
          dataPoint.sunspecModelId = modelId;
          dataPoint.fieldName = param.fieldName;

          if (value instanceof Number numValue) {
            dataPoint.valueNumeric = numValue.doubleValue();
          } else {
            dataPoint.valueString = String.valueOf(value);
          }

          dataPoints.add(dataPoint);
        }
      }
    }

    // Batch persist data points
    if (!dataPoints.isEmpty()) {
      try {
        persistDataPoints(dataPoints);
      } catch (Exception e) {
        LOG.warnf(e, "Failed to persist metrics data for device %d model %d", deviceId, modelId);
      }
    }
  }

  /**
   * Builds a {@link MetricsDataEntity} from a completed {@link FieldAccumulator}.
   *
   * <p>Numeric values are averaged over all samples collected during the minute.
   * String values use the last observed value. Returns {@code null} when the
   * accumulator holds no data.</p>
   *
   * @param acc     the accumulator for a completed minute bucket
   * @param config  the metrics config (provides device reference)
   * @param param   the parameter entity
   * @param modelId SunSpec model ID (denormalized on the entity)
   * @return a ready-to-persist entity, or {@code null} if the accumulator is empty
   */
  private MetricsDataEntity buildFromAccumulator(FieldAccumulator acc, MetricsConfigEntity config,
                                                  MetricsParameterEntity param, int modelId) {
    if (!acc.hasData()) {
      return null;
    }
    MetricsDataEntity dp = new MetricsDataEntity();
    dp.device = config.device;
    dp.parameter = param;
    dp.recordedAt = acc.minuteBucket;
    dp.sunspecModelId = modelId;
    dp.fieldName = param.fieldName;

    OptionalDouble avg = acc.average();
    if (avg.isPresent()) {
      dp.valueNumeric = avg.getAsDouble();
    } else if (acc.lastString != null) {
      dp.valueString = acc.lastString;
    } else {
      return null;
    }
    return dp;
  }

  /**
   * Discovers which SunSpec models are actually present on the device.
   *
   * <p>Uses the cached SunSpec discovery result (via {@link SunSpecService#getOrDiscover})
   * to determine which model IDs are available. Returns null if discovery fails,
   * allowing the scraper to fall back to attempting all configured models.</p>
   *
   * @param address  device address
   * @param deviceId device ID (for logging)
   * @return set of available model IDs, or null if discovery failed
   */
  private Set<Integer> discoverAvailableModels(DeviceAddress address, Long deviceId) {
    try {
      SunSpecDiscoveryResult discovery = sunSpecService.getOrDiscover(address);
      return new java.util.HashSet<>(discovery.modelIds());
    } catch (Exception e) {
      LOG.debugf("SunSpec discovery failed for device %d during scrape, " +
        "will attempt all configured models: %s", deviceId, e.getMessage());
      return null;
    }
  }

  /**
   * Loads a fresh config with parameters from the database for scraping.
   *
   * <p>This ensures the scraper always uses current parameter IDs,
   * preventing FK constraint violations when parameters have been
   * updated between scrape cycles.</p>
   *
   * @param deviceId the device ID
   * @return the config entity with parameters loaded, or null if not found
   */
  @Transactional
  MetricsConfigEntity loadConfigForScrape(Long deviceId) {
    return configRepository.findByDeviceIdWithParameters(deviceId).orElse(null);
  }

  /**
   * Persists metrics data points to the database.
   */
  @Transactional
  void persistDataPoints(List<MetricsDataEntity> dataPoints) {
    dataRepository.persistAll(dataPoints);
  }

  /**
   * Updates the scrape status using a direct JPQL UPDATE query.
   *
   * <p>This avoids loading/attaching the entity and reduces lock duration.
   * The query updates only the status columns for the specific config ID,
   * so concurrent updates to different configs cannot deadlock each other.</p>
   *
   * @param configId the metrics config entity ID
   * @param status the new scrape status
   * @param errorMessage optional error message (null on success)
   */
  @Transactional
  void updateScrapeStatus(Long configId, ScrapeStatus status, String errorMessage) {
    configRepository.update(
      "lastScrapeTime = ?1, lastScrapeStatus = ?2, lastErrorMessage = ?3 where id = ?4",
      Instant.now(), status, errorMessage, configId);
  }

  // ========== Inner Types ==========

  /**
   * Accumulates scraped values for a single field within one calendar minute.
   *
   * <p>Used when the scrape interval is shorter than one minute: instead of
   * writing every sample to the database, values are collected here and
   * averaged at the end of each minute before being persisted.</p>
   *
   * <p>Package-private to allow direct unit-testing without CDI.</p>
   */
  static class FieldAccumulator {

    /** Start of the calendar minute this accumulator covers. */
    final Instant minuteBucket;

    /** All numeric samples collected within this minute. */
    private final List<Double> numerics = new ArrayList<>();

    /** Last non-null string value observed (strings cannot be averaged). */
    String lastString;

    FieldAccumulator(Instant minuteBucket) {
      this.minuteBucket = minuteBucket;
    }

    /** Adds a scraped value to the accumulator. */
    void add(Object value) {
      if (value instanceof Number n) {
        numerics.add(n.doubleValue());
      } else if (value != null) {
        lastString = String.valueOf(value);
      }
    }

    /** Returns true when at least one value has been recorded. */
    boolean hasData() {
      return !numerics.isEmpty() || lastString != null;
    }

    /** Returns the arithmetic mean of all numeric samples, or empty if none. */
    OptionalDouble average() {
      return numerics.stream().mapToDouble(Double::doubleValue).average();
    }

    /** Sample count (for logging / testing). */
    int sampleCount() {
      return numerics.size();
    }
  }
}
