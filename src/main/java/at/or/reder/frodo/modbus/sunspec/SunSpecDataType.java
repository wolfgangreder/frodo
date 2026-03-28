package at.or.reder.frodo.modbus.sunspec;

/**
 * SunSpec data types used in register definitions.
 *
 * <p>Each type specifies its size in 16-bit Modbus registers and
 * whether it is a signed value.</p>
 */
public enum SunSpecDataType {

  /** Unsigned 16-bit integer (1 register). */
  UINT16(1, false),

  /** Signed 16-bit integer (1 register). */
  INT16(1, true),

  /** Unsigned 32-bit integer (2 registers, big-endian). */
  UINT32(2, false),

  /** Signed 32-bit integer (2 registers, big-endian). */
  INT32(2, true),

  /** Accumulated 32-bit unsigned counter (2 registers). */
  ACC32(2, false),

  /** Accumulated 64-bit unsigned counter (4 registers). */
  ACC64(4, false),

  /** IEEE 754 single-precision float (2 registers, big-endian). */
  FLOAT32(2, false),

  /** 16-bit enumerated value (1 register). */
  ENUM16(1, false),

  /** 32-bit enumerated value (2 registers). */
  ENUM32(2, false),

  /** 16-bit bitmask (1 register). */
  BITFIELD16(1, false),

  /** 32-bit bitmask (2 registers). */
  BITFIELD32(2, false),

  /** SunSpec scale factor: signed 16-bit exponent (base 10). */
  SUNSSF(1, true),

  /**
   * UTF-8/ASCII string. Size in registers is variable and defined
   * per field (2 bytes per register). Use {@link #withSize(int)} to
   * create a sized string type.
   */
  STRING(0, false),

  /** Padding register, should be ignored (1 register). */
  PAD(1, false),

  /** Count field (unsigned 16-bit), indicates repeating group count. */
  COUNT(1, false);

  private final int registerCount;
  private final boolean signed;

  SunSpecDataType(int registerCount, boolean signed) {
    this.registerCount = registerCount;
    this.signed = signed;
  }

  /**
   * Returns the number of 16-bit registers this type occupies.
   * For STRING, this returns 0; use the field definition's size instead.
   *
   * @return register count (0 for variable-size STRING)
   */
  public int getRegisterCount() {
    return registerCount;
  }

  /**
   * Whether the type represents a signed value.
   *
   * @return true if signed
   */
  public boolean isSigned() {
    return signed;
  }

  /**
   * Parses a SunSpec type string from the register map into an enum value.
   *
   * @param typeName the type name from the register map (e.g. "float32", "uint16")
   * @return the matching data type
   * @throws IllegalArgumentException if the type name is not recognized
   */
  public static SunSpecDataType fromString(String typeName) {
    if (typeName == null) {
      throw new IllegalArgumentException("Type name must not be null");
    }
    return switch (typeName.toLowerCase().trim()) {
      case "uint16" -> UINT16;
      case "int16" -> INT16;
      case "uint32" -> UINT32;
      case "int32" -> INT32;
      case "acc32" -> ACC32;
      case "acc64" -> ACC64;
      case "float32" -> FLOAT32;
      case "enum16" -> ENUM16;
      case "enum32" -> ENUM32;
      case "bitfield16" -> BITFIELD16;
      case "bitfield32" -> BITFIELD32;
      case "sunssf" -> SUNSSF;
      case "string" -> STRING;
      case "pad" -> PAD;
      case "count" -> COUNT;
      default -> throw new IllegalArgumentException("Unknown SunSpec data type: " + typeName);
    };
  }
}
