// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.LabeledClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
@AllArgsConstructor
public class ExternalResourceClientConfig {
    private ExternalResourceConfig externalResourceConfig;

    @Bean("externalResourceClients")
    public List<LabeledClient> externalResourceClients() {
        List<LabeledClient> clients = new ArrayList<>();
        externalResourceConfig.externalResources().values()
                .forEach(config -> {
                    switch (config.getClientType()) {
                        case GRAPHQL -> clients.add(LabeledClient.builder()
                                .label(config.getName())
                                .clientType(config.getClientType())
                                .webClient(() -> WebClient.builder().baseUrl(config.getUrl()).build())
                                .build());
                        case REST -> clients.add(LabeledClient.builder()
                                .label(config.getName())
                                .clientType(config.getClientType())
                                .restClient(RestClient.builder().baseUrl(config.getUrl()).build())
                                .build());
                    }
                });
        return clients;
    }
}
