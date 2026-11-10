// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import com.azure.core.credential.TokenCredential;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphServiceClientConfiguration {

    @Bean
    public GraphServiceClient graphServiceClient(TokenCredential tokenCredential) {
        return new GraphServiceClient(tokenCredential);
    }
}
