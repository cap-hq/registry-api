// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.DownstreamApplication;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DownstreamApplicationConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.downstream-applications")
    public Map<String, DownstreamApplication> downstreamApplications() {return new HashMap<>();}
}
