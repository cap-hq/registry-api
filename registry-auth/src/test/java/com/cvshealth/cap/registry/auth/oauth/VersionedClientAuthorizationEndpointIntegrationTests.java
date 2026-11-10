// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Regression coverage for the Spring Authorization Server 7 authorization-endpoint pre-validation filter.
 * <p>
 * A rotated client presents its original client_id (now only present as client_name on a versioned row).
 * The authorization request must reach the login redirect instead of being rejected with
 * {@code invalid_request: client_id} before the custom authentication providers run.
 */
@TestPropertySource(properties = "app.registered-client.allow-multiple-secrets-per-client=true")
public class VersionedClientAuthorizationEndpointIntegrationTests extends RegistryIntegrationTests {

    private static final String CLIENT_NAME = "RotatedUIClient";
    private static final String VERSIONED_CLIENT_ID = CLIENT_NAME + "-v2";
    private static final String REDIRECT_URI = "http://localhost:8080/oauth-callback";

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = "http://localhost:" + serverPort;
        if (registeredClientRepository.findByClientId(VERSIONED_CLIENT_ID) == null) {
            RegisteredClient template = registeredClientRepository.findByClientId("TestUIClient-v2");
            registeredClientRepository.save(RegisteredClient.from(template)
                    .id("rotated-ui-client-v2")
                    .clientId(VERSIONED_CLIENT_ID)
                    .clientName(CLIENT_NAME)
                    .clientSecret("{noop}rotated-ui-client-v2-secret")
                    .build());
        }
    }

    @Test
    public void whenAuthorizeWithClientNameOfVersionedClient_thenRedirectsToLogin() {
        given()
                .redirects().follow(false)
                .accept(ContentType.HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_NAME)
                .queryParam("scope", "openid profile email App.User")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "abc123")
                .when()
                .get("/auth/oauth2/authorize")
                .then()
                .statusCode(302)
                .header("Location", containsString("/auth/oauth2/authorization/"))
                .header("Location", not(containsString("error")));
    }

    @Test
    public void whenAuthorizeWithVersionedClientId_thenRedirectsToLogin() {
        given()
                .redirects().follow(false)
                .accept(ContentType.HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", VERSIONED_CLIENT_ID)
                .queryParam("scope", "openid profile email App.User")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "abc123")
                .when()
                .get("/auth/oauth2/authorize")
                .then()
                .statusCode(302)
                .header("Location", containsString("/auth/oauth2/authorization/"));
    }

    @Test
    public void whenAuthorizeWithUnknownClientId_thenInvalidRequest() {
        given()
                .redirects().follow(false)
                .accept(ContentType.HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", "no-such-client")
                .queryParam("scope", "openid")
                .queryParam("redirect_uri", REDIRECT_URI)
                .when()
                .get("/auth/oauth2/authorize")
                .then()
                .statusCode(400);
    }
}
