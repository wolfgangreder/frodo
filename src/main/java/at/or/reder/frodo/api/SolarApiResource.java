package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.SolarApiStatusResponse;
import at.or.reder.frodo.api.dto.SolarApiStatusResponse.InverterStatus;
import at.or.reder.frodo.api.dto.SolarApiStatusResponse.OhmpilotStatus;
import at.or.reder.frodo.api.dto.SolarApiStatusResponse.SiteStatus;
import at.or.reder.frodo.solarapi.SolarApiClient;
import at.or.reder.frodo.solarapi.SolarApiMetricsService;
import at.or.reder.frodo.solarapi.model.OhmpilotData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData.InverterData;
import at.or.reder.frodo.solarapi.model.PowerFlowRealtimeData.SiteData;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST resource for Solar API status and live metrics.
 *
 * <p>Exposes the current Solar API scraping status and latest power flow
 * values that are collected by {@link SolarApiMetricsService}.</p>
 */
@Path("/solar-api")
@Tag(name = "Solar API", description = "Fronius Solar API status and live metrics")
public class SolarApiResource {

  private static final Logger LOG = Logger.getLogger(SolarApiResource.class);

  @Inject
  SolarApiClient solarApiClient;

  @Inject
  SolarApiMetricsService metricsService;

  /**
   * Returns the current Solar API scraping status and latest power flow values.
   *
   * <p>When Solar API is disabled, returns a response with {@code enabled=false}
   * and empty data sections. When enabled, returns the latest scraped values
   * from the Fronius inverter's Solar API.</p>
   *
   * @return current status and live values
   */
  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get Solar API status and live power flow values")
  public SolarApiStatusResponse getStatus() {
    if (!solarApiClient.isEnabled()) {
      return new SolarApiStatusResponse(
        false, false, 0, 0, 0, null, null,
        Collections.emptyList(), Collections.emptyList());
    }

    PowerFlowRealtimeData data = metricsService.getLastData();

    SiteStatus site = null;
    List<InverterStatus> inverters = Collections.emptyList();
    List<OhmpilotStatus> ohmpilots = Collections.emptyList();

    if (data != null) {
      site = buildSiteStatus(data.getSite());
      inverters = buildInverterStatuses(data.getInverters());
      ohmpilots = buildOhmpilotStatuses(data);
    }

    return new SolarApiStatusResponse(
      true,
      metricsService.isActive(),
      metricsService.getScrapeIntervalSeconds(),
      metricsService.getScrapeCount(),
      metricsService.getErrorCount(),
      metricsService.getLastScrapeTime(),
      site,
      inverters,
      ohmpilots);
  }

  private SiteStatus buildSiteStatus(SiteData site) {
    if (site == null) {
      return null;
    }
    return new SiteStatus(
      site.powerGrid(),
      site.powerLoad(),
      site.powerPV(),
      site.powerBattery(),
      site.relativeAutonomy(),
      site.relativeSelfConsumption(),
      site.meterLocation(),
      site.mode(),
      site.backupMode(),
      site.batteryStandby());
  }

  private List<InverterStatus> buildInverterStatuses(Map<String, InverterData> inverters) {
    if (inverters == null || inverters.isEmpty()) {
      return Collections.emptyList();
    }
    List<InverterStatus> result = new ArrayList<>(inverters.size());
    for (Map.Entry<String, InverterData> entry : inverters.entrySet()) {
      InverterData inv = entry.getValue();
      result.add(new InverterStatus(
        entry.getKey(),
        inv.power(),
        inv.energyTotal(),
        inv.stateOfCharge(),
        inv.batteryMode()));
    }
    return result;
  }

  private List<OhmpilotStatus> buildOhmpilotStatuses(PowerFlowRealtimeData data) {
    if (data.getSmartloads() == null) {
      return Collections.emptyList();
    }
    Map<String, OhmpilotData> ohmpilots = data.getSmartloads().getOhmpilots();
    if (ohmpilots == null || ohmpilots.isEmpty()) {
      return Collections.emptyList();
    }
    List<OhmpilotStatus> result = new ArrayList<>(ohmpilots.size());
    for (Map.Entry<String, OhmpilotData> entry : ohmpilots.entrySet()) {
      OhmpilotData ohm = entry.getValue();
      result.add(new OhmpilotStatus(
        entry.getKey(),
        ohm.powerTotal(),
        ohm.temperature(),
        ohm.state()));
    }
    return result;
  }
}
