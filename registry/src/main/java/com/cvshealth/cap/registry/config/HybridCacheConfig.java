// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.cache.hybrid")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class HybridCacheConfig {
    /**
     * Time-to-live for L1 (Caffeine) cache entries in minutes
     */
    private int l1CacheTtlInMinutes;
    
    /**
     * Time-to-live for L2 (Redis) cache entries in minutes
     */
    private int l2CacheTtlInMinutes;
    
    /**
     * Maximum size for the L1 (local Caffeine) cache
     */
    private int l1MaxSize;

    /**
     * Batch size for L2 (Redis) cache
     */
    private int l2CacheBatchSize;

    /**
     * Prefix for L2 (Redis) cache key
     */
    private String l2CacheKeyPrefix;
}
