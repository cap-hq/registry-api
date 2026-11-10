// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.cache")
@Getter
@Setter
public class CacheProperties {

    /** Default TTL (in minutes) applied to any cache that has no explicit override. */
    private long defaultTtlInMinutes = 5;

    /** Per-cache TTL overrides (in minutes), keyed by cache name. */
    private Map<String, Long> ttlInMinutes = new HashMap<>();
}
