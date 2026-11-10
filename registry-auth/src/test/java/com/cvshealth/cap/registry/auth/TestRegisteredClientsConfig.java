// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import com.cvshealth.cap.registry.auth.config.ClientSecretsConfig;
import com.cvshealth.cap.registry.auth.oauth.RegisteredClients;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@TestConfiguration
@Import({RegisteredClients.class, ClientSecretsConfig.class})
class TestRegisteredClientsConfig {

    @Bean
    ApplicationRunner loadRegisteredClients(RegisteredClients registeredClients,
                                            RegisteredClientRepository registeredClientRepository) {
        return args -> registeredClients.getRegisteredClients().forEach(registeredClientRepository::save);
    }
}
