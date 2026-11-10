// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.model.CustomScope;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CustomScopeConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.scopes.authorization")
    public List<CustomScope> customAuthorizationScopes() {
        return new ArrayList<>();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.scopes.user-consent")
    public List<CustomScope> customUserConsentScopes() {
        return new ArrayList<>();
    }
}
