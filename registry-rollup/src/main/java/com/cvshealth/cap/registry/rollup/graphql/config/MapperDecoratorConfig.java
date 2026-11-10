// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql.config;

import com.cvshealth.cap.registry.rollup.mapper.ManagerRollupMapperDecorator;
import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import com.cvshealth.cap.registry.rollup.service.LineOfBusinessRollupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MapperDecoratorConfig {

    @Bean
    List<MapperDecoratorProvider> mapperDecoratorProviders(
            LineOfBusinessRollupService lineOfBusinessRollupService,
            @Value("${app.manager-title-limit}") List<String> managerTitleLimit) {
        return List.of(
                new ManagerRollupMapperDecorator(managerTitleLimit, lineOfBusinessRollupService));
    }
}
