// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class JwksControllerIntegrationTests extends ControllerIntegrationTests {

    @BeforeEach
    void setUp() {
        setUpBeforeEachTest();
    }

    @Test
    public void getJwks() {
        JwkPublicKey[] keys = given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/jwks")
                .then()
                .statusCode(200)
                .extract()
                .as(JwkPublicKey[].class);
        org.hamcrest.MatcherAssert.assertThat("JWKS list is not null", keys, notNullValue());
    }

    @Test
    public void getJwksNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/jwks")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }
}
