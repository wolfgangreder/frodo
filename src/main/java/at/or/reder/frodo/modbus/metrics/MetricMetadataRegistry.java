package at.or.reder.frodo.modbus.metrics;

import at.or.reder.frodo.modbus.metrics.MetricMetadata.FieldMapping;
import at.or.reder.frodo.modbus.metrics.MetricMetadata.ResolvedMetric;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registry that loads the semantic metric mapping from
 * {@code metrics-semantic-mapping.json} and provides fast lookups
 * from (modelId, fieldName) to resolved Prometheus metric names and tags.
 *
 * <p>The mapping is loaded once at application startup. All lookup
 * operations are read-only and thread-safe.</p>
 */
@ApplicationScoped
public class MetricMetadataRegistry {

  private static final Logger LOG = Logger.getLogger(MetricMetadataRegistry.class);
  private static final String MAPPING_RESOURCE = "metrics-semantic-mapping.json";

  /**
   * All metric definitions, in declaration order.
   */
  private List<MetricMetadata> allMetrics = List.of();

  /**
   * Fast lookup: "modelId_fieldName" -> ResolvedMetric.
   */
  private Map<String, ResolvedMetric> lookupIndex = Map.of();

  /**
   * Loads the mapping file at application startup.
   */
  void onStart(@Observes StartupEvent event) {
    try {
      loadMapping();
      LOG.infof("Loaded %d semantic metric definitions (%d field mappings)",
        allMetrics.size(), lookupIndex.size());
    } catch (Exception e) {
      LOG.errorf(e, "Failed to load metrics-semantic-mapping.json — " +
        "semantic metric names will not be available");
    }
  }

  /**
   * Resolves a (modelId, fieldName) pair to a semantic metric name and tags.
   *
   * @param modelId   SunSpec model ID (e.g. 113, 160)
   * @param fieldName field name (e.g. "W", "module/1/DCA")
   * @return the resolved metric, or empty if no mapping exists
   */
  public Optional<ResolvedMetric> resolve(int modelId, String fieldName) {
    String key = modelId + "_" + fieldName;
    return Optional.ofNullable(lookupIndex.get(key));
  }

  /**
   * Returns all loaded metric definitions, in declaration order.
   *
   * @return unmodifiable list of metric metadata
   */
  public List<MetricMetadata> getAllMetrics() {
    return allMetrics;
  }

  /**
   * Returns whether the registry has been successfully loaded.
   *
   * @return true if at least one metric definition is available
   */
  public boolean isLoaded() {
    return !allMetrics.isEmpty();
  }

  // ========== Internal ==========

  /**
   * Loads and parses the JSON mapping file from the classpath.
   */
  void loadMapping() {
    InputStream is = Thread.currentThread().getContextClassLoader()
      .getResourceAsStream(MAPPING_RESOURCE);
    if (is == null) {
      throw new IllegalStateException(MAPPING_RESOURCE + " not found on classpath");
    }

    try (JsonReader reader = Json.createReader(is)) {
      JsonObject root = reader.readObject();
      JsonArray metricsArray = root.getJsonArray("metrics");

      List<MetricMetadata> metrics = new ArrayList<>(metricsArray.size());
      Map<String, ResolvedMetric> index = new HashMap<>();

      for (JsonValue entry : metricsArray) {
        JsonObject obj = entry.asJsonObject();
        MetricMetadata metadata = parseMetric(obj);
        metrics.add(metadata);

        // Build lookup index for each field mapping
        for (FieldMapping fm : metadata.fields()) {
          ResolvedMetric resolved = new ResolvedMetric(
            metadata.metricName(),
            metadata.description(),
            fm.tags()
          );
          for (int modelId : fm.modelIds()) {
            String key = modelId + "_" + fm.field();
            index.put(key, resolved);
          }
        }
      }

      this.allMetrics = Collections.unmodifiableList(metrics);
      this.lookupIndex = Collections.unmodifiableMap(index);

      // Validate: all fields under the same metricName must have the same tag key set
      validateTagKeyConsistency(metrics);
    }
  }

  /**
   * Validates that all field mappings for a given metric name produce the
   * same set of tag keys. Prometheus requires this — registering meters
   * with the same name but different tag key sets causes a runtime crash.
   *
   * @throws IllegalStateException if any metric has inconsistent tag key sets
   */
  private void validateTagKeyConsistency(List<MetricMetadata> metrics) {
    List<String> errors = new ArrayList<>();
    for (MetricMetadata metric : metrics) {
      Set<String> expectedKeys = null;
      for (FieldMapping fm : metric.fields()) {
        Set<String> keys = new TreeSet<>(fm.tags().keySet());
        if (expectedKeys == null) {
          expectedKeys = keys;
        } else if (!expectedKeys.equals(keys)) {
          errors.add(String.format(
            "Metric '%s' has inconsistent tag keys: field '%s' has keys %s, " +
              "but expected %s (from first field mapping)",
            metric.metricName(), fm.field(), keys, expectedKeys));
        }
      }
    }
    if (!errors.isEmpty()) {
      String message = "Tag key consistency violations in " + MAPPING_RESOURCE + ":\n  " +
        String.join("\n  ", errors);
      LOG.error(message);
      throw new IllegalStateException(message);
    }
  }

  /**
   * Parses a single metric definition from JSON.
   */
  private MetricMetadata parseMetric(JsonObject obj) {
    String metricName = obj.getString("metricName");
    String semanticName = obj.getString("semanticName");
    String description = obj.getString("description");
    String unit = obj.isNull("unit") ? null : obj.getString("unit");
    String baseUnit = obj.isNull("baseUnit") ? null : obj.getString("baseUnit");
    String type = obj.getString("type");
    String category = obj.getString("category");

    JsonArray fieldsArray = obj.getJsonArray("fields");
    List<FieldMapping> fields = new ArrayList<>(fieldsArray.size());

    for (JsonValue fv : fieldsArray) {
      JsonObject fo = fv.asJsonObject();
      fields.add(parseFieldMapping(fo));
    }

    return new MetricMetadata(metricName, semanticName, description,
      unit, baseUnit, type, category, Collections.unmodifiableList(fields));
  }

  /**
   * Parses a field mapping entry from JSON.
   */
  private FieldMapping parseFieldMapping(JsonObject fo) {
    JsonArray modelIdsArray = fo.getJsonArray("modelIds");
    List<Integer> modelIds = new ArrayList<>(modelIdsArray.size());
    for (int i = 0; i < modelIdsArray.size(); i++) {
      modelIds.add(modelIdsArray.getInt(i));
    }

    String field = fo.getString("field");

    Map<String, String> tags;
    if (fo.containsKey("tags") && !fo.isNull("tags")) {
      JsonObject tagsObj = fo.getJsonObject("tags");
      tags = new LinkedHashMap<>();
      for (Map.Entry<String, JsonValue> te : tagsObj.entrySet()) {
        tags.put(te.getKey(), ((JsonString) te.getValue()).getString());
      }
      tags = Collections.unmodifiableMap(tags);
    } else {
      tags = Map.of();
    }

    return new FieldMapping(
      Collections.unmodifiableList(modelIds),
      field,
      tags
    );
  }
}
