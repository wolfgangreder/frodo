package at.or.reder.frodo.modbus.connection;

import io.smallrye.mutiny.subscription.UniEmitter;

import java.time.Duration;
import java.time.Instant;

/**
 * Internal representation of a queued Modbus request with its emitter.
 *
 * @param request     the Modbus request to execute
 * @param emitter     emitter to complete with result or failure
 * @param enqueuedAt  timestamp when request was added to queue
 * @param timeout     maximum time to wait for completion
 */
record QueuedRequest(
  ModbusRequest request,
  UniEmitter<? super byte[]> emitter,
  Instant enqueuedAt,
  Duration timeout
) {}
