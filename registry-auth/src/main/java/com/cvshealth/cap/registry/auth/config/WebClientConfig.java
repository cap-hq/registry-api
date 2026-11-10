// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(@Value("${registry.base-uri}") String baseUri,
                               @Value("${registry.api-secret}") String apiSecret) {
        return WebClient.builder()
                .baseUrl(baseUri)
                .defaultHeader("X-Api-Secret", apiSecret)
                .build();
    }

}
