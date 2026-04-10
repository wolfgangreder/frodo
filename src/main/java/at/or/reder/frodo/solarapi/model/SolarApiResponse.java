package at.or.reder.frodo.solarapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic wrapper for Fronius Solar API responses.
 *
 * <p>All Solar API endpoints follow the same response structure:</p>
 * <pre>
 * {
 *   "Body": {
 *     "Data": { ... actual data ... }
 *   },
 *   "Head": {
 *     "RequestArguments": {},
 *     "Status": { "Code": 0, "Reason": "", "UserMessage": "" },
 *     "Timestamp": "2026-04-10T14:30:00+01:00"
 *   }
 * }
 * </pre>
 *
 * @param <T> the type of data contained in the Body.Data field
 */
public record SolarApiResponse<T>(
  @JsonProperty("Body") Body<T> body,
  @JsonProperty("Head") Head head
) {

  /**
   * Response body containing the actual data.
   *
   * @param <T> the data type
   */
  public record Body<T>(
    @JsonProperty("Data") T data
  ) {
  }

  /**
   * Response metadata and status information.
   */
  public record Head(
    @JsonProperty("RequestArguments") Object requestArguments,
    @JsonProperty("Status") Status status,
    @JsonProperty("Timestamp") String timestamp
  ) {
  }

  /**
   * API call status information.
   */
  public record Status(
    @JsonProperty("Code") int code,
    @JsonProperty("Reason") String reason,
    @JsonProperty("UserMessage") String userMessage
  ) {

    /**
     * Checks if the API call was successful.
     *
     * @return true if status code is 0 (success)
     */
    public boolean isSuccess() {
      return code == 0;
    }
  }

  /**
   * Extracts the data payload from the response.
   *
   * @return the data object, or null if body is null
   */
  public T getData() {
    return body != null ? body.data() : null;
  }

  /**
   * Checks if the response indicates success.
   *
   * @return true if status code is 0
   */
  public boolean isSuccess() {
    return head != null && head.status() != null && head.status().isSuccess();
  }
}
