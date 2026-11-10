// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for the ApiForge API resource constraint check performed when an
 * application is deleted or its external id is changed.
 *
 * <p>All environment, state and approval state values are matched case-insensitively.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.api-resource-check")
public class ApiForgeConfig {

    /** When false the constraint check is skipped entirely. */
    private boolean enabled = false;

    /** ApiForge GraphQL endpoint. */
    private String url;

    private int maxRetries = 3;

    private int timeoutSeconds = 10;

    /** Only resources in this environment block the operation. */
    private String blockingEnvironment = "production";

    /**
     * Only api definition environments in these states block the operation.
     * An empty list disables state filtering. Not applicable to consumed APIs -
     * ApiForge does not expose a state on consumer environments.
     */
    private List<String> blockingStates = List.of("active");

    /** Only consumptions in these approval states block the operation. */
    private List<String> blockingApprovalStates = List.of("pending", "approved");
}

