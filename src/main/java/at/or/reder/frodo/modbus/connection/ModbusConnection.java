package at.or.reder.frodo.modbus.connection;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.net.NetClient;
import io.vertx.mutiny.core.net.NetSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of a single Modbus TCP connection.
 * Handles connection state transitions, auto-reconnect with exponential backoff,
 * request execution with proper MBAP framing and transaction ID correlation.
 */
@ApplicationScoped
public class ModbusConnection {

  private static final Logger LOG = Logger.getLogger(ModbusConnection.class);
  private static final int MBAP_HEADER_LENGTH = 7; // Transaction(2) + Protocol(2) + Length(2) + UnitID(1)

  @Inject
  Vertx vertx;

  private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
  private final AtomicReference<NetSocket> socket = new AtomicReference<>();
  private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>();
  private final AtomicInteger reconnectDelaySeconds = new AtomicInteger(1);
  private final AtomicLong totalRequests = new AtomicLong(0);
  private final AtomicLong failedRequests = new AtomicLong(0);

  private NetClient netClient;
  private String host;
  private int port;
  private Duration connectionTimeout;
  private int reconnectInitialDelaySeconds;
  private int reconnectMaxDelaySeconds;

  // MBAP frame accumulator for handling partial TCP frames
  private Buffer frameBuffer = Buffer.buffer();
  private volatile PendingRequest pendingRequest = null;

  /**
   * Internal holder for pending request with emitter.
   */
  private static class PendingRequest {
    final int transactionId;
    final UniEmitter<? super byte[]> emitter;
    final long timerId;

    PendingRequest(int transactionId, UniEmitter<? super byte[]> emitter, long timerId) {
      this.transactionId = transactionId;
      this.emitter = emitter;
      this.timerId = timerId;
    }
  }

  /**
   * Initializes the connection with configuration parameters.
   *
   * @param host                           Modbus device hostname or IP address
   * @param port                           Modbus device port (typically 502)
   * @param connectionTimeout              timeout for establishing connection
   * @param reconnectInitialDelaySeconds   initial delay before first reconnect attempt
   * @param reconnectMaxDelaySeconds       maximum delay between reconnect attempts
   */
  public void initialize(
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
    this.netClient = vertx.createNetClient();
    this.reconnectDelaySeconds.set(reconnectInitialDelaySeconds);
  }

  /**
   * Establishes a connection to the Modbus device.
   *
   * @return Uni that completes when connected or fails on error
   */
  public Uni<Void> connect() {
    if (state.get() == ConnectionState.CONNECTED || state.get() == ConnectionState.CONNECTING) {
      return Uni.createFrom().voidItem();
    }

    setState(ConnectionState.CONNECTING);
    LOG.infof("Connecting to Modbus device at %s:%d", host, port);

    return netClient.connect(port, host)
      .ifNoItem().after(connectionTimeout).fail()
      .onItem().invoke(sock -> {
        socket.set(sock);
        setState(ConnectionState.CONNECTED);
        reconnectDelaySeconds.set(reconnectInitialDelaySeconds);
        frameBuffer = Buffer.buffer(); // Reset buffer on new connection
        pendingRequest = null;
        LOG.infof("Connected to Modbus device at %s:%d", host, port);

        // Setup socket handler for incoming data
        sock.handler(this::handleIncomingData);

        sock.exceptionHandler(ex -> {
          LOG.errorf(ex, "Socket exception on connection to %s:%d", host, port);
          handleConnectionFailure();
        });

        sock.closeHandler(() -> {
          LOG.warnf("Connection closed by remote %s:%d", host, port);
          handleConnectionFailure();
        });
      })
      .onFailure().invoke(ex -> {
        LOG.errorf(ex, "Failed to connect to %s:%d", host, port);
        setState(ConnectionState.FAILED);
        scheduleReconnect();
      })
      .replaceWithVoid();
  }

  /**
   * Closes the connection and releases all resources.
   *
   * @return Uni that completes when disconnected
   */
  public Uni<Void> disconnect() {
    setState(ConnectionState.DISCONNECTED);
    NetSocket sock = socket.getAndSet(null);
    
    // Fail pending request if any
    PendingRequest pending = pendingRequest;
    if (pending != null) {
      vertx.cancelTimer(pending.timerId);
      pending.emitter.fail(new IllegalStateException("Connection closed"));
      pendingRequest = null;
    }
    
    frameBuffer = Buffer.buffer();

    Uni<Void> closeSocketUni = Uni.createFrom().voidItem();
    if (sock != null) {
      LOG.infof("Disconnecting from %s:%d", host, port);
      closeSocketUni = sock.close()
        .onFailure().invoke(ex -> LOG.warnf(ex, "Error closing socket"))
        .replaceWithVoid();
    }

    // Close NetClient
    if (netClient != null) {
      return closeSocketUni.chain(() -> netClient.close()
        .onFailure().invoke(ex -> LOG.warnf(ex, "Error closing NetClient"))
        .replaceWithVoid());
    }

    return closeSocketUni;
  }

