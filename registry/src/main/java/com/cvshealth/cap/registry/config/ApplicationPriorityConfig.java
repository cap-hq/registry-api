// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.ApplicationPriority;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationPriorityConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.application-priorities")
    public List<ApplicationPriority> applicationPriorities() {
        return new ArrayList<>();
    }
}
