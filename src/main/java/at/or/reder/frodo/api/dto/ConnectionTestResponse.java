package at.or.reder.frodo.api.dto;

/**
 * Response DTO for connection test results.
 *
 * @param success      whether the connection test succeeded
 * @param message      descriptive message about the test result
 * @param manufacturer device manufacturer (if identified, from Modbus FC 0x2B)
 * @param productCode  device product code (if identified)
 * @param modelName    device model name (if identified)
 * @param revision     firmware/software revision (if identified)
 * @param responseTimeMs connection response time in milliseconds
 * @param detectionMethod method used to detect device ("Device ID", "SunSpec signature", etc.)
 */
public record ConnectionTestResponse(
  boolean success,
  String message,
  String manufacturer,
  String productCode,
  String modelName,
  String revision,
  Long responseTimeMs,
  String detectionMethod
) {
  /**
   * Create a successful connection test response with device identification.
   */
  public static ConnectionTestResponse success(
    String manufacturer,
    String productCode,
    String modelName,
    String revision,
    long responseTimeMs,
    String detectionMethod
  ) {
    return new ConnectionTestResponse(
      true,
      "Connection successful",
      manufacturer,
      productCode,
      modelName,
      revision,
      responseTimeMs,
      detectionMethod
    );
  }

  /**
   * Create a successful connection test response without device identification.
   */
  public static ConnectionTestResponse successWithoutIdentification(long responseTimeMs, String detectionMethod) {
    return new ConnectionTestResponse(
      true,
      "Connection successful",
      null,
      null,
      null,
      null,
      responseTimeMs,
      detectionMethod
    );
  }

  /**
   * Create a failed connection test response.
   */
  public static ConnectionTestResponse failure(String message, long responseTimeMs) {
    return new ConnectionTestResponse(
      false,
      message,
      null,
      null,
      null,
      null,
      responseTimeMs,
      null
    );
  }
}
