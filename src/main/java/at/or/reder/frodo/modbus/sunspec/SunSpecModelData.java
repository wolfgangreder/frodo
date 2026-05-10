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

package at.or.reder.frodo.modbus.sunspec;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the parsed data for a single SunSpec model instance read from a device.
 *
 * <p>Values are stored as typed Java objects (Integer, Long, Float, String)
 * keyed by field name. Fields that are "not implemented" on the device
 * will have {@code null} values.</p>
 *
 * @param modelId    SunSpec model ID
 * @param modelName  human-readable model name
 * @param address    Modbus base address of this model instance
 * @param values     map of field name to decoded value
 * @param readTime   timestamp when the data was read
 */
public record SunSpecModelData(
  int modelId,
  String modelName,
  int address,
  Map<String, Object> values,
  Instant readTime
) {

  /**
   * Gets a typed value by field name.
   *
   * @param fieldName field name
   * @param type      expected Java type
   * @param <T>       value type
   * @return the value, or null if the field is not present or not implemented
   * @throws ClassCastException if the value is not of the expected type
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String fieldName, Class<T> type) {
    Object value = values.get(fieldName);
    if (value == null) {
      return null;
    }
    return (T) value;
  }

  /**
   * Gets a String value by field name.
   *
   * @param fieldName field name
   * @return string value, or null
   */
  public String getString(String fieldName) {
    return get(fieldName, String.class);
  }

  /**
   * Gets an Integer value by field name.
   *
   * @param fieldName field name
   * @return integer value, or null
   */
  public Integer getInt(String fieldName) {
    return get(fieldName, Integer.class);
  }

  /**
   * Gets a Long value by field name.
   *
   * @param fieldName field name
   * @return long value, or null
   */
  public Long getLong(String fieldName) {
    Object value = values.get(fieldName);
    if (value == null) {
      return null;
    }
    if (value instanceof Long l) {
      return l;
    }
    if (value instanceof Integer i) {
      return i.longValue();
    }
    return (Long) value;
  }

  /**
   * Gets a Float value by field name.
   *
   * @param fieldName field name
   * @return float value, or null
   */
  public Float getFloat(String fieldName) {
    return get(fieldName, Float.class);
  }

  /**
   * Gets a Double value by field name (typically a scaled Int&SF value).
   *
   * @param fieldName field name
   * @return double value, or null
   */
  public Double getDouble(String fieldName) {
    Object value = values.get(fieldName);
    if (value == null) {
      return null;
    }
    if (value instanceof Double d) {
      return d;
    }
    if (value instanceof Float f) {
      return f.doubleValue();
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    return (Double) value;
  }

  /**
   * Checks whether a field has a non-null value.
   *
   * @param fieldName field name
   * @return true if the field exists and is not null
   */
  public boolean hasValue(String fieldName) {
    return values.containsKey(fieldName) && values.get(fieldName) != null;
  }

  /**
   * Creates a builder for constructing SunSpecModelData instances.
   *
   * @param modelId   SunSpec model ID
   * @param modelName model name
   * @param address   Modbus base address
   * @return builder instance
   */
  public static Builder builder(int modelId, String modelName, int address) {
    return new Builder(modelId, modelName, address);
  }

  /**
   * Builder for SunSpecModelData.
   */
  public static class Builder {

    private final int modelId;
    private final String modelName;
    private final int address;
    private final Map<String, Object> values = new LinkedHashMap<>();

    Builder(int modelId, String modelName, int address) {
      this.modelId = modelId;
      this.modelName = modelName;
      this.address = address;
    }

    /**
     * Puts a field value.
     *
     * @param fieldName field name
     * @param value     decoded value (may be null)
     * @return this builder
     */
    public Builder put(String fieldName, Object value) {
      values.put(fieldName, value);
      return this;
    }

    /**
     * Builds the model data instance.
     *
     * @return immutable SunSpecModelData
     */
    public SunSpecModelData build() {
      return new SunSpecModelData(
        modelId,
        modelName,
        address,
        Collections.unmodifiableMap(new LinkedHashMap<>(values)),
        Instant.now()
      );
    }
  }
}
