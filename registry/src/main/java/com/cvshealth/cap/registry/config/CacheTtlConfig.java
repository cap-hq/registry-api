// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Component
@ConfigurationProperties("app.cache.ttl")
@Validated
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CacheTtlConfig {
    @Min(value = 1, message = "defaultTtlInMinutes must be at least 1")
    private int defaultTtlInMinutes;
    private Map<String, Integer> ttlInMinutes;

    @AssertTrue(message = "ttlInMinutes values must be at least 1")
    private boolean isTtlInMinutesValid() {
        if (CollectionUtils.isEmpty(ttlInMinutes)) {
            return true;
        }
        return ttlInMinutes.values().stream().allMatch(v -> v != null && v >= 1);
    }
}
