package at.or.reder.frodo.api.dto;

import java.time.Instant;

/**
 * Standard error response DTO.
 *
 * @param status    HTTP status code
 * @param error     error type/name
 * @param message   error message
 * @param timestamp when the error occurred
 * @param path      request path that caused the error
 */
public record ErrorResponse(
  int status,
  String error,
  String message,
  Instant timestamp,
  String path
) {
}
