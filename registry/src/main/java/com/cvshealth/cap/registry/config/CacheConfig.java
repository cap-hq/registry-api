// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.cache.HybridCacheManager;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the application.
 * <p>
 * Configures a {@link CompositeCacheManager} that supports:
 * <ul>
 *   <li>Hybrid L1/L2 caching (Caffeine + Redis) for {@code TEAM_ROLLUP_INFO} when Redis is available</li>
 *   <li>Caffeine-only caching for {@code CACHE_SLACK_CHANNEL_INFO} and {@code CACHE_EXTERNAL_APP_INFO}</li>
 *   <li>Fallback to Caffeine-only for all caches when Redis is not configured</li>
 * </ul>
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {
    public static final String CACHE_SLACK_CHANNEL_INFO = "cache_slack_channel_info";
    public static final String CACHE_EXTERNAL_APP_INFO = "cache_external_app_info";
    public static final String CACHE_CLOUD_TENANTS_FOR_TEAM = "cache_cloud_tenants_for_team";
    public static final String TEAM_ROLLUP_INFO = "team_rollup_info";
    public static final String PERSONAL_TEAM_ROLLUP_INFO = "personal_team_rollup_info";

    /** Cache names that use Caffeine-only (no L2 Redis) */
    private static final List<String> CAFFEINE_ONLY_CACHE_NAMES = List.of(CACHE_SLACK_CHANNEL_INFO, CACHE_EXTERNAL_APP_INFO, CACHE_CLOUD_TENANTS_FOR_TEAM);

    /** All cache names including hybrid caches (used as fallback when Redis is not available) */
    private static final List<String> ALL_CACHE_NAMES = List.of(CACHE_SLACK_CHANNEL_INFO, CACHE_EXTERNAL_APP_INFO, CACHE_CLOUD_TENANTS_FOR_TEAM, TEAM_ROLLUP_INFO);

    /**
     * Creates the HybridCacheManager for L1/L2 caching when Redis is enabled.
     * <p>
     * This bean is only created when {@code app.cache.redis.enabled=true}.
     * It requires the {@code cacheRedisTemplate} bean from {@link RedisConfig}.
     *
     * @param redisTemplate    the Redis template for L2 cache operations (from RedisConfig)
     * @param hybridCacheConfig configuration for hybrid (team rollup) cache TTL and size
     * @return the HybridCacheManager
     */
    @Bean
    @ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true")
    public HybridCacheManager hybridCacheManager(
            RedisTemplate<String, Object> redisTemplate,
            HybridCacheConfig hybridCacheConfig) {
        log.info("Creating HybridCacheManager with Redis L2 support");
        return new HybridCacheManager(redisTemplate, hybridCacheConfig);
    }

    /**
     * Creates the primary CacheManager using a CompositeCacheManager.
     * <p>
     * When Redis is available, the composite includes:
     * <ol>
     *   <li>HybridCacheManager - handles TEAM_ROLLUP_INFO with L1/L2 caching</li>
     *   <li>CaffeineCacheManager - handles other caches with L1-only caching</li>
     * </ol>
     * <p>
     * When Redis is not available, only the CaffeineCacheManager is used for all caches.
     *
     * @param cacheTtlConfig        configuration for the cache TTL
     * @param hybridCacheManager    the hybrid cache manager (optional, only present when Redis is configured)
     * @return the primary CacheManager
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            CacheTtlConfig cacheTtlConfig,
            @Autowired(required = false) HybridCacheManager hybridCacheManager) {
        
        CompositeCacheManager compositeManager = new CompositeCacheManager();
        List<CacheManager> cacheManagers = new ArrayList<>();

        if (hybridCacheManager != null) {
            // Redis is available - use hybrid caching for TEAM_ROLLUP_INFO
            log.info("Configuring hybrid L1/L2 caching for {} (Redis enabled)", TEAM_ROLLUP_INFO);
            cacheManagers.add(hybridCacheManager);
            // Caffeine-only for other caches
            cacheManagers.addAll(caffeineCacheManagers(CAFFEINE_ONLY_CACHE_NAMES, cacheTtlConfig));
        } else {
            // Redis not available - use Caffeine-only for all caches
            log.info("Configuring Caffeine-only caching for all caches (Redis not configured)");
            cacheManagers.addAll(caffeineCacheManagers(ALL_CACHE_NAMES, cacheTtlConfig));
        }

        compositeManager.setCacheManagers(cacheManagers);
        return compositeManager;
    }

    private List<CacheManager> caffeineCacheManagers(List<String> cacheNames, CacheTtlConfig cacheTtlConfig) {
        if (CollectionUtils.isEmpty(cacheNames)) {
            return Collections.emptyList();
        }
        int defaultTtlInMinutes = cacheTtlConfig.getDefaultTtlInMinutes();
        if (CollectionUtils.isEmpty(cacheTtlConfig.getTtlInMinutes())) {
            return List.of(caffeineCacheManager(cacheNames, defaultTtlInMinutes));
        }
        return cacheNames.stream().map(cacheName -> {
            int ttlInMinutes = cacheTtlConfig.getTtlInMinutes().getOrDefault(cacheName, defaultTtlInMinutes);
            if (TEAM_ROLLUP_INFO.equals(cacheName)) {
                Integer personalTeamTtlInMinutes = cacheTtlConfig.getTtlInMinutes().get(PERSONAL_TEAM_ROLLUP_INFO);
                if (personalTeamTtlInMinutes != null) {
                    return caffeineCacheManagerWithVariableExpiry(List.of(cacheName), ttlInMinutes, personalTeamTtlInMinutes);
                }
            }
            return caffeineCacheManager(List.of(cacheName), ttlInMinutes);
        }).toList();
    }

    /**
     * Creates a Caffeine-only CacheManager for the specified cache names.
     * @param cacheNames        the cache names to manage
     * @param ttlInMinutes      the cache entry time to live in minutes
     * @return the CaffeineCacheManager
     */
    private CacheManager caffeineCacheManager(List<String> cacheNames, int ttlInMinutes) {
        log.debug("Configuring Caffeine cache for {} with ttlInMinutes: {}", cacheNames, ttlInMinutes);
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(cacheNames);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlInMinutes, TimeUnit.MINUTES));
        return cacheManager;
    }

    /**
     * Creates a Caffeine-only CacheManager with variable per-entry expiry for {@code TEAM_ROLLUP_INFO}.
     * Personal team entries use {@code personalTeamTtlInMinutes}; all others use {@code defaultTtlInMinutes}.
     *
     * @param cacheNames               the cache names to manage
     * @param defaultTtlInMinutes      TTL for non-personal team entries
     * @param personalTeamTtlInMinutes TTL for personal team entries
     * @return the CaffeineCacheManager
     */
    private CacheManager caffeineCacheManagerWithVariableExpiry(List<String> cacheNames,
                                                                 int defaultTtlInMinutes,
                                                                 int personalTeamTtlInMinutes) {
        log.debug("Configuring Caffeine cache for {} with defaultTtlInMinutes: {}, personalTeamTtlInMinutes: {}",
                cacheNames, defaultTtlInMinutes, personalTeamTtlInMinutes);
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(cacheNames);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfter(new Expiry<>() {
                    @Override
                    public long expireAfterCreate(Object key, Object value, long currentTime) {
                        return ttlNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                        return ttlNanos(value);
                    }

                    @Override
                    public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long ttlNanos(Object value) {
                        if (value instanceof TeamRollupView teamRollupView) {
                            TeamType type = teamRollupView.getType();
                            if (type == null) {
                                log.warn("TeamRollupView has null type for team: {}, using default TTL", teamRollupView.getName());
                                return TimeUnit.MINUTES.toNanos(defaultTtlInMinutes);
                            }
                            long ttlMinutes = TeamType.PERSONAL.equals(type) ? personalTeamTtlInMinutes : defaultTtlInMinutes;
                            return TimeUnit.MINUTES.toNanos(ttlMinutes);
                        }
                        return TimeUnit.MINUTES.toNanos(defaultTtlInMinutes);
                    }
                }));
        return cacheManager;
    }
}
