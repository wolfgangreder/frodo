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

package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.AvailableParameter;
import at.or.reder.frodo.api.dto.AvailableParametersResponse;
import at.or.reder.frodo.api.dto.LatestMetricsResponse;
import at.or.reder.frodo.api.dto.MetricsConfigRequest;
import at.or.reder.frodo.api.dto.MetricsConfigResponse;
import at.or.reder.frodo.api.dto.MetricsDataResponse;
import at.or.reder.frodo.api.dto.MetricsStatusResponse;
import at.or.reder.frodo.api.dto.ParameterConfigRequest;
import at.or.reder.frodo.api.exception.DeviceNotFoundException;
import at.or.reder.frodo.modbus.connection.DeviceAddress;
import at.or.reder.frodo.modbus.entity.AggregationMode;
import at.or.reder.frodo.modbus.entity.MetricsConfigEntity;
import at.or.reder.frodo.modbus.entity.MetricsParameterEntity;
import at.or.reder.frodo.modbus.entity.ModbusDeviceEntity;
import at.or.reder.frodo.modbus.metrics.MetricMetadataRegistry;
import at.or.reder.frodo.modbus.repository.MetricsConfigRepository;
import at.or.reder.frodo.modbus.repository.MetricsDataRepository;
import at.or.reder.frodo.modbus.repository.ModbusDeviceRepository;
import at.or.reder.frodo.modbus.service.MetricsScrapingService;
import at.or.reder.frodo.modbus.sunspec.SunSpecConstants;
import at.or.reder.frodo.modbus.sunspec.SunSpecDataType;
import at.or.reder.frodo.modbus.sunspec.SunSpecDiscoveryResult;
import at.or.reder.frodo.modbus.sunspec.SunSpecFieldDefinition;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelBlock;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelDefinition;
import at.or.reder.frodo.modbus.sunspec.SunSpecModelRegistry;
import at.or.reder.frodo.modbus.sunspec.SunSpecService;
import at.or.reder.frodo.solarapi.SolarApiFields;
import at.or.reder.frodo.solarapi.SolarApiMetricsService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API for per-device metrics scraping configuration and historical data.
 *
 * <p>Provides endpoints to configure metrics scraping parameters,
 * discover available SunSpec fields, check scraping status, and
 * query historical metrics data.</p>
 */
@Path("/devices/{deviceId}/metrics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Metrics Configuration", description = "Configure per-device metrics scraping")
public class MetricsConfigResource {

  private static final Logger LOG = Logger.getLogger(MetricsConfigResource.class);

  @Inject
  MetricsConfigRepository configRepository;

  @Inject
  MetricsDataRepository dataRepository;

  @Inject
  ModbusDeviceRepository deviceRepository;

  @Inject
  MetricsScrapingService scrapingService;

  @Inject
  SunSpecService sunSpecService;

  @Inject
  MetricMetadataRegistry metadataRegistry;

  @Inject
  SolarApiMetricsService solarApiMetricsService;

  @ConfigProperty(name = "frodo.solar-api.enabled", defaultValue = "false")
  boolean solarApiEnabled;

  /**
   * Gets the metrics configuration for a device.
   *
   * @param deviceId device ID
   * @return metrics configuration, or a default if none exists
   */
  @GET
  @Path("/config")
  @Blocking
  @Transactional
  @Operation(summary = "Get metrics configuration for a device")
  public MetricsConfigResponse getConfig(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId
  ) {
    LOG.debugf("Getting metrics config: deviceId=%d", deviceId);
    ensureDeviceExists(deviceId);

    return configRepository.findByDeviceIdWithParameters(deviceId)
      .map(MetricsConfigResponse::from)
      .orElse(MetricsConfigResponse.defaultConfig(deviceId));
  }

