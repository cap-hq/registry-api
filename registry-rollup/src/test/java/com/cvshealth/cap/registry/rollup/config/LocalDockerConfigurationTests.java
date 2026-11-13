// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Docker stack (scripts/registry-stack) starts Rollup with exactly {@code localdocker} and the shared
 * {@code REGISTRY_API_SECRET} in the container environment ({@code docker run --env-file}). These tests load
 * that profile through Spring Boot's config-data pipeline so it stays self-contained on top of application.yaml.
 */
class LocalDockerConfigurationTests {

    private static ConfigurableEnvironment dockerStackEnvironment() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("container", Map.of("REGISTRY_API_SECRET", "local-test-secret")));
        environment.setActiveProfiles("localdocker");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    @Test
    void resolvesApiSecretFromTheContainerEnvironment() {
        var environment = dockerStackEnvironment();

        assertThat(environment.getActiveProfiles()).containsExactly("localdocker");
        assertThat(environment.getRequiredProperty("registry.api-secret")).isEqualTo("local-test-secret");
    }

    @Test
    void declaresTheDockerStackRuntimeSettingsItself() {
        var environment = dockerStackEnvironment();

        assertThat(environment.getRequiredProperty("server.port", Integer.class)).isEqualTo(8150);
        assertThat(environment.getRequiredProperty("registry.base-uri")).isEqualTo("http://registry:8100/api/graphql");
        assertThat(environment.getRequiredProperty("graphql.spqr.gui.enabled", Boolean.class)).isTrue();
        assertThat(environment.getRequiredProperty("spring.security.oauth2.resourceserver.jwt.sources[0].jwk-set-uri"))
                .isEqualTo("http://registry-auth:8200/auth/oauth2/jwks");
        assertThat(environment.getRequiredProperty("app.rollup.executor.user.pool-size", Integer.class)).isEqualTo(2);
        assertThat(environment.getRequiredProperty("app.cors.allowed-headers[4]")).isEqualTo("X-External-User-Id");
    }
}
