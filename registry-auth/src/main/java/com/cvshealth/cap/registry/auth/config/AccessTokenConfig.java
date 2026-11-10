// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.model.CustomScopeType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.access-token")
@Getter
@Setter
public class AccessTokenConfig {

    private long defaultExpireMinutes = 30;

    /**
     * Per-scope access token TTL overrides (in minutes), keyed by {@link CustomScopeType}.
     * When a scope has an entry here, the issued access token's expiry will be set to this value
     * instead of the registered client's default TTL. When absent or empty, no override is applied.
     *
     * Example YAML configuration:
     * <pre>
     * app.access-token.scope-expire-minutes:
     *   OPEN_POLICY_AGENT: 480
     *   REGISTRY: 30
     * </pre>
     */
    private Map<CustomScopeType, Long> scopeExpireMinutes = new HashMap<>();
}
