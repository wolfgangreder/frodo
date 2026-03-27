package at.or.reder.frodo.modbus.connection;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FIFO queue for serializing Modbus requests.
 * Ensures only one request is processed at a time to avoid connection conflicts.
 * Uses Vert.x worker pool for blocking queue operations.
 */
@ApplicationScoped
public class ModbusRequestQueue {

  private static final Logger LOG = Logger.getLogger(ModbusRequestQueue.class);

  @Inject
  ModbusConnection connection;

  @Inject
  Vertx vertx;

  private BlockingQueue<QueuedRequest> queue;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private int queueCapacity;

  /**
   * Initializes the queue with specified capacity.
   *
   * @param queueCapacity maximum number of requests that can be queued
   */
  public void initialize(int queueCapacity) {
    this.queueCapacity = queueCapacity;
    this.queue = new LinkedBlockingQueue<>(queueCapacity);
  }

  /**
   * Starts the background queue processor using Vert.x worker pool.
   */
  public void start() {
    if (running.compareAndSet(false, true)) {
      LOG.info("Starting Modbus request queue processor");
      startQueueProcessor();
    }
  }

  /**
   * Stops the queue processor and fails all pending requests.
   */
  public void stop() {
    if (running.compareAndSet(true, false)) {
      LOG.info("Stopping Modbus request queue processor");
      
      // Fail remaining queued requests
      QueuedRequest req;
      while ((req = queue.poll()) != null) {
        req.emitter().fail(new IllegalStateException("Queue stopped"));
      }
    }
  }

  /**
   * Enqueues a Modbus request for execution.
   *
   * @param request the request to execute
   * @return Uni that completes with response bytes or fails
   */
  public Uni<byte[]> enqueue(ModbusRequest request) {
    if (!running.get()) {
      return Uni.createFrom().failure(new IllegalStateException("Queue not running"));
    }

    return Uni.createFrom().emitter(emitter -> {
      QueuedRequest queuedRequest = new QueuedRequest(
        request,
        emitter,
        Instant.now(),
        request.timeout()
      );

      boolean added = queue.offer(queuedRequest);
      if (!added) {
        emitter.fail(new IllegalStateException(
          "Request queue full (capacity: " + queueCapacity + ")"
        ));
      } else {
        LOG.debugf("Request enqueued (queue size: %d)", queue.size());
      }
    });
  }

  /**
   * Returns current queue size.
   *
   * @return number of requests waiting in queue
   */
  public int getQueueSize() {
    return queue.size();
  }

  /**
   * Starts the queue processor using Vert.x executeBlocking.
   * Recursively processes queue items on the Vert.x worker pool.
   */
  private void startQueueProcessor() {
    if (!running.get()) {
      return;
    }

    vertx.executeBlocking(Uni.createFrom().item(() -> {
      try {
        // Block until a request is available
        QueuedRequest queuedRequest = queue.take();
        return queuedRequest;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }))
    .subscribe().with(
      queuedRequest -> {
        if (queuedRequest != null) {
          processRequest(queuedRequest);
        }
        // Continue processing queue recursively
        startQueueProcessor();
      },
      error -> {
        LOG.errorf(error, "Queue processor error");
        if (running.get()) {
          startQueueProcessor(); // Restart on error
        }
      }
    );
  }

  /**
   * Processes a single request from the queue.
   *
   * @param queuedRequest the queued request to process
   */
  private void processRequest(QueuedRequest queuedRequest) {
    Instant now = Instant.now();
    Duration waitTime = Duration.between(queuedRequest.enqueuedAt(), now);

    // Check if request already timed out while waiting in queue
    if (waitTime.compareTo(queuedRequest.timeout()) >= 0) {
      LOG.warnf("Request timed out in queue (waited %d ms)", waitTime.toMillis());
      queuedRequest.emitter().fail(new java.util.concurrent.TimeoutException(
        "Request timed out in queue after " + waitTime.toMillis() + "ms"
      ));
      return;
    }

    LOG.debugf("Processing request (queue wait: %d ms)", waitTime.toMillis());

    // Calculate remaining timeout
    Duration remainingTimeout = queuedRequest.timeout().minus(waitTime);

    // Execute request and BLOCK until completion (strictly serialized)
    try {
      byte[] response = connection.sendRequest(queuedRequest.request())
        .await()
        .atMost(remainingTimeout);
      
      LOG.debugf("Request completed successfully (%d bytes)", response.length);
      queuedRequest.emitter().complete(response);
    } catch (Exception failure) {
      LOG.errorf(failure, "Request failed");
      queuedRequest.emitter().fail(failure);
    }

    // Small delay between requests to avoid overwhelming device
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