  /**
   * Sends a Modbus request over the established connection and waits for response.
   *
   * @param request Modbus request with frame and transaction ID
   * @return Uni that completes with response bytes or fails on error
   */
  public Uni<byte[]> sendRequest(ModbusRequest request) {
    totalRequests.incrementAndGet();

    if (state.get() != ConnectionState.CONNECTED) {
      failedRequests.incrementAndGet();
      return Uni.createFrom().failure(
        new IllegalStateException("Connection not established: " + state.get())
      );
    }

    NetSocket sock = socket.get();
    if (sock == null) {
      failedRequests.incrementAndGet();
      return Uni.createFrom().failure(
        new IllegalStateException("Socket is null despite connected state")
      );
    }

    if (pendingRequest != null) {
      failedRequests.incrementAndGet();
      return Uni.createFrom().failure(
        new IllegalStateException("Another request is already pending")
      );
    }

    return Uni.createFrom().<byte[]>emitter(emitter -> {
      // Setup timeout
      long timerId = vertx.setTimer(request.timeout().toMillis(), id -> {
        PendingRequest pending = pendingRequest;
        if (pending != null && pending.transactionId == request.transactionId()) {
          pendingRequest = null;
          failedRequests.incrementAndGet();
          emitter.fail(new java.util.concurrent.TimeoutException(
            "Request timeout after " + request.timeout().toMillis() + "ms"
          ));
        }
      });

      // Store pending request for response correlation
      pendingRequest = new PendingRequest(request.transactionId(), emitter, timerId);

      // Send request
      sock.write(Buffer.buffer(request.requestFrame()))
        .subscribe()
        .with(
          v -> LOG.debugf("Modbus request sent (txId=%d, %d bytes)", 
            request.transactionId(), request.requestFrame().length),
          ex -> {
            vertx.cancelTimer(timerId);
            pendingRequest = null;
            failedRequests.incrementAndGet();
            emitter.fail(ex);
          }
        );
    });
  }

  /**
   * Handles incoming data from socket, accumulates MBAP frames and dispatches complete responses.
   *
   * @param buffer data received from socket
   */
  private void handleIncomingData(Buffer buffer) {
    frameBuffer.appendBuffer(buffer);

    // Try to parse complete MBAP frame(s)
    while (frameBuffer.length() >= MBAP_HEADER_LENGTH) {
      // Parse MBAP header
      int transactionId = ((frameBuffer.getByte(0) & 0xFF) << 8) | (frameBuffer.getByte(1) & 0xFF);
      int protocolId = ((frameBuffer.getByte(2) & 0xFF) << 8) | (frameBuffer.getByte(3) & 0xFF);
      int length = ((frameBuffer.getByte(4) & 0xFF) << 8) | (frameBuffer.getByte(5) & 0xFF);
      int expectedFrameLength = 6 + length; // MBAP header (6 bytes) + length field value

      // Check if we have a complete frame
      if (frameBuffer.length() < expectedFrameLength) {
        LOG.debugf("Partial frame received (%d/%d bytes), waiting for more data", 
          frameBuffer.length(), expectedFrameLength);
        break; // Wait for more data
      }

      // Extract complete frame
      byte[] frame = frameBuffer.getBytes(0, expectedFrameLength);
      frameBuffer = frameBuffer.getBuffer(expectedFrameLength, frameBuffer.length());

      LOG.debugf("Complete MBAP frame received (txId=%d, protocol=%d, length=%d, total=%d bytes)",
        transactionId, protocolId, length, expectedFrameLength);

      // Validate protocol ID
      if (protocolId != 0) {
        LOG.warnf("Invalid protocol ID: %d (expected 0)", protocolId);
        failPendingRequest(new IllegalArgumentException("Invalid Modbus protocol ID: " + protocolId));
        continue;
      }

      // Dispatch to pending request
      dispatchResponse(transactionId, frame);
    }
  }

  /**
   * Dispatches a complete response frame to the pending request.
   *
   * @param transactionId transaction ID from response MBAP header
   * @param frame         complete MBAP frame bytes
   */
  private void dispatchResponse(int transactionId, byte[] frame) {
    PendingRequest pending = pendingRequest;
    if (pending == null) {
      LOG.warnf("Received response (txId=%d) but no pending request", transactionId);
      return;
    }

    if (pending.transactionId != transactionId) {
      LOG.errorf("Transaction ID mismatch: expected %d, got %d", pending.transactionId, transactionId);
      failPendingRequest(new IllegalStateException(
        "Transaction ID mismatch: expected " + pending.transactionId + ", got " + transactionId
      ));
      return;
    }

    // Success - complete the request
    vertx.cancelTimer(pending.timerId);
    pendingRequest = null;
    lastSuccessTime.set(Instant.now());
    pending.emitter.complete(frame);
  }

  /**
   * Fails the pending request with an error.
   *
   * @param error the error to fail the request with
   */
  private void failPendingRequest(Throwable error) {
    PendingRequest pending = pendingRequest;
    if (pending != null) {
      vertx.cancelTimer(pending.timerId);
      pendingRequest = null;
      failedRequests.incrementAndGet();
      pending.emitter.fail(error);
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

  private void setState(ConnectionState newState) {
    ConnectionState oldState = state.getAndSet(newState);
    if (oldState != newState) {
      LOG.infof("Connection state: %s → %s", oldState, newState);
    }
  }

  private void handleConnectionFailure() {
    NetSocket sock = socket.getAndSet(null);
    if (sock != null) {
      sock.closeAndForget();
    }
    
    // Fail pending request
    failPendingRequest(new IllegalStateException("Connection failed"));
    
    setState(ConnectionState.FAILED);
    scheduleReconnect();
  }

  private void scheduleReconnect() {
    int currentDelay = reconnectDelaySeconds.get();
    LOG.infof("Scheduling reconnect in %d seconds", currentDelay);

    vertx.setTimer(Duration.ofSeconds(currentDelay).toMillis(), id -> {
      connect().subscribe().with(
        v -> LOG.info("Reconnect successful"),
        ex -> LOG.errorf(ex, "Reconnect failed, will retry")
      );
    });

    // Increase delay for next attempt (exponential backoff)
    int nextDelay = Math.min(currentDelay * 2, reconnectMaxDelaySeconds);
    reconnectDelaySeconds.set(nextDelay);
  }
}
