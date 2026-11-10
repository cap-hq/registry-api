// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.cvshealth.cap.registry.auth.oauth.JwksRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class JwksEndpointIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private JwksRepository jwksRepository;

    @Test
    void getJwksEndpoint_returns200WithKeys() {
        given()
                .when()
                .get("http://localhost:" + serverPort + "/auth/oauth2/jwks")
                .then()
                .statusCode(200)
                .contentType("application/json");

        Response response = given()
                .when()
                .get("http://localhost:" + serverPort + "/auth/oauth2/jwks")
                .then()
                .extract().response();

        String body = response.getBody().asString();
        assertThat(body).contains("\"keys\"");
        assertThat(body).contains("\"RSA\"");
    }

    @Test
    void getJwksEndpoint_kidMatchesDatabaseRecord() {
        List<JwkPublicKey> dbKeys = jwksRepository.findAll();
        assertThat(dbKeys).isNotEmpty();

        String responseBody = given()
                .when()
                .get("http://localhost:" + serverPort + "/auth/oauth2/jwks")
                .then()
                .statusCode(200)
                .extract().body().asString();

        dbKeys.forEach(key ->
                assertThat(responseBody).contains(key.getKid()));
    }

    @Test
    void getJwksEndpoint_responseContainsRequiredJwkFields() {
        String body = given()
                .when()
                .get("http://localhost:" + serverPort + "/auth/oauth2/jwks")
                .then()
                .statusCode(200)
                .extract().body().asString();

        // RFC 7517 required fields for RSA public key
        assertThat(body).contains("\"kty\"");
        assertThat(body).contains("\"n\"");
        assertThat(body).contains("\"e\"");
        assertThat(body).contains("\"kid\"");
    }
}
