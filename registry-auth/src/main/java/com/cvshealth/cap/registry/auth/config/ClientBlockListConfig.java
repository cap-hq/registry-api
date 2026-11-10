// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client ids whose token requests are rejected before any registered-client lookup.
 * <p>
 * Bound as configuration properties rather than with {@code @Value} so that both a YAML list and a
 * comma-separated {@code APP_SECURITY_BLOCKED_CLIENT_IDS} environment variable resolve correctly. A YAML
 * sequence is exposed as indexed keys, which a single {@code @Value} placeholder would silently miss.
 */
@Component
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class ClientBlockListConfig {
    private List<String> blockedClientIds = new ArrayList<>();
}
