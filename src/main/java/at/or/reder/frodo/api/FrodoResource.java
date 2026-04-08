package at.or.reder.frodo.api;

import at.or.reder.frodo.FrodoVersion;
import at.or.reder.frodo.modbus.connection.ConnectionStats;
import at.or.reder.frodo.modbus.connection.ModbusConnectionPool;
import at.or.reder.frodo.modbus.service.MetricsScrapingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;

@Path("/")
@Tag(name = "Frodo API", description = "Frodo server endpoints")
public class FrodoResource {

  @Inject
  FrodoVersion frodoVersion;

  @Inject
  ModbusConnectionPool connectionPool;

  @Inject
  MetricsScrapingService metricsScrapingService;

  @GET
  @Path("/info")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get application info", description = "Returns basic application information")
  public ApplicationInfo info() {
    return new ApplicationInfo("frodo", frodoVersion.getVersion(), "Frodo Quarkus Server");
  }

  /**
   * Returns Modbus connection pool and metrics scraping status.
   */
  @GET
  @Path("/status/pool")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "Get connection pool status",
    description = "Returns Modbus connection pool statistics and metrics scraping status"
  )
  @APIResponse(
    responseCode = "200",
    description = "Pool status",
    content = @Content(schema = @Schema(implementation = PoolStatusResponse.class))
  )
  public PoolStatusResponse poolStatus() {
    ConnectionStats stats = connectionPool.getAggregatedStats();
    return new PoolStatusResponse(
      stats.state().name(),
      connectionPool.getConnectionCount(),
      stats.queueSize(),
      stats.totalRequests(),
      stats.failedRequests(),
      stats.lastSuccessTime(),
      connectionPool.isHealthy(),
      metricsScrapingService.getActiveScrapingCount()
    );
  }

  public record ApplicationInfo(String name, String version, String description) {
  }

  /**
   * Connection pool and scraping status response.
   *
   * @param connectionState       aggregated connection state (DISCONNECTED, CONNECTING, CONNECTED, FAILED)
   * @param activeConnections     number of active host:port connections
   * @param pendingRequests       number of requests waiting in queue
   * @param totalRequests         total requests executed since startup
   * @param failedRequests        total failed requests since startup
   * @param lastSuccessTime       timestamp of last successful request (null if none)
   * @param healthy               whether the pool is considered healthy
   * @param activeScrapingTimers  number of devices with active scraping timers
   */
  public record PoolStatusResponse(
    String connectionState,
    int activeConnections,
    int pendingRequests,
    long totalRequests,
    long failedRequests,
    Instant lastSuccessTime,
    boolean healthy,
    int activeScrapingTimers
  ) {
  }
}
