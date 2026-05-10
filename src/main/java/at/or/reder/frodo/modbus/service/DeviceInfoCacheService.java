/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.modbus.service;

import at.or.reder.frodo.modbus.cache.CachedDeviceInfo;
import at.or.reder.frodo.modbus.model.DeviceIdentification;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache service for device identification information.
 *
 * <p>Provides fast access to device identification data with configurable
 * time-to-live (TTL). Cached entries expire after the configured TTL and
 * are automatically cleaned up every 5 minutes.</p>
 *
 * <p>Thread-safe for concurrent access using {@link ConcurrentHashMap}.</p>
 */
@ApplicationScoped
public class DeviceInfoCacheService {

  private static final Logger LOG = Logger.getLogger(DeviceInfoCacheService.class);

  private final ConcurrentHashMap<Long, CachedDeviceInfo> cache = new ConcurrentHashMap<>();
  private final Duration cacheTtl;

  /**
   * Constructor with CDI injection.
   *
   * @param cacheTtlMinutes cache TTL in minutes (default: 60)
   */
  public DeviceInfoCacheService(
    @ConfigProperty(name = "frodo.modbus.device-info.cache-ttl-minutes", defaultValue = "60") int cacheTtlMinutes
  ) {
    this.cacheTtl = Duration.ofMinutes(cacheTtlMinutes);
    LOG.infof("Device info cache initialized with TTL: %d minutes", cacheTtlMinutes);
  }

  /**
   * Gets cached device identification for the given device ID.
   *
   * <p>Returns empty if the device is not cached or if the cached entry
   * has expired.</p>
   *
   * @param deviceId the device ID
   * @return Optional containing the device identification, or empty if not cached or expired
   */
  public Optional<DeviceIdentification> get(Long deviceId) {
    CachedDeviceInfo cached = cache.get(deviceId);
    if (cached == null) {
      LOG.debugf("Cache miss for device %d: not cached", deviceId);
      return Optional.empty();
    }

    if (cached.isExpired()) {
      LOG.debugf("Cache miss for device %d: expired (age: %s)", deviceId, cached.age());
      cache.remove(deviceId);
      return Optional.empty();
    }

    LOG.debugf("Cache hit for device %d (age: %s, TTL: %s)", deviceId, cached.age(), cached.timeToExpiration());
    return Optional.of(cached.identification());
  }

  /**
   * Puts device identification into the cache with the configured TTL.
   *
   * @param deviceId the device ID
   * @param identification the device identification data
   */
  public void put(Long deviceId, DeviceIdentification identification) {
    CachedDeviceInfo cached = CachedDeviceInfo.of(identification, cacheTtl);
    cache.put(deviceId, cached);
    LOG.debugf("Cached device info for device %d (TTL: %s)", deviceId, cacheTtl);
  }

  /**
   * Invalidates the cached entry for the given device ID.
   *
   * @param deviceId the device ID
   */
  public void invalidate(Long deviceId) {
    CachedDeviceInfo removed = cache.remove(deviceId);
    if (removed != null) {
      LOG.debugf("Invalidated cache for device %d", deviceId);
    }
  }

  /**
   * Clears all cached entries.
   */
  public void clear() {
    int size = cache.size();
    cache.clear();
    LOG.infof("Cleared cache (%d entries removed)", size);
  }

  /**
   * Gets the current cache size.
   *
   * @return number of cached entries
   */
  public int size() {
    return cache.size();
  }

  /**
   * Gets cache statistics.
   *
   * @return cache statistics record
   */
  public CacheStats getStats() {
    int totalEntries = cache.size();
    long expiredCount = cache.values().stream()
      .filter(CachedDeviceInfo::isExpired)
      .count();
    long activeCount = totalEntries - expiredCount;

    return new CacheStats(totalEntries, activeCount, expiredCount);
  }

  /**
   * Scheduled job to clean up expired cache entries.
   *
   * <p>Runs every 5 minutes to remove stale entries and free memory.</p>
   */
  @Scheduled(every = "5m", identity = "cache-cleanup")
  void cleanupExpired() {
    Instant start = Instant.now();
    int initialSize = cache.size();

    cache.entrySet().removeIf(entry -> {
      boolean expired = entry.getValue().isExpired();
      if (expired) {
        LOG.debugf("Removing expired cache entry for device %d", entry.getKey());
      }
      return expired;
    });

    int removed = initialSize - cache.size();
    Duration duration = Duration.between(start, Instant.now());
    LOG.infof("Cache cleanup completed: removed %d expired entries in %d ms (%d active entries remain)",
      removed, duration.toMillis(), cache.size());
  }

  /**
   * Cache statistics record.
   *
   * @param totalEntries total number of entries in cache
   * @param activeEntries number of non-expired entries
   * @param expiredEntries number of expired entries (not yet cleaned up)
   */
  public record CacheStats(
    int totalEntries,
    long activeEntries,
    long expiredEntries
  ) {
  }
}
