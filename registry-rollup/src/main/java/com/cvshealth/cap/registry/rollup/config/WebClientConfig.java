// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Slf4j
public class WebClientConfig {

    public static final String API_SECRET = "X-Api-Secret";

    @Bean
    public WebClient webClient(
            @Value("${registry.base-uri}") String baseUri,
            @Value("${registry.api-secret:}") String apiSecret,
            @Value("${registry.codec.max-in-memory-size:8388608}") int maxInMemorySize) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .build();
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUri)
                .exchangeStrategies(strategies);
        if (StringUtils.isNotBlank(apiSecret)) {
            builder.defaultHeader(API_SECRET, apiSecret);
        } else {
            log.warn("Missing configuration for Registry API webClient '{}'", API_SECRET);
        }
        return builder.build();
    }

}
