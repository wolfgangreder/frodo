package at.or.reder.frodo.solarapi;

import at.or.reder.frodo.modbus.service.ExportSchedulerService;
import at.or.reder.frodo.solarapi.model.OhmpilotData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData.InverterData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData.SiteData;
import at.or.reder.frodo.solarapi.model.SolarApiResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that exposes Fronius Solar API data as Prometheus metrics.
 *
 * <p>When enabled, periodically calls the {@code GetPowerFlowRealtimeData}
 * endpoint and publishes the results as Micrometer gauges. This provides
 * site-level and per-device metrics that complement the SunSpec register
 * data scraped by {@code MetricsScrapingService}.</p>
 *
 * <h3>Registered metrics:</h3>
 *
 * <p><b>Site-level</b> (tags: none)</p>
 * <ul>
 *   <li>{@code frodo_solar_site_grid_power_watts} — Grid power (positive = import, negative = export)</li>
 *   <li>{@code frodo_solar_site_load_power_watts} — Load/consumption power</li>
 *   <li>{@code frodo_solar_site_pv_power_watts} — PV production power</li>
 *   <li>{@code frodo_solar_site_battery_power_watts} — Battery power (positive = charging, negative = discharging)</li>
 *   <li>{@code frodo_solar_site_autonomy_ratio} — Relative autonomy (0–1)</li>
 *   <li>{@code frodo_solar_site_self_consumption_ratio} — Relative self-consumption (0–1)</li>
 * </ul>
 *
 * <p><b>Per-inverter</b> (tags: {@code device_id})</p>
 * <ul>
 *   <li>{@code frodo_solar_inverter_power_watts} — Inverter output power</li>
 *   <li>{@code frodo_solar_inverter_energy_total_watthours} — Total energy produced</li>
 *   <li>{@code frodo_solar_inverter_battery_soc_ratio} — Battery state of charge (0–1)</li>
 * </ul>
 *
 * <p><b>Per-Ohmpilot</b> (tags: {@code component_id})</p>
 * <ul>
 *   <li>{@code frodo_solar_ohmpilot_power_watts} — Ohmpilot power consumption</li>
 *   <li>{@code frodo_solar_ohmpilot_temperature_celsius} — Tank/storage temperature</li>
 *   <li>{@code frodo_solar_ohmpilot_state} — Operating state as numeric code
 *       (0=normal, 1=boost, 2=fault, 3=startup, 4=standby, -1=unknown)</li>
 * </ul>
 *
 * <p>The service is only active when {@code frodo.solar-api.enabled=true}.</p>
 *
 * @see SolarApiClient
 */
@ApplicationScoped
public class SolarApiMetricsService {

  private static final Logger LOG = Logger.getLogger(SolarApiMetricsService.class);

  @Inject
  SolarApiClient solarApiClient;

  @Inject
  MeterRegistry meterRegistry;

  @Inject
  ExportSchedulerService exportSchedulerService;

  @ConfigProperty(name = "frodo.solar-api.enabled", defaultValue = "false")
  boolean solarApiEnabled;

