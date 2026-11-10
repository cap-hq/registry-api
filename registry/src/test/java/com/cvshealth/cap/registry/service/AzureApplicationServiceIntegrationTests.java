// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.config.AuthProviderClientCheckJobConfig;
import com.cvshealth.cap.registry.model.CredentialInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AzureApplicationServiceIntegrationTests extends RegistryIntegrationTests {
    @Autowired
    private AzureApplicationService azureApplicationService;

    @Autowired
    private AuthProviderClientCheckJobConfig authProviderClientCheckConfig;

    @Test
    public void testClientCredentials() {
        authProviderClientCheckConfig.getClients().forEach(client -> {
            List<CredentialInfo> results = azureApplicationService.clientCredentials(client);
            assertThat(results.size()).isGreaterThanOrEqualTo(1);
            CredentialInfo credentialInfo = results.get(0);
            assertThat(credentialInfo.credentialName()).startsWith("dev-client-secret");
            assertThat(credentialInfo.ageInDays()).isGreaterThanOrEqualTo(0);
            assertThat(credentialInfo.expirationInDays()).isGreaterThanOrEqualTo(0);
        });
    }
}
