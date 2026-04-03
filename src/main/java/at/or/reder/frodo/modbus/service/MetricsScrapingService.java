package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import at.or.reder.frodo.modbus.entity.MetricsDataEntity;
import at.or.reder.frodo.modbus.entity.MetricsParameterEntity;
import at.or.reder.frodo.modbus.entity.ScrapeStatus;
import at.or.reder.frodo.modbus.repository.MetricsConfigRepository;
import at.or.reder.frodo.modbus.repository.MetricsDataRepository;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelData;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Service that manages periodic metrics scraping for configured devices.
 *
 * <p>For each device with an enabled metrics config, this service schedules
 * a Vert.x periodic timer that reads SunSpec model data at the configured
 * interval. Scraped values are:</p>
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
  Vertx vertx;

  /**
   * Map of device ID to Vert.x periodic timer ID.
   */
  private final Map<Long, Long> scheduledTimers = new ConcurrentHashMap<>();

  /**
   * Gauge value cache: deviceId -> fieldKey -> current value.
   * Uses AtomicReference for thread-safe gauge updates.
   */
  private final Map<Long, Map<String, AtomicReference<Double>>> gaugeValues = new ConcurrentHashMap<>();

  /**
   * Tracks which gauge metric names have been registered (to avoid double-registration).
   */
  private final Map<String, Boolean> registeredGauges = new ConcurrentHashMap<>();

  /**
   * On application startup, initialize scraping for all enabled configs.
   */
  void onStart(@Observes StartupEvent event) {
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
   * On application shutdown, cancel all scheduled timers.
   */
  void onStop(@Observes ShutdownEvent event) {
    LOG.info("Shutting down metrics scraping service");
    scheduledTimers.forEach((deviceId, timerId) -> {
      vertx.cancelTimer(timerId);
      LOG.debugf("Cancelled scraping timer for device %d", deviceId);
    });
    scheduledTimers.clear();
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

    // Register Prometheus gauges for enabled parameters
    registerGauges(config);

    // Schedule periodic scraping via Vert.x timer
    long intervalMs = config.scrapeIntervalSeconds * 1000L;
    long timerId = vertx.setPeriodic(intervalMs, id -> scrapeDevice(config));

    scheduledTimers.put(deviceId, timerId);
    LOG.infof("Scheduled metrics scraping for device %d (%s) every %d seconds (%d parameters)",
      deviceId, config.device.name, config.scrapeIntervalSeconds, enabledParams);
  }

  /**
   * Cancels scheduled scraping for a device.
   *
   * @param deviceId device ID
   */
  public void cancelDeviceScraping(Long deviceId) {
    Long existingTimerId = scheduledTimers.remove(deviceId);
    if (existingTimerId != null) {
      vertx.cancelTimer(existingTimerId);
      LOG.debugf("Cancelled scraping timer for device %d", deviceId);
    }
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
   * Registers Micrometer gauges for all enabled parameters in a config.
   */
  private void registerGauges(MetricsConfigEntity config) {
    Long deviceId = config.device.id;
    String deviceName = config.device.name;

    gaugeValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());

    for (MetricsParameterEntity param : config.parameters) {
      if (!param.enabled) {
        continue;
      }

      String metricName = buildMetricName(param);
      String fieldKey = param.sunspecModelId + "_" + param.fieldName;

      // Create the AtomicReference for the gauge value
      AtomicReference<Double> gaugeValue = new AtomicReference<>(Double.NaN);
      gaugeValues.get(deviceId).put(fieldKey, gaugeValue);

      // Register gauge only once per unique combination of metric name + tags
      String gaugeKey = metricName + "|" + deviceId + "|" + fieldKey;
      if (registeredGauges.putIfAbsent(gaugeKey, Boolean.TRUE) == null) {
        Gauge.builder(metricName, gaugeValue, AtomicReference::get)
          .tag("device_id", String.valueOf(deviceId))
          .tag("device_name", deviceName)
          .tag("model_id", String.valueOf(param.sunspecModelId))
          .tag("field", param.fieldName)
          .description("SunSpec " + param.fieldName + " from model " + param.sunspecModelId)
          .register(meterRegistry);
      }
    }
  }

  /**
   * Builds the Prometheus metric name for a parameter.
   */
  private String buildMetricName(MetricsParameterEntity param) {
    if (param.customMetricName != null && !param.customMetricName.isBlank()) {
      return param.customMetricName;
    }
    // Default naming: frodo_sunspec_{modelId}_{fieldName}
    return String.format("frodo_sunspec_%d_%s",
      param.sunspecModelId,
      param.fieldName.toLowerCase().replace("/", "_"));
  }

  /**
   * Performs a scrape for a single device.
   *
   * <p>Groups enabled parameters by SunSpec model ID for efficient reads,
   * then reads each model and updates gauge values. Optionally persists
   * data points to the database.</p>
   */
  private void scrapeDevice(MetricsConfigEntity config) {
    Long deviceId = config.device.id;
    int unitId = config.device.unitId;
    Instant scrapeTime = Instant.now();

    // Group enabled parameters by model ID
    Map<Integer, List<MetricsParameterEntity>> paramsByModel = config.parameters.stream()
      .filter(p -> p.enabled)
      .collect(Collectors.groupingBy(p -> p.sunspecModelId));

    for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : paramsByModel.entrySet()) {
      int modelId = entry.getKey();
      List<MetricsParameterEntity> params = entry.getValue();

      sunSpecService.readModel(unitId, modelId)
        .subscribe().with(
          modelData -> onModelReadSuccess(config, deviceId, modelId, params, modelData, scrapeTime),
          error -> onModelReadFailure(config, deviceId, modelId, error)
        );
    }
  }

  /**
   * Handles a successful model read: updates gauges and optionally persists data.
   */
  private void onModelReadSuccess(MetricsConfigEntity config, Long deviceId, int modelId,
                                   List<MetricsParameterEntity> params,
                                   SunSpecModelData modelData, Instant scrapeTime) {
    List<MetricsDataEntity> dataPoints = new ArrayList<>();

    for (MetricsParameterEntity param : params) {
      Object value = modelData.values().get(param.fieldName);

      // Update Prometheus gauge
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

      // Prepare DB persistence if enabled
      if (config.storeToDatabase && value != null) {
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

    // Batch persist data points
    if (!dataPoints.isEmpty()) {
      try {
        persistDataPoints(dataPoints);
      } catch (Exception e) {
        LOG.warnf(e, "Failed to persist metrics data for device %d model %d", deviceId, modelId);
      }
    }

    updateScrapeStatus(config, ScrapeStatus.SUCCESS, null);
  }

  /**
   * Handles a failed model read.
   */
  private void onModelReadFailure(MetricsConfigEntity config, Long deviceId, int modelId, Throwable error) {
    LOG.warnf("Failed to scrape model %d from device %d: %s",
      modelId, deviceId, error.getMessage());
    updateScrapeStatus(config, ScrapeStatus.FAILED, error.getMessage());
  }

  /**
   * Persists metrics data points to the database.
   */
  @Transactional
  void persistDataPoints(List<MetricsDataEntity> dataPoints) {
    dataRepository.persistAll(dataPoints);
  }

  /**
   * Updates the scrape status on the config entity.
   */
  @Transactional
  void updateScrapeStatus(MetricsConfigEntity config, ScrapeStatus status, String errorMessage) {
    config.lastScrapeTime = Instant.now();
    config.lastScrapeStatus = status;
    config.lastErrorMessage = errorMessage;
    configRepository.persist(config);
  }
}
