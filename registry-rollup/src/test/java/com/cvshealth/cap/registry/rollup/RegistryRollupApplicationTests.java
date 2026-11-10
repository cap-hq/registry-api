// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup;

import com.cvshealth.cap.registry.rollup.config.CorsConfig;
import com.cvshealth.cap.registry.rollup.config.JwtPropertyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the service the way an open-source deployment would: no profile, so only application.yaml and the files it
 * imports are loaded, and only the input a deployer must supply (the registry API secret). Environment-specific values
 * belong in the local/dev/prod/preview profiles; these tests fail if a generic default stops being generic or goes
 * missing.
 */
@ActiveProfiles("default")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "registry.api-secret=base-config-test-secret")
class RegistryRollupApplicationTests {

    @LocalServerPort
    private Integer serverPort;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void servesHealthWithBaseConfigOnly() {
        assertThat(environment.getActiveProfiles()).containsExactly("default");
        // only built outside the local/test profiles
        assertThat(context.containsBean("jwtSecurity")).isTrue();

        given().port(serverPort)
                .when().get("/rollup/actuator/health")
                .then().statusCode(200);
    }

    @Test
    void applicationNameIsGeneric() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("registry-rollup");
    }

    @Test
    void registryIsTheLocalDefault() {
        assertThat(environment.getProperty("registry.base-uri")).isEqualTo("http://localhost:8100/api/graphql");
    }

    @Test
    void registryAuthIsTheDefaultTokenIssuer(@Autowired JwtPropertyConfig jwtProperties) {
        assertThat(jwtProperties.getSources()).singleElement().satisfies(source -> {
            assertThat(source.getIssuerUri()).isEqualTo("http://localhost:8200/auth");
            assertThat(source.getJwkSetUri()).isEqualTo("http://localhost:8200/auth/oauth2/jwks");
        });
    }

    @Test
    void corsAllowsOnlyLocalhost(@Autowired CorsConfig corsConfig) {
        assertThat(corsConfig.getAllowedOriginPatterns()).containsExactly("http://localhost:*");
    }

    @Test
    void lineOfBusinessIsDisabledAndOrganizationUnset() {
        assertThat(environment.getProperty("app.line-business.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("app.line-business.organization.name")).isNull();
        assertThat(environment.getProperty("app.line-business.organization.manager-external-id")).isNull();
    }

    @Test
    void managerTitleLimitIsTheGenericList() {
        assertThat(Binder.get(environment).bind("app.manager-title-limit", Bindable.listOf(String.class)).get())
                .containsExactly("SVP", "Senior Vice President", "EVP", "Executive Vice President");
    }
}
