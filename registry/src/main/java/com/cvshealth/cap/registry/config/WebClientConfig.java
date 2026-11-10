// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Supplier;

import static com.cvshealth.cap.registry.config.RestClientConfig.API_SECRET;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebClientConfig {
    private final JsonMapper jsonMapper;

    @Bean(name = "rollupApiWebClient")
    public Supplier<WebClient> rollupWebClient(@Value("${registry-rollup.base-uri:MISSING}") String baseUri,
                                               @Value("${registry-rollup.api-secret:}") String apiSecret,
                                               @Value("${registry-rollup.codec.max-in-memory-size:8388608}") int maxInMemorySize) {
        if (!"MISSING".equals(baseUri)) {
            return () -> {
                WebClient.Builder builder = webClientBuilder()
                        .baseUrl(baseUri)
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize));
                if (StringUtils.isNotBlank(apiSecret)) {
                    builder.defaultHeader(API_SECRET, apiSecret);
                } else {
                    log.warn("Missing configuration for rollupApiWebClient '{}'", API_SECRET);
                }
                return builder.build();
            };
        } else {
            log.warn("registry-rollup.base-uri is MISSING");
            return () -> null;
        }
    }

    @Bean(name = "externalAppApiWebClient")
    public Supplier<WebClient> externalAppWebClient(@Value("${external-application-api.base-uri:MISSING}") String baseUri) {
        if (!"MISSING".equals(baseUri)) {
            return () -> webClientBuilder().baseUrl(baseUri).build();
        } else {
            log.warn("external-application-api.base-uri is MISSING");
            return () -> null;
        }
    }

    @Bean(name = "externalAppAuthWebClient")
    public Supplier<WebClient> externalAppAuthWebClient(@Value("${external-application-api.auth-provider.base-uri:MISSING}") String baseUri) {
        if (!"MISSING".equals(baseUri)) {
            return () -> webClientBuilder()
                    .baseUrl(baseUri)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            log.warn("external-application-api.auth-provider.base-uri is MISSING");
            return () -> null;
        }
    }

    private WebClient.Builder webClientBuilder() {
        return WebClient.builder().codecs(configurer -> {
            configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
            configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
        });
    }
}
