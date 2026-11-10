// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    public static final String CACHE_TEAM = "cache_team";
    public static final String CACHE_AZURE = "cache_azure";
    public static final String CACHE_MEMBER = "cache_member";
    public static final String CACHE_LOB = "cache_lob";

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CompositeCacheManager cacheManager = new CompositeCacheManager();
        List<CacheManager> cacheManagers = Collections.singletonList(caffeineCacheManager());
        cacheManager.setCacheManagers(cacheManagers);
        return cacheManager;
    }

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        //add new cache here by separating with comma.
        cacheManager.setCacheNames(List.of(CACHE_TEAM, CACHE_AZURE, CACHE_MEMBER, CACHE_LOB));
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES) // Set TTL for 5 minutes
                .maximumSize(5000); // Set maximum size to 5000 entries
    }

}
