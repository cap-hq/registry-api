// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Docker stack (scripts/registry-stack) starts Registry Auth with exactly {@code localdocker,bootstrap} and
 * the secrets.yml keys in the container environment ({@code docker run --env-file}). These tests load that
 * profile set through Spring Boot's config-data pipeline so it stays self-contained on top of application.yml.
 */
class LocalDockerConfigurationTests {

    private static ConfigurableEnvironment dockerStackEnvironment() {
        var environment = new StandardEnvironment();
        // SystemEnvironmentPropertySource: container env vars bind with relaxed names (SPRING_CLOUD_AZURE_* -> spring.cloud.azure.*).
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("container", Map.of(
                "REGISTRY_API_SECRET", "local-test-secret",
                "SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_ID", "client-id",
                "SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_SECRET", "client-secret",
                "SPRING_CLOUD_AZURE_PROFILE_TENANT_ID", "tenant-id")));
        environment.setActiveProfiles("localdocker", "bootstrap");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    @Test
    void resolvesSecretsFromTheContainerEnvironment() {
        var environment = dockerStackEnvironment();

        assertThat(environment.getActiveProfiles()).containsExactly("localdocker", "bootstrap");
        assertThat(environment.getRequiredProperty("registry-auth.api-secret")).isEqualTo("local-test-secret");
        assertThat(environment.getRequiredProperty("registry.api-secret")).isEqualTo("local-test-secret");
        assertThat(environment.getRequiredProperty("spring.security.oauth2.client.registration.azure-idp.client-id"))
                .isEqualTo("client-id");
        assertThat(environment.getRequiredProperty("spring.cloud.azure.profile.tenant-id")).isEqualTo("tenant-id");
    }

    @Test
    void declaresTheDockerStackRuntimeSettingsItself() {
        var environment = dockerStackEnvironment();

        assertThat(environment.getRequiredProperty("server.port", Integer.class)).isEqualTo(8200);
        assertThat(environment.getRequiredProperty("spring.flyway.url")).isEqualTo("jdbc:postgresql://db_auth:5432/registry_auth");
        assertThat(environment.getRequiredProperty("app.oauth2.issuer")).isEqualTo("http://host.docker.internal:8200/auth");
        assertThat(environment.getRequiredProperty("registry.base-uri")).isEqualTo("http://registry:8100/api/graphql");
    }
}
