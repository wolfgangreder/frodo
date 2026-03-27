package at.or.reder.frodo.modbus.connection;

import java.time.Instant;

/**
 * Statistics for monitoring a Modbus connection.
 *
 * @param state            current connection state
 * @param queueSize        number of requests waiting in queue
 * @param lastSuccessTime  timestamp of last successful request (null if none)
 * @param totalRequests    total number of requests executed
 * @param failedRequests   total number of failed requests
 */
public record ConnectionStats(
  ConnectionState state,
  int queueSize,
  Instant lastSuccessTime,
  long totalRequests,
  long failedRequests
) {}
