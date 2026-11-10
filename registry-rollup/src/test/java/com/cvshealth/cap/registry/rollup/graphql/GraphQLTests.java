// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql;

import com.cvshealth.cap.registry.rollup.RegistryIntegrationTests;
import com.cvshealth.cap.registry.rollup.security.AuthenticationToken;
import com.cvshealth.cap.registry.rollup.service.ApplicationRollupService;
import com.cvshealth.cap.registry.rollup.service.ApplicationService;
import com.cvshealth.cap.registry.rollup.service.TeamRollupService;
import com.cvshealth.cap.registry.rollup.service.TeamService;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;

public class GraphQLTests extends RegistryIntegrationTests {
    @Autowired
    protected TeamService teamService;
    @Autowired
    protected ApplicationService applicationService;
    @Autowired
    protected TeamRollupService teamRollupService;
    @Autowired
    protected ApplicationRollupService applicationRollupService;

    protected static final String GRAPHQL_PATH = "/rollup/graphql";

    protected static final String MEMBER1_EXTERNAL_ID = "00000000-0000-0000-0000-000000000001"; // "Tester, One" user id

    protected WireMockServer registryApiWiremock;

    protected void setUpBeforeEachTest() {
        RestAssured.baseURI = "http://localhost:" + serverPort;

        SecurityContextHolder.getContext().setAuthentication(new AuthenticationToken("integration-test"));

        registryApiWiremock = new WireMockServer(options().port(8177)); // app reg api mock server
        registryApiWiremock.start();
    }

    protected void setUpAfterEachTest() {
        registryApiWiremock.stop();
    }

    ValidatableResponse givenGraphQL(GraphQLQuery query, String externalIdHeader) {
        ValidatableResponse response = given()
                .header("X-External-User-Id", externalIdHeader)
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(GRAPHQL_PATH)
                .then()
                .statusCode(200)
                .and();
        // log graphql response - useful for debugging
        System.out.println(response.extract().as(Object.class));
        return response;
    }

    ValidatableResponse givenGraphQLNoAuthThen401(GraphQLQuery query) {
        // no auth header is provided
        return given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(GRAPHQL_PATH)
                .then()
                .statusCode(401);
    }

}
