package at.or.reder.frodo.api;

import at.or.reder.frodo.modbus.metrics.MetricMetadata;
import at.or.reder.frodo.modbus.metrics.MetricMetadata.FieldMapping;
import at.or.reder.frodo.modbus.metrics.MetricMetadataRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
