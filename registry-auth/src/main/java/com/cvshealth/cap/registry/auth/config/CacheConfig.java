// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    public static final String JWKS_CACHE = "jwks";

    @Bean
    public CacheManager cacheManager(CacheProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(properties.getDefaultTtlInMinutes()))
        );
        properties.getTtlInMinutes().forEach((cacheName, ttlMinutes) ->
                cacheManager.registerCustomCache(cacheName,
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(ttlMinutes)).build())
        );
        return cacheManager;
    }
}
