package at.or.reder.frodo.solarapi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

/**
 * CDI producer for JAX-RS HTTP client.
 *
 * <p>Creates a configured HTTP client for Solar API communication.</p>
 */
@ApplicationScoped
public class SolarApiClientProducer {

  @ConfigProperty(name = "frodo.solar-api.timeout-seconds", defaultValue = "5")
  int timeoutSeconds;

  /**
   * Produces a JAX-RS Client bean for dependency injection.
   *
   * @return configured HTTP client
   */
  @Produces
  @ApplicationScoped
  public Client produceClient() {
    return ClientBuilder.newBuilder()
      .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
      .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
      .build();
  }
}
