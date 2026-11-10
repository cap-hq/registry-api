// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.controller;

import graphql.GraphQL;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;

public class GraphqlControllerIntegrationTests extends ControllerTests {
    @Autowired
    protected GraphQL graphQl;
    private static final String EXTERNAL_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        setUpBeforeEachTest();
    }

    @Test
    public void getSchema() {
        given()
                .header("X-External-User-Id", EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .when()
                .get("/rollup/v1/schema")
                .then()
                .statusCode(200);
    }

    @Test
    public void getSchemaNoAuth() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/rollup/v1/schema")
                .then()
                .statusCode(200);
    }
}
