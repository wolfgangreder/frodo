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

/**
 * Represents a single SunSpec model block discovered during model chain scanning.
 *
 * @param modelId  SunSpec model ID
 * @param address  Modbus holding register address of this model's ID register
 * @param length   number of data registers (excluding the 2-register ID+L header)
 */
public record SunSpecModelBlock(
  int modelId,
  int address,
  int length
) {

  /**
   * Returns the Modbus address of the first data register (after ID and L).
   *
   * @return data start address
   */
  public int dataAddress() {
    return address + 2;
  }

  /**
   * Returns the total number of registers including the ID and L header.
   *
   * @return total register count (length + 2)
   */
  public int totalRegisters() {
    return length + 2;
  }

  @Override
  public String toString() {
    return String.format("ModelBlock[id=%d, addr=%d, len=%d]", modelId, address, length);
  }
}
