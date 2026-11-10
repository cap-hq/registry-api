// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Creates the ApiForge GraphQL client. Only registered when
 * {@code app.api-resource-check.enabled} is true.
 */
@Configuration
@Slf4j
@AllArgsConstructor
@ConditionalOnProperty(prefix = "app.api-resource-check", name = "enabled", havingValue = "true")
public class ApiForgeClientConfig {

    private final ApiForgeConfig apiForgeConfig;

    @Bean("apiForgeWebClient")
    public WebClient apiForgeWebClient() {
        log.info("ApiForge resource constraint check enabled - endpoint: {}", apiForgeConfig.getUrl());
        return WebClient.builder().baseUrl(apiForgeConfig.getUrl()).build();
    }
}

