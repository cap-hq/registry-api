// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.LabeledRestClient;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

@Configuration
@Slf4j
@AllArgsConstructor
public class DownstreamApplicationRestClientConfig {
    /**
     * Used to check if downstream applications are being used by an Registry team
     * Downstream apps will provide a GET endpoint which accepts a team name as a path parameter
     * The endpoint's response will be json with a single boolean 'teamExists' field
     */

    private DownstreamApplicationConfig downstreamApplicationConfig;

    @Bean
    public List<LabeledRestClient> downstreamApplicationRestClients() {
        List<LabeledRestClient> applicationRestClients = new ArrayList<>();
        downstreamApplicationConfig.downstreamApplications().values()
                .forEach(config -> {
                    if (config.getToken() == null || config.getToken().isBlank()) {
                        throw new ApiException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "downstream-application-config-missing-token",
                                ExceptionReason.DOWNSTREAM_APPLICATION_CONFIG_TOKEN_MISSING,
                                String.format(ExceptionReason.DOWNSTREAM_APPLICATION_CONFIG_TOKEN_MISSING.label,
                                        config.getName())
                        );
                    }
                    applicationRestClients.add(LabeledRestClient.builder()
                            .label(config.getName())
                            .restClient(RestClient.builder()
                                    .baseUrl(config.getUrl())
                                    .defaultHeader("token", config.getToken())
                                    .build()).build());
                });

        return applicationRestClients;
    }
}
