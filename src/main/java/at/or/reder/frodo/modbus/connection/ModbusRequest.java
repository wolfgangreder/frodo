package at.or.reder.frodo.modbus.connection;

import java.time.Duration;

/**
 * Represents a Modbus request to be executed.
 *
 * @param requestFrame   raw Modbus TCP frame (MBAP + PDU)
 * @param transactionId  Modbus transaction ID (for response correlation)
 * @param timeout        maximum time to wait for response
 */
public record ModbusRequest(
  byte[] requestFrame,
  int transactionId,
  Duration timeout
) {}
