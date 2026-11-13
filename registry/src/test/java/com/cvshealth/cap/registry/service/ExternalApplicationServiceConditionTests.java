// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.ExternalApplicationApiConfig;
import com.cvshealth.cap.registry.config.ExternalApplicationClientAuthConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@code app.external-application.enabled} flag on {@link ExternalApplicationService}. Uses a minimal
 * context rather than the full application because the bean's presence is decided at startup.
 */
class ExternalApplicationServiceConditionTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void serviceBeanIsAbsent_whenFlagIsNotSet() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ExternalApplicationService.class));
    }

    @Test
    void serviceBeanIsAbsent_whenFlagIsFalse() {
        contextRunner.withPropertyValues("app.external-application.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ExternalApplicationService.class));
    }

    @Test
    void serviceBeanIsPresent_whenFlagIsTrue() {
        contextRunner.withPropertyValues("app.external-application.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ExternalApplicationService.class));
    }

    @Configuration
    @Import(ExternalApplicationServiceImpl.class)
    static class TestConfig {
        @Bean
        Supplier<WebClient> externalAppApiWebClient() {
            return () -> null;
        }

        @Bean
        Supplier<WebClient> externalAppAuthWebClient() {
            return () -> null;
        }

        @Bean
        ExternalApplicationClientAuthConfig externalApplicationClientAuthConfig() {
            return new ExternalApplicationClientAuthConfig();
        }

        @Bean
        ExternalApplicationApiConfig externalApplicationApiConfig() {
            return new ExternalApplicationApiConfig();
        }
    }
}