  /**
   * Creates or updates the metrics configuration for a device.
   *
   * @param deviceId device ID
   * @param request  metrics configuration request
   * @return updated metrics configuration
   */
  @PUT
  @Path("/config")
  @Blocking
  @Transactional
  @Operation(summary = "Update metrics configuration for a device")
  public MetricsConfigResponse updateConfig(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId,
    @Valid MetricsConfigRequest request
  ) {
    LOG.infof("Updating metrics config: deviceId=%d, interval=%ds, enabled=%b",
      deviceId, request.scrapeIntervalSeconds(), request.enabled());

    ModbusDeviceEntity device = deviceRepository.findByIdOptional(deviceId)
      .orElseThrow(() -> DeviceNotFoundException.forId(deviceId));

    MetricsConfigEntity config = configRepository.findByDeviceIdWithParameters(deviceId)
      .orElse(null);

    if (config == null) {
      config = new MetricsConfigEntity();
      config.device = device;
    }

    config.scrapeIntervalSeconds = request.scrapeIntervalSeconds();
    config.enabled = request.enabled();

    if (request.storeToDatabase() != null) {
      config.storeToDatabase = request.storeToDatabase();
    }
    if (request.retentionDays() != null) {
      config.retentionDays = request.retentionDays();
    }

    // Update parameters
    if (request.parameters() != null) {
      updateParameters(config, request.parameters());
    }

    configRepository.save(config);

    // Flush to ensure constraint violations surface before scheduling
    configRepository.flush();

    // Reschedule scraping with new config
    scrapingService.scheduleDeviceScraping(config);

    return MetricsConfigResponse.from(config);
  }

  /**
   * Gets the available SunSpec parameters for metrics collection.
   *
   * <p>Performs SunSpec model chain discovery on the device and returns
   * all numeric fields from known models that can be scraped.</p>
   *
   * @param deviceId device ID
   * @return available parameters grouped by model
   */
  @GET
  @Path("/available-parameters")
  @Blocking
  @Transactional
  @Operation(summary = "Get available SunSpec parameters for metrics collection")
  public AvailableParametersResponse getAvailableParameters(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId
  ) {
    LOG.debugf("Getting available parameters: deviceId=%d", deviceId);

    ModbusDeviceEntity device = deviceRepository.findByIdOptional(deviceId)
      .orElseThrow(() -> DeviceNotFoundException.forId(deviceId));

    DeviceAddress address = DeviceAddress.fromEntity(device);
    try {
      SunSpecDiscoveryResult discovery = sunSpecService.discover(address);
      List<AvailableParameter> params = new ArrayList<>();

      for (SunSpecModelBlock model : discovery.models()) {
        if (SunSpecModelRegistry.isKnown(model.modelId())) {
          SunSpecModelDefinition def = SunSpecModelRegistry.require(model.modelId());
          String modelName = def.name();

          for (SunSpecFieldDefinition field : def.fields()) {
            if (isScrapableField(field)) {
              params.add(new AvailableParameter(
                model.modelId(),
                modelName,
                field.name(),
                field.units(),
                field.description(),
                resolveMetricName(model.modelId(), field.name())
              ));
            }
          }
        }
      }

      if (solarApiEnabled) {
        params.addAll(buildSolarApiParameters());
      }
      return new AvailableParametersResponse(deviceId, params, true);
    } catch (Exception error) {
      LOG.warnf("SunSpec discovery failed for device %d, falling back to static registry: %s",
        deviceId, error.getMessage());
      return buildAvailableParametersFromRegistry(deviceId);
    }
  }

  /**
   * Gets the current scraping status for a device.
   *
   * @param deviceId device ID
   * @return scraping status
   */
  @GET
  @Path("/status")
  @Blocking
  @Transactional
  @Operation(summary = "Get current scraping status")
  public MetricsStatusResponse getStatus(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId
  ) {
    LOG.debugf("Getting metrics status: deviceId=%d", deviceId);
    ensureDeviceExists(deviceId);

    return configRepository.findByDeviceIdWithParameters(deviceId)
      .map(MetricsStatusResponse::from)
      .orElse(MetricsStatusResponse.notConfigured(deviceId));
  }

