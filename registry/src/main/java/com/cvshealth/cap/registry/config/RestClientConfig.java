// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Configuration
public class RestClientConfig {

    public static String API_SECRET = "X-Api-Secret";

    @Bean
    public RestClient getRestClient(@Value("${registry-auth.base-uri}") String baseUri,
                                    @Value("${registry-auth.api-secret}") String apiSecret) {
        return RestClient.builder()
                .baseUrl(baseUri)
                .defaultHeader(API_SECRET, apiSecret)
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .build();
    }

}
