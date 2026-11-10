// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.cache;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.config.HybridCacheConfig;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * A CacheManager that provides hybrid L1/L2 caching for specific cache names.
 * <p>
 * This manager creates {@link HybridCache} instances for configured cache names
 * (currently {@code TEAM_ROLLUP_INFO}), which use Caffeine as L1 and Redis as L2.
 * <p>
 * For cache names not configured for hybrid caching, this manager returns null,
 * allowing a fallback CacheManager (via CompositeCacheManager) to handle them.
 */
@Slf4j
public class HybridCacheManager implements CacheManager {

    private static final List<String> HYBRID_CACHE_NAMES = List.of(CacheConfig.TEAM_ROLLUP_INFO);

    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;
    private final HybridCacheConfig hybridCacheConfig;

    /**
     * Creates a new HybridCacheManager.
     *
     * @param redisTemplate     the Redis template for L2 cache operations
     * @param hybridCacheConfig configuration for team rollup cache TTL and size
     */
    public HybridCacheManager(RedisTemplate<String, Object> redisTemplate,
                              HybridCacheConfig hybridCacheConfig) {
        this.redisTemplate = redisTemplate;
        this.hybridCacheConfig = hybridCacheConfig;
        log.info("HybridCacheManager initialized with L1 TTL: {} min, L2 TTL: {} min, L1 max size: {}, L2 batch size: {}",
                hybridCacheConfig.getL1CacheTtlInMinutes(),
                hybridCacheConfig.getL2CacheTtlInMinutes(),
                hybridCacheConfig.getL1MaxSize(),
                hybridCacheConfig.getL2CacheBatchSize());
    }

    @Override
    public Cache getCache(String name) {
        if (!HYBRID_CACHE_NAMES.contains(name)) {
            // Not a hybrid cache - let another CacheManager handle it
            return null;
        }

        return caches.computeIfAbsent(name, this::createHybridCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return HYBRID_CACHE_NAMES;
    }

    /**
     * Creates a HybridCache for the given cache name.
     *
     * @param name the cache name
     * @return a new HybridCache instance
     */
    private Cache createHybridCache(String name) {
        log.info("Creating hybrid cache for: {}", name);

        // Create L1 Caffeine cache with specific configuration
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(hybridCacheConfig.getL1CacheTtlInMinutes(), TimeUnit.MINUTES)
                .maximumSize(hybridCacheConfig.getL1MaxSize())
                .recordStats()
                .build();

        Cache l1Cache = new CaffeineCache(name, caffeineCache);

        return new HybridCache(
                name,
                l1Cache,
                redisTemplate,
                hybridCacheConfig
        );
    }
}
