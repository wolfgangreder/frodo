package at.or.reder.frodo.modbus.cache;

import at.or.reder.frodo.modbus.model.DeviceIdentification;

import java.time.Duration;
import java.time.Instant;

/**
 * Cached device identification information with expiration.
 *
 * <p>Wraps a {@link DeviceIdentification} along with caching metadata
 * (cache time and expiration time). Used by {@link at.or.reder.frodo.modbus.service.DeviceInfoCacheService}
 * for in-memory caching of device info.</p>
 *
 * @param identification the device identification data
 * @param cachedAt the instant when this info was cached
 * @param expiresAt the instant when this info expires
 */
public record CachedDeviceInfo(
  DeviceIdentification identification,
  Instant cachedAt,
  Instant expiresAt
) {

  /**
   * Creates a new cached device info with the given identification and TTL.
   *
   * @param identification the device identification data
   * @param ttl time-to-live duration
   * @return new cached device info
   * @throws IllegalArgumentException if ttl is negative
   */
  public static CachedDeviceInfo of(DeviceIdentification identification, Duration ttl) {
    if (ttl.isNegative()) {
      throw new IllegalArgumentException("TTL must not be negative");
    }
    Instant now = Instant.now();
    return new CachedDeviceInfo(identification, now, now.plus(ttl));
  }

  /**
   * Checks if this cached info has expired.
   *
   * @return true if expired, false otherwise
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /**
   * Calculates the age of this cached info.
   *
   * @return duration since cached
   */
  public Duration age() {
    return Duration.between(cachedAt, Instant.now());
  }

  /**
   * Calculates the time remaining until expiration.
   *
   * @return duration until expiration (may be negative if expired)
   */
  public Duration timeToExpiration() {
    return Duration.between(Instant.now(), expiresAt);
  }
}
