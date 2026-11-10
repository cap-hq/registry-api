// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.security.SecurityHelper;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

import static com.cvshealth.cap.registry.rollup.config.WebClientConfig.API_SECRET;
import static com.cvshealth.cap.registry.rollup.security.SecurityHelper.UNKNOWN_USER;

public class RegistryService {
    private final WebClient webClient;

    public RegistryService(WebClient webClient) {
        this.webClient = webClient;
    }

    protected HttpGraphQlClient constructGraphQLClient() {
        return HttpGraphQlClient.builder(webClient)
                .headers(h -> {
                    if (!h.containsHeader(API_SECRET)) {
                        String bearerToken = SecurityHelper.getBearerToken();
                        if (bearerToken != null) {
                            h.add("Authorization", "Bearer " + bearerToken);
                        } else {
                            String externalId = SecurityHelper.getCurrentUserExternalId();
                            if (externalId != null && !UNKNOWN_USER.equals(externalId)) {
                                h.add("X-External-User-Id", externalId);
                            }
                        }
                    }
                })
                .build();
    }
}
