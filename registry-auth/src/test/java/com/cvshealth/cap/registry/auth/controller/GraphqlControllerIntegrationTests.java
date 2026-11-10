// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class GraphqlControllerIntegrationTests extends ControllerIntegrationTests {

    @BeforeEach
    void setUp() {
        setUpBeforeEachTest();
    }

    @Test
    public void getSchema_returns200WithTextPlain() {
        given()
                .when()
                .get("/auth/v1/schema")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT);
    }

    @Test
    public void getSchema_responseContainsQueryType() {
        given()
                .when()
                .get("/auth/v1/schema")
                .then()
                .statusCode(200)
                .body(containsString("type Query"));
    }
}