  /**
   * Gets historical metrics data for a device.
   *
   * @param deviceId  device ID
   * @param fromStr   start of time range (ISO-8601), defaults to 24h ago
   * @param toStr     end of time range (ISO-8601), defaults to now
   * @param modelId   optional SunSpec model ID filter
   * @param fieldName optional field name filter
   * @param limit     max results (default 1000)
   * @return historical data points
   */
  @GET
  @Path("/data")
  @Blocking
  @Transactional
  @Operation(summary = "Get historical metrics data for a device")
  public MetricsDataResponse getHistoricalData(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId,
    @QueryParam("from") String fromStr,
    @QueryParam("to") String toStr,
    @QueryParam("modelId") Integer modelId,
    @QueryParam("field") String fieldName,
    @QueryParam("limit") @DefaultValue("1000") int limit
  ) {
    ensureDeviceExists(deviceId);

    Instant from = fromStr != null ? Instant.parse(fromStr) : Instant.now().minus(24, ChronoUnit.HOURS);
    Instant to = toStr != null ? Instant.parse(toStr) : Instant.now();

    var data = dataRepository.findByDeviceAndTimeRange(deviceId, from, to, limit);

    // Apply optional filters
    if (modelId != null) {
      data = data.stream().filter(d -> d.sunspecModelId == modelId).toList();
    }
    if (fieldName != null) {
      data = data.stream().filter(d -> d.fieldName.equals(fieldName)).toList();
    }

    return MetricsDataResponse.from(deviceId, data, from, to);
  }

  /**
   * Gets the latest metrics values for a device.
   *
   * @param deviceId device ID
   * @param limit    max results (default 100)
   * @return latest values per field
   */
  @GET
  @Path("/data/latest")
  @Blocking
  @Transactional
  @Operation(summary = "Get latest metrics values for a device")
  public LatestMetricsResponse getLatestData(
    @Parameter(description = "Device ID", required = true)
    @PathParam("deviceId") Long deviceId,
    @QueryParam("limit") @DefaultValue("100") int limit
  ) {
    ensureDeviceExists(deviceId);

    var data = dataRepository.findLatestByDevice(deviceId, limit);
    return LatestMetricsResponse.from(deviceId, data);
  }

  // ========== Private Helper Methods ==========

  private void ensureDeviceExists(Long deviceId) {
    deviceRepository.findByIdOptional(deviceId)
      .orElseThrow(() -> DeviceNotFoundException.forId(deviceId));
  }

  /**
   * Builds available parameters from the static SunSpec model registry.
   *
   * <p>Used as a fallback when live SunSpec discovery fails (device offline,
   * Modbus disabled, etc.), so users can still configure metrics parameters
   * even when the device is unreachable.</p>
   */
  private AvailableParametersResponse buildAvailableParametersFromRegistry(Long deviceId) {
    List<AvailableParameter> params = new ArrayList<>();

    for (var entry : SunSpecModelRegistry.all().entrySet()) {
      int modelId = entry.getKey();
      SunSpecModelDefinition def = entry.getValue();

      for (SunSpecFieldDefinition field : def.fields()) {
        if (isScrapableField(field)) {
          params.add(new AvailableParameter(
            modelId,
            def.name(),
            field.name(),
            field.units(),
            field.description(),
            resolveMetricName(modelId, field.name())
          ));
        }
      }
    }

    if (solarApiEnabled) {
      params.addAll(buildSolarApiParameters());
    }

    return new AvailableParametersResponse(deviceId, params, false);
  }

  /**
   * Builds the Solar API site-level parameter entries for the available-parameters response.
   *
   * <p>These appear alongside SunSpec parameters in the metrics configuration UI.
   * They are always included when {@code frodo.solar-api.enabled=true}, regardless of
   * whether SunSpec discovery succeeded.</p>
   */
  private List<AvailableParameter> buildSolarApiParameters() {
    return SolarApiFields.SITE_FIELDS.stream()
      .map(f -> new AvailableParameter(
        SunSpecConstants.MODEL_ID_SOLAR_API,
        SunSpecConstants.modelName(SunSpecConstants.MODEL_ID_SOLAR_API),
        f.fieldName(),
        f.units(),
        f.description(),
        f.metricName()
      ))
      .toList();
  }

