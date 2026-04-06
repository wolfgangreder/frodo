package at.or.reder.frodo.modbus.connection;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the lifecycle of a single Modbus TCP connection to a specific host:port.
 *
 * <p>Handles connection state transitions, auto-reconnect with exponential backoff,
 * request execution with proper MBAP framing and transaction ID correlation.
 * Requests are serialized via a fair {@link ReentrantLock} with an inter-request
 * delay to avoid overwhelming the device.</p>
 *
 * <p>This is a plain POJO managed by {@link ModbusConnectionPool}. Multiple
 * unit IDs on the same host:port share a single connection, since the unit ID
 * is encoded in the MBAP frame header.</p>
 *
 * <p>Uses blocking {@link java.net.Socket} for TCP communication.</p>
 */
public class ModbusConnection {

  private static final Logger LOG = Logger.getLogger(ModbusConnection.class);
  /** Number of bytes in the first part of an MBAP frame read before the length-prefixed payload: Transaction(2) + Protocol(2) + Length(2). */
  private static final int MBAP_FRAME_PREFIX_LENGTH = 6;

  /** Minimum delay between consecutive requests (ms) to avoid overwhelming the device. */
  private static final long INTER_REQUEST_DELAY_MS = 100;

  private final String host;
  private final int port;
  private final Duration connectionTimeout;
  private final int reconnectInitialDelaySeconds;
  private final int reconnectMaxDelaySeconds;

  private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
  private final AtomicReference<Socket> socketRef = new AtomicReference<>();
  private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>();
  private final AtomicLong totalRequests = new AtomicLong(0);
  private final AtomicLong failedRequests = new AtomicLong(0);

  /** Fair lock for serializing request execution. */
  private final ReentrantLock requestLock = new ReentrantLock(true);

  private volatile int reconnectDelaySeconds;

  /**
   * Creates a new connection for the given host:port.
   *
   * @param host                           Modbus device hostname or IP address
   * @param port                           Modbus device port (typically 502)
   * @param connectionTimeout              timeout for establishing connection
   * @param reconnectInitialDelaySeconds   initial delay before first reconnect attempt
   * @param reconnectMaxDelaySeconds       maximum delay between reconnect attempts
   */
  public ModbusConnection(
    String host,
    int port,
    Duration connectionTimeout,
    int reconnectInitialDelaySeconds,
    int reconnectMaxDelaySeconds) {

    this.host = host;
    this.port = port;
    this.connectionTimeout = connectionTimeout;
    this.reconnectInitialDelaySeconds = reconnectInitialDelaySeconds;
    this.reconnectMaxDelaySeconds = reconnectMaxDelaySeconds;
    this.reconnectDelaySeconds = reconnectInitialDelaySeconds;
  }

  /**
   * Returns the host this connection targets.
   *
   * @return hostname or IP address
   */
  public String getHost() {
    return host;
  }

  /**
   * Returns the port this connection targets.
   *
   * @return TCP port number
   */
  public int getPort() {
    return port;
  }

  /**
   * Establishes a blocking connection to the Modbus device.
   *
   * @throws IOException if connection fails
   */
  public void connect() throws IOException {
    if (state.get() == ConnectionState.CONNECTED || state.get() == ConnectionState.CONNECTING) {
      return;
    }

    state.set(ConnectionState.CONNECTING);
    LOG.infof("Connecting to Modbus device at %s:%d", host, port);

    try {
      Socket sock = new Socket();
      sock.connect(new InetSocketAddress(host, port), (int) connectionTimeout.toMillis());
      sock.setSoTimeout((int) connectionTimeout.toMillis());
      sock.setTcpNoDelay(true);
      sock.setKeepAlive(true);

      socketRef.set(sock);
      state.set(ConnectionState.CONNECTED);
      reconnectDelaySeconds = reconnectInitialDelaySeconds;
      LOG.infof("Connected to Modbus device at %s:%d", host, port);
    } catch (IOException e) {
      LOG.errorf(e, "Failed to connect to %s:%d", host, port);
      state.set(ConnectionState.FAILED);
      throw e;
    }
  }

  /**
   * Closes the connection and releases all resources.
   */
  public void disconnect() {
    state.set(ConnectionState.DISCONNECTED);
    closeSocket();
  }

