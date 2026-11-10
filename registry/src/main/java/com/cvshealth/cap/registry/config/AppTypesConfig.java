// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.ComplianceType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class AppTypesConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.types.compliance-types")
    public List<ComplianceType> complianceTypes() {
        return new ArrayList<>();
    }

    @Bean
    public Set<String> complianceTypeNames() {
        return complianceTypes().stream().map(ComplianceType::getName).collect(Collectors.toSet());
    }
}