  /**
   * Resolves the Prometheus metric name for a SunSpec field.
   *
   * <p>Uses the semantic mapping from {@link MetricMetadataRegistry} if available,
   * otherwise falls back to the legacy naming convention
   * {@code frodo_sunspec_{modelId}_{fieldName}}.</p>
   *
   * @param modelId   SunSpec model ID
   * @param fieldName field name within the model
   * @return the metric name that will be used in Prometheus
   */
  private String resolveMetricName(int modelId, String fieldName) {
    return metadataRegistry.resolve(modelId, fieldName)
      .map(resolved -> resolved.metricName())
      .orElseGet(() -> String.format("frodo_sunspec_%d_%s",
        modelId, fieldName.toLowerCase().replace("/", "_")));
  }

  /**
   * Determines if a SunSpec field is suitable for metrics scraping.
   *
   * <p>Excludes scale factors (SUNSSF), padding (PAD), and string fields.
   * Includes numeric types (UINT16, INT16, UINT32, INT32, ACC32, ACC64,
   * FLOAT32) and also enum/bitfield types since they can be represented
   * as numeric gauge values.</p>
   */
  private boolean isScrapableField(SunSpecFieldDefinition field) {
    return switch (field.dataType()) {
      case UINT16, INT16, UINT32, INT32, ACC32, ACC64, FLOAT32,
           ENUM16, ENUM32, BITFIELD16, BITFIELD32, COUNT -> true;
      case SUNSSF, STRING, PAD -> false;
    };
  }

  /**
   * Synchronizes the parameter list of a config entity with the requested parameters.
   *
   * <p>Updates parameters in-place where possible (matching by modelId + fieldName),
   * preserving their database IDs. This prevents FK constraint violations when a
   * concurrent scrape task holds references to the old parameter entities.</p>
   */
  private void updateParameters(MetricsConfigEntity config, List<ParameterConfigRequest> requested) {
    // Build a lookup of existing parameters by (modelId, fieldName)
    Map<String, MetricsParameterEntity> existingByKey = new HashMap<>();
    for (MetricsParameterEntity existing : config.parameters) {
      String key = existing.sunspecModelId + "|" + existing.fieldName;
      existingByKey.put(key, existing);
    }

    // Track which existing parameters are still needed
    Set<String> requestedKeys = new HashSet<>();

    for (ParameterConfigRequest paramReq : requested) {
      String key = paramReq.sunspecModelId() + "|" + paramReq.fieldName();
      requestedKeys.add(key);

      MetricsParameterEntity existing = existingByKey.get(key);
      if (existing != null) {
        // Update in-place — preserves the database ID
        existing.enabled = paramReq.enabled();
        existing.customMetricName = paramReq.customMetricName();
        existing.aggregationMode = paramReq.aggregationMode() != null
          ? paramReq.aggregationMode()
          : AggregationMode.MINUTE_AVERAGE;
      } else {
        // New parameter — add it
        MetricsParameterEntity param = new MetricsParameterEntity();
        param.config = config;
        param.sunspecModelId = paramReq.sunspecModelId();
        param.fieldName = paramReq.fieldName();
        param.enabled = paramReq.enabled();
        param.customMetricName = paramReq.customMetricName();
        param.aggregationMode = paramReq.aggregationMode() != null
          ? paramReq.aggregationMode()
          : AggregationMode.MINUTE_AVERAGE;
        config.parameters.add(param);
      }
    }

    // Remove parameters that are no longer in the request
    config.parameters.removeIf(p -> !requestedKeys.contains(p.sunspecModelId + "|" + p.fieldName));
  }
}
