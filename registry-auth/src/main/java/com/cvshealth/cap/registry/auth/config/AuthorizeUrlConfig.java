// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.model.AuthorizeUrlCustomParameter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AuthorizeUrlConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.oauth2.authorize-url.custom-parameters")
    public List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters() {
        return new ArrayList<>();
    }
}
