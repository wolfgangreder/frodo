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

import at.or.reder.frodo.modbus.entity.AggregationMode;
import at.or.reder.frodo.modbus.metrics.MetricMetadata;
import at.or.reder.frodo.modbus.metrics.MetricMetadataRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for metrics documentation.
 *
 * <p>Serves the semantic metric metadata loaded from
 * {@code metrics-semantic-mapping.json} as JSON, for use by
 * the frontend documentation page and external tools.</p>
 */
@Path("/metrics-docs")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Metrics Documentation", description = "Semantic metrics metadata and documentation")
public class MetricsDocsResource {

  @Inject
  MetricMetadataRegistry metadataRegistry;

  /**
   * Returns all semantic metric definitions.
   *
   * <p>Each entry describes a Prometheus metric: its name, description,
   * unit, category, and which SunSpec model fields map to it.</p>
   */
  @GET
  @Operation(
    summary = "Get metrics documentation",
    description = "Returns all semantic metric definitions with names, descriptions, units, and field mappings"
  )
  public MetricsDocsResponse getMetricsDocs() {
    List<MetricDoc> docs = metadataRegistry.getAllMetrics().stream()
      .map(MetricsDocsResource::toDoc)
      .toList();
    return new MetricsDocsResponse(docs, metadataRegistry.isLoaded());
  }

  /**
   * Returns all supported aggregation modes with descriptions and disk-usage estimates.
   */
  @GET
  @Path("/aggregation-modes")
  @Operation(
    summary = "Get supported aggregation modes",
    description = "Returns all aggregation modes with description, window size, and estimated rows/year per parameter"
  )
  public List<AggregationModeInfo> getAggregationModes() {
    return Arrays.stream(AggregationMode.values())
      .map(mode -> new AggregationModeInfo(
        mode.name(),
        mode.description(),
        mode.windowSeconds(),
        mode.estimatedRowsPerYear()
      ))
      .toList();
  }

  // ========== Response Records ==========

  public record MetricsDocsResponse(
    List<MetricDoc> metrics,
    boolean loaded
  ) {}

  public record MetricDoc(
    String metricName,
    String semanticName,
    String description,
    String unit,
    String baseUnit,
    String type,
    String category,
    List<FieldDoc> fields
  ) {}

  public record FieldDoc(
    List<Integer> modelIds,
    String field,
    Map<String, String> tags
  ) {}

  /**
   * Information record for a single aggregation mode.
   *
   * @param name                 enum constant name (e.g. {@code HOUR_AVERAGE})
   * @param description          human-readable description
   * @param windowSeconds        window duration in seconds (60, 3600, or 86400)
   * @param estimatedRowsPerYear estimated DB rows per year per parameter at 30 s scrape interval
   */
  public record AggregationModeInfo(
    String name,
    String description,
    long windowSeconds,
    long estimatedRowsPerYear
  ) {}

  // ========== Mapping ==========

  private static MetricDoc toDoc(MetricMetadata m) {
    List<FieldDoc> fields = m.fields().stream()
      .map(f -> new FieldDoc(f.modelIds(), f.field(), f.tags()))
      .toList();
    return new MetricDoc(
      m.metricName(), m.semanticName(), m.description(),
      m.unit(), m.baseUnit(), m.type(), m.category(), fields
    );
  }
}
