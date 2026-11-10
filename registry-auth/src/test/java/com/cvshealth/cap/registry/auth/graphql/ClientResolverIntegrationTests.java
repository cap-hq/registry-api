// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.graphql;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class ClientResolverIntegrationTests extends RegistryIntegrationTests {

    private WireMockServer wiremock;
    private String accessToken;

    @BeforeEach
    void setUp() {
        wiremock = new WireMockServer(options().port(8333));
        wiremock.start();
        wiremock.stubFor(post("/api/graphql")
                .withRequestBody(containing("getAuthClient"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "data": { "getAuthClient": {
                                    "clientId": "client-credentials-flow",
                                    "clientVersion": 1,
                                    "teams": [{"name": "Registry-Admin", "role": "OWNER"}]
                                }}}
                                """)));

        RestAssured.baseURI = "http://localhost:" + serverPort;
        accessToken = given()
                .auth().preemptive().basic("client-credentials-flow", "test3")
                .formParam("grant_type", "client_credentials")
                .when().post("/auth/oauth2/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    @AfterEach
    void tearDown() {
        wiremock.stop();
    }

    @Test
    void getClients_returnsResults() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getClients { clientId type } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getClients", not(empty()));
    }

    @Test
    void getClientById_knownClient_returnsClient() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getClientById(clientId: \"CapUIClient\") { clientId type } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getClientById.clientId", equalTo("CapUIClient"))
                .body("data.getClientById.type", notNullValue());
    }

    @Test
    void getClientById_unknownClient_returnsError() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getClientById(clientId: \"non-existent-client-xxx\") { clientId } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", not(empty()));
    }

    @Test
    void getClientsByIdOrName_byClientId_returnsMatch() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getClientsByIdOrName(clientIdOrName: \"CapUIClient\") { clientId } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getClientsByIdOrName", hasSize(greaterThanOrEqualTo(1)))
                .body("data.getClientsByIdOrName[0].clientId", equalTo("CapUIClient"));
    }

    @Test
    void getClientsByIdOrName_noMatch_returnsEmpty() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getClientsByIdOrName(clientIdOrName: \"zzz-no-match-zzz\") { clientId } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getClientsByIdOrName", empty());
    }

    @Test
    void getClientSecretExpirations_returnsResults() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getClientSecretExpirations { clientId clientIdIssuedAt ageInDays } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getClientSecretExpirations", not(empty()));
    }
}
