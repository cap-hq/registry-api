// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.cache;

import com.cvshealth.cap.registry.config.HybridCacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A hybrid cache implementation that uses Caffeine as L1 (local) cache
 * and Redis as L2 (distributed) cache.
 * <p>
 * Cache lookup order:
 * <ol>
 *   <li>Check L1 (Caffeine) - fastest, local memory</li>
 *   <li>On L1 miss, check L2 (Redis) - distributed, shared across instances</li>
 *   <li>On L2 hit, populate L1 and return</li>
 *   <li>On L2 miss, return null (Spring will invoke the @Cacheable method)</li>
 * </ol>
 * <p>
 * Cache write and eviction operations affect both L1 and L2 caches.
 */
@Slf4j
public class HybridCache implements Cache {

    private static final Duration DEFAULT_L2_TTL = Duration.ofMinutes(60);
    private static final int DEFAULT_CACHE_BATCH_SIZE = 100;

    private final String name;
    private final Cache l1Cache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String redisKeyPrefix;
    private final Duration l2Ttl;
    private final int l2CacheBatchSize;

    /**
     * Creates a new HybridCache.
     *
     * @param name              the cache name
     * @param l1Cache           the L1 Caffeine cache
     * @param redisTemplate     the Redis template for L2 operations
     * @param hybridCacheConfig configuration for L1/L2 cache
     */
    public HybridCache(String name,
                       Cache l1Cache,
                       RedisTemplate<String, Object> redisTemplate,
                       HybridCacheConfig hybridCacheConfig) {
        this.name = name;
        this.l1Cache = l1Cache;
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = StringUtils.isEmpty(hybridCacheConfig.getL2CacheKeyPrefix()) ? "" :
                hybridCacheConfig.getL2CacheKeyPrefix();
        this.l2Ttl = hybridCacheConfig.getL2CacheTtlInMinutes() > 0 ?
                Duration.ofMinutes(hybridCacheConfig.getL2CacheTtlInMinutes()) : DEFAULT_L2_TTL;
        this.l2CacheBatchSize = hybridCacheConfig.getL2CacheBatchSize() > 0 ?
                hybridCacheConfig.getL2CacheBatchSize() : DEFAULT_CACHE_BATCH_SIZE;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(@NonNull Object key) {
        // 1. Check L1 (Caffeine) first
        ValueWrapper l1Value = l1Cache.get(key);
        if (l1Value != null) {
            if (log.isDebugEnabled()) {
                log.debug("L1 cache hit for key: {}", key);
            }
            return l1Value;
        }

        // 2. Check L2 (Redis)
        String redisKey = buildRedisKey(key);
        try {
            Object l2Value = redisTemplate.opsForValue().get(redisKey);
            if (l2Value != null) {
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache hit for key: {}, populating L1", key);
                }
                // Populate L1 from L2
                l1Cache.put(key, l2Value);
                return () -> l2Value;
            }
        } catch (Exception e) {
            log.warn("Error reading from L2 cache for key: {}", key, e);
            // Continue without L2 - graceful degradation
        }

        if (log.isDebugEnabled()) {
            log.debug("Cache miss for key: {}", key);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }

        // Cache miss - load value and cache it
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new Cache.ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(@NonNull Object key, Object value) {
        if (value == null) {
            return;
        }

        // Write to L1
        l1Cache.put(key, value);

        // Write to L2 with TTL
        String redisKey = buildRedisKey(key);
        try {
            redisTemplate.opsForValue().set(redisKey, value, l2Ttl);
            if (log.isDebugEnabled()) {
                log.debug("Cached value in L1 and L2 for key: {}", key);
            }
        } catch (Exception e) {
            log.warn("Error writing to L2 cache for key: {}", key, e);
            // L1 write succeeded, continue without L2
        }
    }

    @Override
    public void evict(@NonNull Object key) {
        // Evict from L1
        l1Cache.evict(key);

        // Evict from L2
        String redisKey = buildRedisKey(key);
        try {
            redisTemplate.delete(redisKey);
            if (log.isDebugEnabled()) {
                log.debug("Evicted key from L1 and L2: {}", key);
            }
        } catch (Exception e) {
            log.warn("Error evicting from L2 cache for key: {}", key, e);
        }
    }

    @Override
    public boolean evictIfPresent(@NonNull Object key) {
        boolean l1Evicted = l1Cache.evictIfPresent(key);

        String redisKey = buildRedisKey(key);
        boolean l2Evicted = false;
        try {
            l2Evicted = redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Error evicting from L2 cache for key: {}", key, e);
        }

        return l1Evicted || l2Evicted;
    }

    @Override
    public void clear() {
        // Clear L1
        l1Cache.clear();

        clearRedisL2Cache();
    }

    // Clear L2 using SCAN (non-blocking) instead of KEYS to avoid blocking Redis in production
    private void clearRedisL2Cache() {
        String pattern = redisKeyPrefix + name + ":*";
        try {
            Long deleted = redisTemplate.execute((RedisCallback<Long>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).build();
                long count = 0;
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    List<byte[]> batch = new ArrayList<>(l2CacheBatchSize);
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= l2CacheBatchSize) {
                            Long removed = connection.keyCommands().del(batch.toArray(new byte[0][]));
                            count += (removed != null ? removed : 0);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        Long removed = connection.keyCommands().del(batch.toArray(new byte[0][]));
                        count += (removed != null ? removed : 0);
                    }
                }
                return count;
            });
            if (deleted != null && deleted > 0) {
                log.info("Cleared {} keys from L2 cache for: {}", deleted, name);
            }
        } catch (Exception e) {
            log.warn("Error clearing L2 cache for: {}", name, e);
        }
    }

    @Override
    public boolean invalidate() {
        clear();
        return true;
    }

    /**
     * Builds the Redis key for a given cache key.
     *
     * @param key the cache key
     * @return the full Redis key with prefix
     */
    private String buildRedisKey(Object key) {
        return redisKeyPrefix + name + ":" + key.toString();
    }
}
