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

import org.jboss.logging.Logger;

/**
 * Decodes raw Modbus register data into typed {@link SunSpecModelData}
 * using a {@link SunSpecModelDefinition} as a schema.
 *
 * <p>For Float-format models, values are decoded directly as IEEE 754 floats.
 * For Int&amp;SF-format models, scale factors are resolved and applied to
 * produce {@link Double} values for scaled fields.</p>
 *
 * <p>This class is stateless and thread-safe.</p>
 */
public final class SunSpecModelDataDecoder {

  private static final Logger LOG = Logger.getLogger(SunSpecModelDataDecoder.class);

  private SunSpecModelDataDecoder() {
    // Utility class
  }

  /**
   * Decodes raw register data into a SunSpecModelData instance.
   *
   * <p>The registers array should contain only the data registers for the model,
   * NOT including the ID and L header registers.</p>
   *
   * @param definition the model definition describing the fields
   * @param registers  raw register data (data block only, after ID and L)
   * @param address    Modbus address of this model instance's ID register
   * @return decoded model data
   * @throws IllegalArgumentException if registers array is too short
   */
  public static SunSpecModelData decode(SunSpecModelDefinition definition, int[] registers, int address) {
    SunSpecModelData.Builder builder = SunSpecModelData.builder(
      definition.modelId(), definition.name(), address);

    for (SunSpecFieldDefinition field : definition.fields()) {
      if (field.offset() + field.size() > registers.length) {
        LOG.warnf("Field '%s' at offset %d (size %d) exceeds register data length %d for model %d",
          field.name(), field.offset(), field.size(), registers.length, definition.modelId());
        builder.put(field.name(), null);
        continue;
      }

      Object value = decodeField(field, registers);

      // Apply scale factor if present
      if (field.hasScaleFactor() && value instanceof Number) {
        SunSpecFieldDefinition sfField = definition.field(field.scaleFactor());
        if (sfField != null && sfField.offset() + sfField.size() <= registers.length) {
          Integer sf = SunSpecRegisterDecoder.decodeSunssf(registers, sfField.offset());
          if (sf != null && value != null) {
            // Produce a scaled double value, then apply any additional conversion factor
            double scaledValue = ((Number) value).doubleValue() * Math.pow(10, sf) * field.conversionFactor();
            builder.put(field.name(), scaledValue);
            continue;
          }
        }
      }

      // Apply conversion factor for non-SF numeric fields (e.g. Float PF)
      if (field.conversionFactor() != 1.0 && value instanceof Number) {
        builder.put(field.name(), ((Number) value).doubleValue() * field.conversionFactor());
        continue;
      }

      builder.put(field.name(), value);
    }

    return builder.build();
  }

  /**
   * Decodes a single field from the register data.
   *
   * @param field     field definition
   * @param registers raw register data
   * @return decoded value (type depends on field data type)
   */
  static Object decodeField(SunSpecFieldDefinition field, int[] registers) {
    int offset = field.offset();
    int size = field.size();

    return switch (field.dataType()) {
      case UINT16, COUNT -> SunSpecRegisterDecoder.decodeUint16(registers, offset);
      case INT16 -> SunSpecRegisterDecoder.decodeInt16(registers, offset);
      case UINT32 -> SunSpecRegisterDecoder.decodeUint32(registers, offset);
      case INT32 -> {
        Long raw = SunSpecRegisterDecoder.decodeUint32(registers, offset);
        yield raw == null ? null : raw.intValue();
      }
      case ACC32 -> SunSpecRegisterDecoder.decodeAcc32(registers, offset);
      case ACC64 -> SunSpecRegisterDecoder.decodeAcc64(registers, offset);
      case FLOAT32 -> SunSpecRegisterDecoder.decodeFloat32(registers, offset);
      case ENUM16 -> SunSpecRegisterDecoder.decodeEnum16(registers, offset);
      case ENUM32 -> SunSpecRegisterDecoder.decodeUint32(registers, offset);
      case BITFIELD16 -> SunSpecRegisterDecoder.decodeBitfield16(registers, offset);
      case BITFIELD32 -> SunSpecRegisterDecoder.decodeBitfield32(registers, offset);
      case SUNSSF -> SunSpecRegisterDecoder.decodeSunssf(registers, offset);
      case STRING -> SunSpecRegisterDecoder.decodeString(registers, offset, size);
      case PAD -> null;
    };
  }
}
