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
