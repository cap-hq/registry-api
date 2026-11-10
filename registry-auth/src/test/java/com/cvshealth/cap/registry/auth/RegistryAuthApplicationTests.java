// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import com.cvshealth.cap.registry.auth.config.ClientBlockListConfig;
import com.cvshealth.cap.registry.auth.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Starts the service the way an open-source deployment would: no profile, so only application.yml and the files it
 * imports are loaded, and only the inputs a deployer must supply (the database from {@link RegistryTestContainer},
 * the two API secrets, and the Azure sign-in client). The mock OIDC server stands in for the deployer's Entra tenant.
 * Environment-specific values belong in the local/dev/prod/preview profiles; these tests fail if a generic default
 * stops being generic or goes missing.
 */
@ActiveProfiles("default")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "registry-auth.api-secret=base-config-test-secret",
        "registry.api-secret=base-config-test-secret",
        "SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_ID=base-config-test-client",
        "SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_SECRET=base-config-test-client-secret",
        "spring.security.oauth2.client.provider.azure.issuer-uri=http://localhost:4012"
})
class RegistryAuthApplicationTests extends RegistryTestContainer {

    @LocalServerPort
    private Integer serverPort;

    @Autowired
    private Environment environment;

    @Test
    void servesDiscoveryWithBaseConfigOnly() {
        assertThat(environment.getActiveProfiles()).containsExactly("default");

        given().port(serverPort)
                .when().get("/auth/.well-known/openid-configuration")
                .then().statusCode(200).body("issuer", notNullValue());
    }

    @Test
    void registryIsTheLocalDefault() {
        assertThat(environment.getProperty("registry.base-uri")).isEqualTo("http://localhost:8100/api/graphql");
    }

    @Test
    void corsAllowsOnlyLocalhost(@Autowired CorsConfig corsConfig) {
        assertThat(corsConfig.getAllowedOriginPatterns()).containsExactly("http://localhost:*");
    }

    @Test
    void agentValuesAreGeneric() {
        assertThat(Binder.get(environment)
                .bind("app.oauth2.authorize-url.custom-parameters[0].values", Bindable.listOf(String.class)).get())
                .containsExactly("copilot", "claude-code");
    }

    @Test
    void noClientsAreBlocked(@Autowired ClientBlockListConfig clientBlockListConfig) {
        assertThat(clientBlockListConfig.getBlockedClientIds()).isEmpty();
    }
}
