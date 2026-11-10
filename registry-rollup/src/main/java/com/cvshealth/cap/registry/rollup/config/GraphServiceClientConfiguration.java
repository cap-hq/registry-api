// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import com.azure.core.credential.TokenCredential;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class GraphServiceClientConfiguration {

    private static final String AZURE_CREDENTIAL_ENV_VAR = "SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_SECRET";

    @Bean
    public GraphServiceClient graphServiceClient(TokenCredential tokenCredential) {
        log.info("Registry Auth Provider Token Check (active:{})", getAuthProviderCredentialPrefix());

        return new GraphServiceClient(tokenCredential);
    }

    private static String getAuthProviderCredentialPrefix() {
        return System.getenv().containsKey(AZURE_CREDENTIAL_ENV_VAR) ? System.getenv(AZURE_CREDENTIAL_ENV_VAR).substring(0, 3) : null;
    }
}
