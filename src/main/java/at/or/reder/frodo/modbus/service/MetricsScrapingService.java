/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.AggregationMode;
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
import at.or.reder.frodo.solarapi.SolarApiMetricsService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * <p>Each parameter has its own {@link AggregationMode} that controls how scraped
 * values are reduced before being written to the database. All modes use a
 * window-based accumulator ({@link AggregatingAccumulator}); Prometheus gauges are
 * always updated live regardless of mode.</p>
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

  @Inject
  SolarApiMetricsService solarApiMetricsService;

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
   * Per-parameter time-window accumulators.
   *
   * <p>Structure: deviceId → (accKey → accumulator for the current window).
   * The accumulator key is {@code {modelId}_{fieldName}_{mode}}. Each parameter
   * has at most one active accumulator at a time. Accumulators are flushed to the
   * DB when the window boundary is crossed, then replaced with a fresh one.</p>
   */
  private final Map<Long, Map<String, AggregatingAccumulator>> accumulators = new ConcurrentHashMap<>();

  /**
   * Previous-window last value for diff-mode parameters.
   *
   * <p>Structure: deviceId → ({modelId}_{fieldName} → last observed value).
   * Updated on every successful diff-accumulator flush. Cleared when scraping
   * is cancelled. Not persisted across restarts — the first diff value after
   * a restart is skipped.</p>
   */
  private final Map<Long, Map<String, Double>> lastDiffValues = new ConcurrentHashMap<>();

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
    accumulators.clear();
    lastDiffValues.clear();
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
          .filter(p -> p.enabled
            && (SunSpecConstants.isSolarApiModel(p.sunspecModelId)
                || availableModelIds.contains(p.sunspecModelId)))
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

    // Discard pending accumulators — incomplete windows are not persisted to
    // avoid partial averages / incorrect diffs distorting the time-series.
    accumulators.remove(deviceId);

    // Clear diff state so the first sample after re-enabling is skipped safely.
    lastDiffValues.remove(deviceId);
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

      // Skip parameters for models not present on the device.
      // Solar API params (modelId < 0) are never filtered by SunSpec discovery.
      if (availableModelIds != null
          && !SunSpecConstants.isSolarApiModel(param.sunspecModelId)
          && !availableModelIds.contains(param.sunspecModelId)) {
        LOG.debugf("Skipping gauge registration for model %d (%s) on device %d: not present on device",
          param.sunspecModelId, SunSpecConstants.modelName(param.sunspecModelId), deviceId);
        continue;
      }

      // Solar API params: SolarApiMetricsService already owns the frodo_solar_site_* gauges
      // (registered without device tags). Registering them here with device tags would produce
      // a Prometheus tag-set conflict ("same metric name, different tag keys"). Skip gauge
      // registration — DB persistence still runs via processModelReadSuccess.
      if (SunSpecConstants.isSolarApiModel(param.sunspecModelId)) {
        continue;
      }

      String fieldKey = param.sunspecModelId + "_" + param.fieldName;

      // Resolve semantic metric name and tags from the mapping
      Optional<ResolvedMetric> resolved = metadataRegistry.resolve(param.sunspecModelId, param.fieldName);

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
        String modelName = SunSpecModelRegistry.get(param.sunspecModelId)
          .map(def -> def.name())
          .orElse("unknown");

        Gauge.Builder<?> builder = Gauge.builder(metricName, gaugeValue, AtomicReference::get)
          .tag("device_id", String.valueOf(deviceId))
          .tag("device_name", deviceName)
          .tag("model_id", String.valueOf(param.sunspecModelId))
          .tag("model_name", modelName)
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
   * Builds the Prometheus metric name for a SunSpec parameter.
   *
   * <p>Priority order:</p>
   * <ol>
   *   <li>Custom name from {@link MetricsParameterEntity#customMetricName}</li>
   *   <li>Semantic name from {@link MetricMetadataRegistry} (e.g. {@code frodo_sunspec_ac_power_watts})</li>
   *   <li>Legacy fallback: {@code frodo_sunspec_{modelId}_{fieldName}}</li>
   * </ol>
   *
   * <p>Solar API params are never passed here — gauge registration for Solar API
   * is skipped in {@link #registerGauges} since {@code SolarApiMetricsService}
   * already owns those gauges.</p>
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

      // Group enabled parameters by model ID, then split Solar API from SunSpec
      Map<Integer, List<MetricsParameterEntity>> paramsByModel = config.parameters.stream()
        .filter(p -> p.enabled)
        .collect(Collectors.groupingBy(p -> p.sunspecModelId));

      if (paramsByModel.isEmpty()) {
        return;
      }

      // Separate Solar API params (model ID < 0) from SunSpec params so that
      // SunSpec discovery filtering does not exclude Solar API entries.
      Map<Integer, List<MetricsParameterEntity>> solarApiParamsByModel = new LinkedHashMap<>();
      Map<Integer, List<MetricsParameterEntity>> sunspecParamsByModel = new LinkedHashMap<>();
      for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : paramsByModel.entrySet()) {
        if (SunSpecConstants.isSolarApiModel(entry.getKey())) {
          solarApiParamsByModel.put(entry.getKey(), entry.getValue());
        } else {
          sunspecParamsByModel.put(entry.getKey(), entry.getValue());
        }
      }

      // Run SunSpec discovery to determine which models are actually present
      // on the device, then filter out configured models that don't exist.
      // This prevents noisy warnings and false partial-failure status when
      // users have selected models from the static registry fallback that
      // don't match the device (e.g. Float models on an Int+SF device,
      // or three-phase meter models on a single-phase meter).
      if (!sunspecParamsByModel.isEmpty()) {
        Set<Integer> availableModelIds = discoverAvailableModels(address, deviceId);
        if (availableModelIds != null) {
          Map<Integer, List<MetricsParameterEntity>> filteredParams = new LinkedHashMap<>();
          for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : sunspecParamsByModel.entrySet()) {
            int modelId = entry.getKey();
            if (availableModelIds.contains(modelId)) {
              filteredParams.put(modelId, entry.getValue());
            } else {
              LOG.debugf("Skipping model %d (%s) for device %d: not present on device",
                modelId, SunSpecConstants.modelName(modelId), deviceId);
            }
          }
          sunspecParamsByModel = filteredParams;
        }
        // If discovery failed (availableModelIds == null), proceed with all
        // configured SunSpec models and let individual readModel calls fail gracefully.
      }

      boolean anySuccess = false;
      boolean anyFailed = false;
      String firstError = null;

      // --- SunSpec models ---
      for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : sunspecParamsByModel.entrySet()) {
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

      // --- Solar API params ---
      if (!solarApiParamsByModel.isEmpty()) {
        try {
          scrapeSolarApiParams(config, deviceId, solarApiParamsByModel, scrapeTime);
          anySuccess = true;
        } catch (Exception error) {
          LOG.warnf("Failed to scrape Solar API params for device %d: %s",
            deviceId, error.getMessage());
          anyFailed = true;
          if (firstError == null) {
            firstError = error.getMessage();
          }
        }
      }

      if (sunspecParamsByModel.isEmpty() && solarApiParamsByModel.isEmpty()) {
        // All SunSpec models were filtered out and no Solar API params
        LOG.warnf("No configured models are available on device %d after discovery filtering", deviceId);
        try {
          updateScrapeStatus(config.id, ScrapeStatus.FAILED,
            "None of the configured models are available on this device");
        } catch (Exception e) {
          LOG.warnf(e, "Failed to update scrape status for device %d", deviceId);
        }
        return;
      }

      // Update scrape status once after all models are done.
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
   * <p>All parameters use an {@link AggregatingAccumulator} regardless of mode.
   * Values are accumulated within the current time window (minute/hour/day)
   * and flushed to the database when the window boundary is crossed.</p>
   *
   * <p>Prometheus gauges are always updated on every scrape, regardless of
   * the aggregation mode or database storage setting.</p>
   *
   * <p>Incomplete windows (e.g. at shutdown or config change) are discarded —
   * no partial averages or incorrect diffs are written.</p>
   */
  private void processModelReadSuccess(MetricsConfigEntity config, Long deviceId, int modelId,
                                        List<MetricsParameterEntity> params,
                                        SunSpecModelData modelData, Instant scrapeTime) {
    List<MetricsDataEntity> dataPoints = new ArrayList<>();

    Map<String, AggregatingAccumulator> deviceAccumulators = config.storeToDatabase
      ? accumulators.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
      : null;

    Map<String, Double> deviceDiffState = config.storeToDatabase
      ? lastDiffValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
      : null;

    for (MetricsParameterEntity param : params) {
      Object value = modelData.values().get(param.fieldName);

      // Always update Prometheus gauge (live, every scrape)
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

      if (config.storeToDatabase && value != null && deviceAccumulators != null) {
        AggregationMode mode = param.aggregationMode;
        Instant bucketStart = scrapeTime.truncatedTo(mode.chronoUnit());
        String accKey = modelId + "_" + param.fieldName + "_" + mode;
        String diffKey = modelId + "_" + param.fieldName;

        AggregatingAccumulator acc = deviceAccumulators.get(accKey);

        // Check if window boundary crossed — flush the completed bucket
        if (acc != null && acc.shouldFlush(scrapeTime)) {
          Double prevValue = mode.isDiff() ? deviceDiffState.get(diffKey) : null;
          MetricsDataEntity flushed = acc.buildDataEntity(config, param, modelId, prevValue);

          if (flushed != null) {
            dataPoints.add(flushed);
          } else if (mode.isDiff() && acc.hasData()) {
            // First diff: no prevValue, skip writing — just update state below
            LOG.debug("Skipping first diff sample for device " + deviceId +
              " model " + modelId + " field " + param.fieldName + " (no previous value)");
          }

          // Update diff state with last value of the completed window
          if (mode.isDiff()) {
            Double lastVal = acc.getLastNumeric();
            if (lastVal != null) {
              deviceDiffState.put(diffKey, lastVal);
            }
          }

          acc = null;
        }

        // Create new accumulator for the current window if needed
        if (acc == null) {
          acc = new AggregatingAccumulator(mode, bucketStart);
          deviceAccumulators.put(accKey, acc);
        }

        acc.add(value);
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
   * Scrapes Solar API site-level parameters for a device.
   *
   * <p>Reads the latest site values from the cached {@link SolarApiMetricsService}
   * snapshot and wraps them in a synthetic {@link SunSpecModelData} so that the
   * existing {@link #processModelReadSuccess} pipeline (accumulation, gauge update,
   * DB persistence) works unchanged.</p>
   *
   * <p>If the Solar API has not yet delivered data (all values null), this method
   * returns without processing so that no partial records are written to the DB.</p>
   *
   * @param config             device metrics config
   * @param deviceId           device ID (for logging / gauge keys)
   * @param solarApiParamsByModel  Solar API parameter groups (model ID always {@code -1})
   * @param scrapeTime         current scrape timestamp
   */
  private void scrapeSolarApiParams(MetricsConfigEntity config, Long deviceId,
                                    Map<Integer, List<MetricsParameterEntity>> solarApiParamsByModel,
                                    Instant scrapeTime) {
    Map<String, Object> siteValues = solarApiMetricsService.getLastSiteValues();

    // Check if any value is actually available yet
    boolean hasData = siteValues.values().stream().anyMatch(v -> v != null);
    if (!hasData) {
      LOG.debugf("Solar API has no data yet for device %d, skipping Solar API param scrape", deviceId);
      return;
    }

    // Build a synthetic SunSpecModelData so processModelReadSuccess works unchanged
    Instant dataTime = solarApiMetricsService.getLastScrapeTime();
    SunSpecModelData fakeModelData = new SunSpecModelData(
      SunSpecConstants.MODEL_ID_SOLAR_API,
      "Solar API Site",
      0,
      siteValues,
      dataTime != null ? dataTime : scrapeTime
    );

    List<MetricsParameterEntity> params =
      solarApiParamsByModel.getOrDefault(SunSpecConstants.MODEL_ID_SOLAR_API, List.of());
    if (!params.isEmpty()) {
      processModelReadSuccess(config, deviceId, SunSpecConstants.MODEL_ID_SOLAR_API,
        params, fakeModelData, scrapeTime);
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
   * Mode-aware accumulator for a single time window of a single parameter.
   *
   * <p>Collects scraped values within a time window (minute / hour / day)
   * and produces a single aggregated value when the window completes.</p>
   *
   * <p>Behaviour by mode family:</p>
   * <ul>
   *   <li><b>AVERAGE</b> — all samples added to {@code samples}; result is
   *       the arithmetic mean.</li>
   *   <li><b>CURRENT</b> — only the first sample is kept; subsequent values
   *       are discarded.</li>
   *   <li><b>DIFF</b> — all samples added to {@code samples}; result is
   *       {@code lastSample − previousWindowValue}. The previous value is
   *       supplied externally (from {@code lastDiffValues}) at flush time.</li>
   * </ul>
   *
   * <p>String values (rare — non-numeric fields) cannot be averaged or
   * differenced; the last observed string is kept and persisted instead.</p>
   *
   * <p>Package-private to allow direct unit testing without CDI.</p>
   */
  static class AggregatingAccumulator {

    /** Aggregation mode this accumulator implements. */
    final AggregationMode mode;

    /** UTC-aligned start of the window this accumulator covers. */
    final Instant bucketStart;

    /**
     * Numeric samples collected within this window.
     * <ul>
     *   <li>AVERAGE / DIFF: all samples appended in order.</li>
     *   <li>CURRENT: unused — {@code List.of()} is assigned; {@code firstNumeric} used instead.</li>
     * </ul>
     */
    private final List<Double> samples;

    /**
     * First numeric value observed (used by CURRENT modes only).
     * Null until the first sample arrives.
     */
    Double firstNumeric;

    /**
     * Last non-null string value observed (strings cannot be averaged).
     * Persisted as the accumulated value when no numeric result is available.
     */
    String lastString;

    AggregatingAccumulator(AggregationMode mode, Instant bucketStart) {
      this.mode = mode;
      this.bucketStart = bucketStart;
      this.samples = mode.isCurrent() ? List.of() : new ArrayList<>();
    }

    /**
     * Adds a scraped value to the accumulator.
     *
     * @param value scraped value (Number, String, or null — null is ignored)
     */
    void add(Object value) {
      if (value instanceof Number n) {
        double d = n.doubleValue();
        if (mode.isCurrent()) {
          if (firstNumeric == null) {
            firstNumeric = d;
          }
          // Subsequent values discarded for CURRENT mode
        } else {
          // AVERAGE and DIFF: keep all samples
          samples.add(d);
        }
      } else if (value != null) {
        lastString = String.valueOf(value);
      }
    }

    /**
     * Returns {@code true} when this accumulator's window has ended and
     * should be flushed before accepting values for the new window.
     *
     * @param currentScrapeTime timestamp of the incoming scrape
     * @return true if {@code currentScrapeTime} is at or beyond the window end
     */
    boolean shouldFlush(Instant currentScrapeTime) {
      Instant windowEnd = bucketStart.plusSeconds(mode.windowSeconds());
      return !currentScrapeTime.isBefore(windowEnd);
    }

    /**
     * Builds a {@link MetricsDataEntity} from the accumulated data.
     *
     * <p>For DIFF modes, {@code previousValue} must be the last numeric value
     * from the previous window. Returns {@code null} if:</p>
     * <ul>
     *   <li>The accumulator holds no data.</li>
     *   <li>Mode is DIFF and {@code previousValue} is {@code null} (first sample
     *       after startup — no baseline to diff against).</li>
     *   <li>No numeric or string value could be computed.</li>
     * </ul>
     *
     * @param config        metrics config (provides device reference)
     * @param param         parameter entity
     * @param modelId       SunSpec model ID (denormalized)
     * @param previousValue last value of the previous window (DIFF modes only)
     * @return ready-to-persist entity, or {@code null}
     */
    MetricsDataEntity buildDataEntity(MetricsConfigEntity config, MetricsParameterEntity param,
                                      int modelId, Double previousValue) {
      if (!hasData()) {
        return null;
      }

      MetricsDataEntity dp = new MetricsDataEntity();
      dp.device = config.device;
      dp.parameter = param;
      dp.recordedAt = bucketStart;
      dp.sunspecModelId = modelId;
      dp.fieldName = param.fieldName;

      switch (mode) {
        case MINUTE_AVERAGE, HOUR_AVERAGE, DAY_AVERAGE -> {
          OptionalDouble avg = samples.stream().mapToDouble(Double::doubleValue).average();
          if (avg.isPresent()) {
            dp.valueNumeric = avg.getAsDouble();
          }
        }
        case MINUTE_CURRENT, HOUR_CURRENT, DAY_CURRENT -> {
          if (firstNumeric != null) {
            dp.valueNumeric = firstNumeric;
          }
        }
        case MINUTE_DIFF, HOUR_DIFF, DAY_DIFF -> {
          if (!samples.isEmpty()) {
            if (previousValue == null) {
              // No baseline yet — skip writing (caller handles logging)
              return null;
            }
            double current = samples.get(samples.size() - 1);
            dp.valueNumeric = current - previousValue;
          }
        }
      }

      if (dp.valueNumeric == null && lastString != null) {
        dp.valueString = lastString;
      }

      return (dp.valueNumeric != null || dp.valueString != null) ? dp : null;
    }

    /**
     * Returns {@code true} when at least one value has been recorded.
     */
    boolean hasData() {
      return !samples.isEmpty() || firstNumeric != null || lastString != null;
    }

    /**
     * Returns the last numeric value in the window.
     *
     * <p>Used to update the diff baseline after flushing a DIFF-mode window.</p>
     *
     * @return last numeric value, or {@code null} if no numeric values present
     */
    Double getLastNumeric() {
      if (mode.isCurrent()) {
        return firstNumeric;
      }
      if (!samples.isEmpty()) {
        return samples.get(samples.size() - 1);
      }
      return null;
    }

    /**
     * Sample count (for logging / testing).
     *
     * <p>For CURRENT mode, returns 1 if a first value was captured, otherwise 0.</p>
     */
    int sampleCount() {
      if (mode.isCurrent()) {
        return firstNumeric != null ? 1 : 0;
      }
      return samples.size();
    }
  }
}
