// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CustomHibernateConfig implements HibernatePropertiesCustomizer {

    @Override
    public void customize(Map<String, Object> properties) {
        properties.put("hibernate.dialect", "com.cvshealth.cap.registry.repository.specification.CustomPostgresSQLDialect");
    }
}