  @ConfigProperty(name = "frodo.solar-api.scrape-interval-seconds", defaultValue = "15")
  int scrapeIntervalSeconds;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "solar-api-scraper");
    t.setDaemon(true);
    return t;
  });

  private ScheduledFuture<?> scheduledFuture;
  private volatile boolean shuttingDown = false;

  // --- Site-level gauge values ---
  private final AtomicReference<Double> siteGridPower = new AtomicReference<>(Double.NaN);
  private final AtomicReference<Double> siteLoadPower = new AtomicReference<>(Double.NaN);
  private final AtomicReference<Double> sitePvPower = new AtomicReference<>(Double.NaN);
  private final AtomicReference<Double> siteBatteryPower = new AtomicReference<>(Double.NaN);
  private final AtomicReference<Double> siteAutonomy = new AtomicReference<>(Double.NaN);
  private final AtomicReference<Double> siteSelfConsumption = new AtomicReference<>(Double.NaN);

  // --- Per-device gauge values: key -> AtomicReference ---
  private final Map<String, AtomicReference<Double>> inverterGauges = new ConcurrentHashMap<>();
  private final Map<String, AtomicReference<Double>> ohmpilotGauges = new ConcurrentHashMap<>();

  // --- Tracks registered Meter.Ids for cleanup ---
  private final Map<String, Meter.Id> registeredMeters = new ConcurrentHashMap<>();

  // --- Last raw data snapshot for API consumers ---
  private final AtomicReference<PowerFlowRealtimeData> lastData = new AtomicReference<>();
  private final AtomicReference<Instant> lastScrapeTime = new AtomicReference<>();
  private volatile int scrapeCount;
  private volatile int errorCount;

  void onStart(@Observes StartupEvent event) {
    if (!solarApiEnabled) {
      LOG.debug("Solar API metrics disabled (frodo.solar-api.enabled=false)");
      return;
    }

    registerSiteGauges();

    scheduledFuture = scheduler.scheduleAtFixedRate(
      this::scrape,
      5, scrapeIntervalSeconds, TimeUnit.SECONDS);

    LOG.infof("Solar API metrics scraping started (interval: %ds)", scrapeIntervalSeconds);
  }

  void onStop(@Observes ShutdownEvent event) {
    shuttingDown = true;
    if (scheduledFuture != null) {
      scheduledFuture.cancel(true);
    }
    scheduler.shutdownNow();

    // Unregister all gauges
    for (Meter.Id meterId : registeredMeters.values()) {
      meterRegistry.remove(meterId);
    }
    registeredMeters.clear();
  }

  /**
   * Returns whether Solar API metrics scraping is active.
   *
   * @return true if the scraping timer is running
   */
  public boolean isActive() {
    return scheduledFuture != null && !scheduledFuture.isCancelled();
  }

  /**
   * Returns the last raw power flow data snapshot, or {@code null} if no
   * successful scrape has occurred yet.
   *
   * @return last scraped data, may be null
   */
  public PowerFlowRealtimeData getLastData() {
    return lastData.get();
  }

  /**
   * Returns the timestamp of the last successful scrape, or {@code null}.
   *
   * @return last scrape instant
   */
  public Instant getLastScrapeTime() {
    return lastScrapeTime.get();
  }

  /**
   * Returns the configured scrape interval in seconds.
   *
   * @return interval seconds
   */
  public int getScrapeIntervalSeconds() {
    return scrapeIntervalSeconds;
  }

  /**
   * Returns the total number of successful scrapes since startup.
   *
   * @return scrape count
   */
  public int getScrapeCount() {
    return scrapeCount;
  }

  /**
   * Returns the total number of failed scrapes since startup.
   *
   * @return error count
   */
  public int getErrorCount() {
    return errorCount;
  }

  /**
   * Returns the latest site-level values as a plain map suitable for the
   * metrics scraping pipeline.
   *
   * <p>Keys match the field names defined in {@link SolarApiFields#SITE_FIELDS}.
   * Values are {@code Double}, or {@code null} when the Solar API has not
   * delivered a reading for that field yet (NaN sentinel converted to null).</p>
   *
   * <p>This method is always safe to call; it never blocks or throws.
   * When Solar API scraping is disabled, all values are null.</p>
   *
   * @return map of field name to current double value (values may be null)
   */
  public Map<String, Object> getLastSiteValues() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("grid_power_watts", nanToNull(siteGridPower.get()));
    result.put("load_power_watts", nanToNull(siteLoadPower.get()));
    result.put("pv_power_watts", nanToNull(sitePvPower.get()));
    result.put("battery_power_watts", nanToNull(siteBatteryPower.get()));
    result.put("autonomy_ratio", nanToNull(siteAutonomy.get()));
    result.put("self_consumption_ratio", nanToNull(siteSelfConsumption.get()));
    return result;
  }

  // ========== Internal ==========

  private void registerSiteGauges() {
    registerGauge("frodo_solar_site_grid_power_watts",
      "Grid power in watts (positive = import, negative = export)",
      siteGridPower);
    registerGauge("frodo_solar_site_load_power_watts",
      "Load/consumption power in watts",
      siteLoadPower);
    registerGauge("frodo_solar_site_pv_power_watts",
      "PV production power in watts",
      sitePvPower);
    registerGauge("frodo_solar_site_battery_power_watts",
      "Battery power in watts (positive = charging, negative = discharging)",
      siteBatteryPower);
    registerGauge("frodo_solar_site_autonomy_ratio",
      "Relative autonomy (0-1)",
      siteAutonomy);
    registerGauge("frodo_solar_site_self_consumption_ratio",
      "Relative self-consumption (0-1)",
      siteSelfConsumption);
  }

  private void registerGauge(String name, String description, AtomicReference<Double> value) {
    Gauge gauge = Gauge.builder(name, value, AtomicReference::get)
      .description(description)
      .register(meterRegistry);
    registeredMeters.put(name, gauge.getId());
  }

  private void registerGauge(String name, String description, AtomicReference<Double> value,
                              String tagKey, String tagValue) {
    Gauge gauge = Gauge.builder(name, value, AtomicReference::get)
      .description(description)
      .tag(tagKey, tagValue)
      .register(meterRegistry);
    registeredMeters.put(name + "|" + tagKey + "=" + tagValue, gauge.getId());
  }

  /**
   * Performs a single scrape of the Solar API and updates all gauge values.
   */
  private void scrape() {
    if (shuttingDown) {
      return;
    }

    try {
      SolarApiResponse<PowerFlowRealtimeData> response =
        solarApiClient.getPowerFlowRealtimeData().await().indefinitely();

      if (response == null || !response.isSuccess() || response.getData() == null) {
        LOG.debug("Solar API returned no data or error during metrics scrape");
        return;
      }

      PowerFlowRealtimeData data = response.getData();
      lastData.set(data);
      lastScrapeTime.set(Instant.now());
      scrapeCount++;
      updateSiteMetrics(data.getSite());
      updateInverterMetrics(data.getInverters());
      updateOhmpilotMetrics(data);
      try {
        exportSchedulerService.onSolarDataUpdated();
      } catch (Exception e) {
        LOG.debugf("Export scheduler update after scrape failed: %s", e.getMessage());
      }
    } catch (Exception e) {
      errorCount++;
      LOG.debugf("Solar API metrics scrape failed: %s", e.getMessage());
    }
  }

  private void updateSiteMetrics(SiteData site) {
    if (site == null) {
      return;
    }
    setIfNotNull(siteGridPower, site.powerGrid());
    setIfNotNull(siteLoadPower, site.powerLoad());
    setIfNotNull(sitePvPower, site.powerPV());
    setIfNotNull(siteBatteryPower, site.powerBattery());
    // API returns percentages (0-100), convert to ratio (0-1)
    if (site.relativeAutonomy() != null) {
      siteAutonomy.set(site.relativeAutonomy() / 100.0);
    }
    if (site.relativeSelfConsumption() != null) {
      siteSelfConsumption.set(site.relativeSelfConsumption() / 100.0);
    }
  }

  private void updateInverterMetrics(Map<String, InverterData> inverters) {
    if (inverters == null) {
      return;
    }

    for (Map.Entry<String, InverterData> entry : inverters.entrySet()) {
      String deviceId = entry.getKey();
      InverterData inv = entry.getValue();

      AtomicReference<Double> power = getOrRegisterInverterGauge(
        deviceId, "power",
        "frodo_solar_inverter_power_watts", "Inverter output power in watts");
      setIfNotNull(power, inv.power());

      AtomicReference<Double> energyTotal = getOrRegisterInverterGauge(
        deviceId, "energy_total",
        "frodo_solar_inverter_energy_total_watthours", "Total energy produced in watt-hours");
      setIfNotNull(energyTotal, inv.energyTotal());

      AtomicReference<Double> soc = getOrRegisterInverterGauge(
        deviceId, "battery_soc",
        "frodo_solar_inverter_battery_soc_ratio", "Battery state of charge (0-1)");
      // API returns percentage (0-100), convert to ratio
      if (inv.stateOfCharge() != null) {
        soc.set(inv.stateOfCharge() / 100.0);
      }
    }
  }

  private void updateOhmpilotMetrics(PowerFlowRealtimeData data) {
    if (data.getSmartloads() == null) {
      return;
    }

    Map<String, OhmpilotData> ohmpilots = data.getSmartloads().getOhmpilots();
    if (ohmpilots == null) {
      return;
    }

    for (Map.Entry<String, OhmpilotData> entry : ohmpilots.entrySet()) {
      String componentId = entry.getKey();
      OhmpilotData ohm = entry.getValue();

      AtomicReference<Double> power = getOrRegisterOhmpilotGauge(
        componentId, "power",
        "frodo_solar_ohmpilot_power_watts", "Ohmpilot power consumption in watts");
      setIfNotNull(power, ohm.powerTotal());

      AtomicReference<Double> temp = getOrRegisterOhmpilotGauge(
        componentId, "temperature",
        "frodo_solar_ohmpilot_temperature_celsius", "Ohmpilot tank/storage temperature in degrees Celsius");
      setIfNotNull(temp, ohm.temperature());

      AtomicReference<Double> state = getOrRegisterOhmpilotGauge(
        componentId, "state",
        "frodo_solar_ohmpilot_state",
        "Ohmpilot operating state (0=normal, 1=boost, 2=fault, 3=startup, 4=standby)");
      state.set(stateToNumeric(ohm.state()));
    }
  }

  private AtomicReference<Double> getOrRegisterInverterGauge(
    String deviceId, String field, String metricName, String description
  ) {
    String key = "inv|" + deviceId + "|" + field;
    return inverterGauges.computeIfAbsent(key, k -> {
      AtomicReference<Double> ref = new AtomicReference<>(Double.NaN);
      registerGauge(metricName, description, ref, "device_id", deviceId);
      return ref;
    });
  }

  private AtomicReference<Double> getOrRegisterOhmpilotGauge(
    String componentId, String field, String metricName, String description
  ) {
    String key = "ohm|" + componentId + "|" + field;
    return ohmpilotGauges.computeIfAbsent(key, k -> {
      AtomicReference<Double> ref = new AtomicReference<>(Double.NaN);
      registerGauge(metricName, description, ref, "component_id", componentId);
      return ref;
    });
  }

  private static void setIfNotNull(AtomicReference<Double> ref, Double value) {
    if (value != null) {
      ref.set(value);
    }
  }

  /** Converts NaN (the "no data" sentinel used by Micrometer gauges) to null. */
  static Double nanToNull(Double value) {
    return (value != null && !Double.isNaN(value)) ? value : null;
  }

  /**
   * Converts Ohmpilot state string to a numeric code for Prometheus.
   */
  private static double stateToNumeric(String state) {
    if (state == null) {
      return -1;
    }
    return switch (state.toLowerCase()) {
      case "normal" -> 0;
      case "boost" -> 1;
      case "fault" -> 2;
      case "startup" -> 3;
      case "standby" -> 4;
      default -> -1;
    };
  }
}