  /**
   * Executes a Modbus request, serializing access via a fair lock.
   *
   * <p>Blocks the calling thread until the lock is acquired, then sends
   * the request and waits for the response. An inter-request delay is
   * added after each successful exchange to avoid overwhelming the device.</p>
   *
   * @param request Modbus request with frame and transaction ID
   * @return response bytes (complete MBAP frame)
   * @throws IOException          if an I/O error occurs
   * @throws TimeoutException     if the response is not received within the request timeout
   * @throws IllegalStateException if the connection is not established
   */
  public byte[] executeRequest(ModbusRequest request) throws IOException, TimeoutException {
    Instant enqueuedAt = Instant.now();

    requestLock.lock();
    try {
      // Check if request already timed out while waiting for the lock
      Duration waitTime = Duration.between(enqueuedAt, Instant.now());
      if (waitTime.compareTo(request.timeout()) >= 0) {
        throw new TimeoutException(
          "Request timed out waiting for queue after " + waitTime.toMillis() + "ms");
      }

      LOG.debugf("Processing request on %s:%d (queue wait: %d ms)", host, port, waitTime.toMillis());

      // Ensure connection is established (auto-reconnect if needed)
      ensureConnected();

      byte[] response = sendRequest(request);

      LOG.debugf("Request completed successfully on %s:%d (%d bytes)", host, port, response.length);

      // Small delay between requests to avoid overwhelming device
      try {
        Thread.sleep(INTER_REQUEST_DELAY_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      return response;
    } finally {
      requestLock.unlock();
    }
  }

  /**
   * Sends a Modbus request over the established connection and blocks until
   * a complete MBAP response frame is received.
   *
   * <p>Must be called while holding {@code requestLock}.</p>
   *
   * @param request Modbus request with frame and transaction ID
   * @return response bytes (complete MBAP frame)
   * @throws IOException      if an I/O error occurs
   * @throws TimeoutException if the response is not received within the request timeout
   * @throws IllegalStateException if the connection is not established
   */
  private byte[] sendRequest(ModbusRequest request) throws IOException, TimeoutException {
    totalRequests.incrementAndGet();

    if (state.get() != ConnectionState.CONNECTED) {
      failedRequests.incrementAndGet();
      throw new IllegalStateException("Connection not established: " + state.get());
    }

    Socket sock = socketRef.get();
    if (sock == null || sock.isClosed()) {
      failedRequests.incrementAndGet();
      throw new IllegalStateException("Socket is null or closed despite connected state");
    }

    try {
      // Set read timeout to request timeout
      sock.setSoTimeout((int) request.timeout().toMillis());

      OutputStream out = sock.getOutputStream();
      InputStream in = sock.getInputStream();

      // Send request
      out.write(request.requestFrame());
      out.flush();
      LOG.debugf("Modbus request sent to %s:%d (txId=%d, %d bytes)",
        host, port, request.transactionId(), request.requestFrame().length);

      // Read response - first the MBAP prefix (6 bytes: TxId + Protocol + Length field)
      byte[] headerBuf = readExactly(in, MBAP_FRAME_PREFIX_LENGTH, request.timeout());

      // Parse length from MBAP header (bytes 4-5)
      int length = ((headerBuf[4] & 0xFF) << 8) | (headerBuf[5] & 0xFF);
      int totalFrameLength = MBAP_FRAME_PREFIX_LENGTH + length;

      // Read remaining bytes (unit ID + PDU)
      byte[] restBuf = readExactly(in, length, request.timeout());

      // Assemble complete frame
      byte[] frame = new byte[totalFrameLength];
      System.arraycopy(headerBuf, 0, frame, 0, MBAP_FRAME_PREFIX_LENGTH);
      System.arraycopy(restBuf, 0, frame, MBAP_FRAME_PREFIX_LENGTH, length);

      // Validate transaction ID
      int responseTransactionId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
      int protocolId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);

      LOG.debugf("Complete MBAP frame received from %s:%d (txId=%d, protocol=%d, length=%d, total=%d bytes)",
        host, port, responseTransactionId, protocolId, length, totalFrameLength);

      if (protocolId != 0) {
        failedRequests.incrementAndGet();
        throw new IOException("Invalid Modbus protocol ID: " + protocolId);
      }

      if (responseTransactionId != request.transactionId()) {
        failedRequests.incrementAndGet();
        throw new IOException(
          "Transaction ID mismatch: expected " + request.transactionId() + ", got " + responseTransactionId);
      }

      lastSuccessTime.set(Instant.now());
      return frame;
    } catch (IOException | TimeoutException e) {
      failedRequests.incrementAndGet();
      handleConnectionFailure();
      throw e;
    }
  }

  /**
   * Reads exactly {@code count} bytes from the input stream.
   *
   * @param in      input stream
   * @param count   number of bytes to read
   * @param timeout maximum time to wait
   * @return byte array of exactly {@code count} bytes
   * @throws IOException      if an I/O error occurs or EOF is reached
   * @throws TimeoutException if reading times out (via socket SO_TIMEOUT)
   */
  private byte[] readExactly(InputStream in, int count, Duration timeout) throws IOException, TimeoutException {
    byte[] buf = new byte[count];
    int offset = 0;
    long deadline = System.currentTimeMillis() + timeout.toMillis();

    while (offset < count) {
      if (System.currentTimeMillis() > deadline) {
        throw new TimeoutException("Read timeout after " + timeout.toMillis() + "ms");
      }
      try {
        int bytesRead = in.read(buf, offset, count - offset);
        if (bytesRead == -1) {
          throw new IOException("Connection closed by remote (EOF) after reading " + offset + " of " + count + " bytes");
        }
        offset += bytesRead;
      } catch (java.net.SocketTimeoutException e) {
        throw new TimeoutException("Socket read timeout after " + timeout.toMillis() + "ms");
      }
    }
    return buf;
  }

  /**
   * Ensures the connection is established, attempting reconnection if needed.
   *
   * @throws IOException if reconnection fails
   */
  private void ensureConnected() throws IOException {
    if (!isHealthy()) {
      LOG.infof("Connection to %s:%d not healthy, attempting reconnect", host, port);
      reconnect();
    }
  }

  /**
   * Checks if the connection is healthy (connected and responsive).
   *
   * @return true if connection is healthy, false otherwise
   */
  public boolean isHealthy() {
    if (state.get() != ConnectionState.CONNECTED) {
      return false;
    }

    Socket sock = socketRef.get();
    if (sock == null || sock.isClosed() || !sock.isConnected()) {
      return false;
    }

    Instant lastSuccess = lastSuccessTime.get();
    if (lastSuccess == null) {
      // No successful requests yet, but connected
      return true;
    }

    // Consider healthy if last success within last 5 minutes
    return Duration.between(lastSuccess, Instant.now()).toMinutes() < 5;
  }

  public ConnectionState getState() {
    return state.get();
  }

  public Instant getLastSuccessTime() {
    return lastSuccessTime.get();
  }

  public long getTotalRequests() {
    return totalRequests.get();
  }

  public long getFailedRequests() {
    return failedRequests.get();
  }

  /**
   * Returns current queue size (number of threads waiting for the lock).
   *
   * @return approximate number of waiting requests
   */
  public int getQueueSize() {
    return requestLock.getQueueLength();
  }

  private void handleConnectionFailure() {
    closeSocket();
    state.set(ConnectionState.FAILED);
  }

  private void closeSocket() {
    Socket sock = socketRef.getAndSet(null);
    if (sock != null) {
      try {
        sock.close();
        LOG.infof("Socket closed for %s:%d", host, port);
      } catch (IOException e) {
        LOG.warnf(e, "Error closing socket to %s:%d", host, port);
      }
    }
  }

  /**
   * Attempts to reconnect with exponential backoff.
   * Blocks the calling thread during the backoff delay.
   *
   * @throws IOException if reconnection fails
   */
  private void reconnect() throws IOException {
    int currentDelay = reconnectDelaySeconds;
    LOG.infof("Reconnecting to %s:%d in %d seconds", host, port, currentDelay);

    try {
      Thread.sleep(Duration.ofSeconds(currentDelay).toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Reconnect interrupted", e);
    }

    // Increase delay for next attempt (exponential backoff)
    int nextDelay = Math.min(currentDelay * 2, reconnectMaxDelaySeconds);
    reconnectDelaySeconds = nextDelay;

    // Reset state so connect() proceeds
    state.set(ConnectionState.DISCONNECTED);
    connect();
  }

  /**
   * Returns current connection statistics.
   *
   * @return ConnectionStats with current state, queue size, and counters
   */
  public ConnectionStats getStats() {
    return new ConnectionStats(
      state.get(),
      getQueueSize(),
      lastSuccessTime.get(),
      totalRequests.get(),
      failedRequests.get()
    );
  }
}
