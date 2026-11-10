// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.graphql;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.cvshealth.cap.registry.auth.job.AuthEventPartitionManager;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class AdminResolverIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthEventPartitionManager authEventPartitionManager;

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

        authEventPartitionManager.runMaintenance();
        jdbcTemplate.execute("DELETE FROM authentication_events_daily");
        // Delete from all partitions via parent table
        jdbcTemplate.execute("DELETE FROM authentication_events");
    }

    @AfterEach
    void tearDown() {
        wiremock.stop();
    }

    @Test
    void getJwks_returnsResults() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query", "{ getJwks { kid kty alg } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getJwks", not(empty()));
    }

    @Test
    void getAuthenticationEvents_noData_returnsEmpty() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDateTime: \"2020-01-01T00:00:00Z\", toDateTime: \"2099-12-31T23:59:59Z\"}) " +
                        "{ events { clientId authenticatedAt success } totalRecordCount totalPageCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEvents.totalRecordCount", equalTo(0))
                .body("data.getAuthenticationEvents.totalPageCount", equalTo(0))
                .body("data.getAuthenticationEvents.events", empty());
    }

    @Test
    void getAuthenticationEvents_withData_returnsPagedResults() {
        jdbcTemplate.execute(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, authenticated_at, success) " +
                "VALUES ('client-a', 'Client A', 'client_credentials', (now() AT TIME ZONE 'UTC'), true)");
        jdbcTemplate.execute(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, authenticated_at, success) " +
                "VALUES ('client-b', 'Client B', 'client_credentials', (now() AT TIME ZONE 'UTC'), false)");

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDateTime: \"2020-01-01T00:00:00Z\", toDateTime: \"2099-12-31T23:59:59Z\"}) " +
                        "{ events { clientId clientName success } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEvents.totalRecordCount", equalTo(2))
                .body("data.getAuthenticationEvents.events", hasSize(2));
    }

    @Test
    void getAuthenticationEvents_filterByClientId_returnsMatchingRows() {
        jdbcTemplate.execute(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, authenticated_at, success) " +
                "VALUES ('client-a', 'Client A', 'client_credentials', (now() AT TIME ZONE 'UTC'), true)");
        jdbcTemplate.execute(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, authenticated_at, success) " +
                "VALUES ('client-b', 'Client B', 'client_credentials', (now() AT TIME ZONE 'UTC'), true)");

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {clientId: \"client-a\", fromDateTime: \"2020-01-01T00:00:00Z\", toDateTime: \"2099-12-31T23:59:59Z\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEvents.totalRecordCount", equalTo(1))
                .body("data.getAuthenticationEvents.events[0].clientId", equalTo("client-a"));
    }

    @Test
    void getAuthenticationEvents_pagination_returnsCorrectPage() {
        for (int i = 1; i <= 5; i++) {
            jdbcTemplate.execute(
                    "INSERT INTO authentication_events (client_id, client_name, grant_type, authenticated_at, success) " +
                    "VALUES ('client-" + i + "', 'Client " + i + "', 'client_credentials', (now() AT TIME ZONE 'UTC'), true)");
        }

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 3}, " +
                        "filter: {fromDateTime: \"2020-01-01T00:00:00Z\", toDateTime: \"2099-12-31T23:59:59Z\"}) " +
                        "{ events { clientId } totalRecordCount totalPageCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEvents.totalRecordCount", equalTo(5))
                .body("data.getAuthenticationEvents.totalPageCount", equalTo(2))
                .body("data.getAuthenticationEvents.events", hasSize(3));
    }

    @Test
    void getAuthenticationEvents_filterByPrincipalOid_returnsMatchingRows() {
        jdbcTemplate.execute(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, principal_oid, authenticated_at, success) " +
                "VALUES ('client-a', 'Client A', 'client_credentials', 'oid-match', (now() AT TIME ZONE 'UTC'), true)");
        jdbcTemplate.execute(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, principal_oid, authenticated_at, success) " +
                "VALUES ('client-b', 'Client B', 'client_credentials', 'oid-other', (now() AT TIME ZONE 'UTC'), true)");

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {principalOid: \"oid-match\", fromDateTime: \"2020-01-01T00:00:00Z\", toDateTime: \"2099-12-31T23:59:59Z\"}) " +
                        "{ events { clientId principalOid } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEvents.totalRecordCount", equalTo(1))
                .body("data.getAuthenticationEvents.events[0].clientId", equalTo("client-a"))
                .body("data.getAuthenticationEvents.events[0].principalOid", equalTo("oid-match"));
    }

    @Test
    void getAuthenticationEventsDaily_noData_returnsEmpty() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDate: \"2020-01-01\", toDate: \"2099-12-31\"}) " +
                        "{ events { clientId eventDate eventCount } totalRecordCount totalPageCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEventsDaily.totalRecordCount", equalTo(0))
                .body("data.getAuthenticationEventsDaily.events", empty());
    }

    @Test
    void getAuthenticationEventsDaily_withData_returnsResults() {
        String today = LocalDate.now().toString();
        jdbcTemplate.execute(
                "INSERT INTO authentication_events_daily " +
                "(event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, success, event_count) " +
                "VALUES ('" + today + "', 'client-a', 'Client A', 'client_credentials', 'user1', 'oid1', 'agent1', true, 42)");

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {eventDate: \"" + LocalDate.now() + "\"}) " +
                        "{ events { clientId eventDate eventCount success } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEventsDaily.totalRecordCount", equalTo(1))
                .body("data.getAuthenticationEventsDaily.events[0].clientId", equalTo("client-a"))
                .body("data.getAuthenticationEventsDaily.events[0].eventCount", equalTo(42))
                .body("data.getAuthenticationEventsDaily.events[0].success", equalTo(true))
                .body("data.getAuthenticationEventsDaily.events[0].eventDate", notNullValue());
    }

    @Test
    void getAuthenticationEventsDaily_filterByClientId_returnsMatchingRows() {
        String today = LocalDate.now().toString();
        jdbcTemplate.execute(
                "INSERT INTO authentication_events_daily " +
                "(event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, success, event_count) " +
                "VALUES ('" + today + "', 'client-a', 'Client A', 'client_credentials', 'user1', 'oid1', 'agent1', true, 10)");
        jdbcTemplate.execute(
                "INSERT INTO authentication_events_daily " +
                "(event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, success, event_count) " +
                "VALUES ('" + today + "', 'client-b', 'Client B', 'client_credentials', 'user1', 'oid1', 'agent1', true, 5)");

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {clientId: \"client-a\", eventDate: \"" + LocalDate.now() + "\"}) " +
                        "{ events { clientId eventCount } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEventsDaily.totalRecordCount", equalTo(1))
                .body("data.getAuthenticationEventsDaily.events[0].clientId", equalTo("client-a"))
                .body("data.getAuthenticationEventsDaily.events[0].eventCount", equalTo(10));
    }

    @Test
    void getAuthenticationEvents_filterMissingFromDate_returnsValidationError() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {toDateTime: \"2026-01-31T23:59:59Z\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", not(empty()))
                .body("data.getAuthenticationEvents", nullValue());
    }

    @Test
    void getAuthenticationEvents_filterMissingToDate_returnsValidationError() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDateTime: \"2026-01-01T00:00:00Z\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", not(empty()))
                .body("data.getAuthenticationEvents", nullValue());
    }

    @Test
    void getAuthenticationEvents_filterWithBothDates_returnsResults() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEvents(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDateTime: \"2026-01-01T00:00:00Z\", toDateTime: \"2026-01-31T23:59:59Z\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", nullValue())
                .body("data.getAuthenticationEvents.totalRecordCount", equalTo(0));
    }

    @Test
    void getAuthenticationEventsDaily_filterWithNoDates_returnsValidationError() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {clientId: \"client-a\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", not(empty()))
                .body("data.getAuthenticationEventsDaily", nullValue());
    }

    @Test
    void getAuthenticationEventsDaily_filterWithEventDate_returnsResults() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {eventDate: \"" + LocalDate.now() + "\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", nullValue())
                .body("data.getAuthenticationEventsDaily.totalRecordCount", equalTo(0));
    }

    @Test
    void getAuthenticationEventsDaily_filterWithDateRange_returnsResults() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDate: \"2026-01-01\", toDate: \"2026-01-31\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", nullValue())
                .body("data.getAuthenticationEventsDaily.totalRecordCount", equalTo(0));
    }

    @Test
    void getAuthenticationEventsDaily_filterWithOnlyFromDate_returnsValidationError() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {fromDate: \"2026-01-01\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", not(empty()))
                .body("data.getAuthenticationEventsDaily", nullValue());
    }

    @Test
    void getAuthenticationEventsDaily_filterWithOnlyToDate_returnsValidationError() {
        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {toDate: \"2026-01-31\"}) " +
                        "{ events { clientId } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("errors", not(empty()))
                .body("data.getAuthenticationEventsDaily", nullValue());
    }

    @Test
    void getAuthenticationEventsDaily_filterByPrincipalOid_returnsMatchingRows() {
        String today = LocalDate.now().toString();
        jdbcTemplate.execute(
                "INSERT INTO authentication_events_daily " +
                "(event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, success, event_count) " +
                "VALUES ('" + today + "', 'client-a', 'Client A', 'client_credentials', 'user1', 'oid-match', 'agent1', true, 10)");
        jdbcTemplate.execute(
                "INSERT INTO authentication_events_daily " +
                "(event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, success, event_count) " +
                "VALUES ('" + today + "', 'client-b', 'Client B', 'client_credentials', 'user2', 'oid-other', 'agent1', true, 5)");

        given()
                .auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("query",
                        "{ getAuthenticationEventsDaily(paginationInput: {pageNumber: 1, pageSize: 10}, " +
                        "filter: {principalOid: \"oid-match\", eventDate: \"" + LocalDate.now() + "\"}) " +
                        "{ events { clientId principalOid eventCount } totalRecordCount } }"))
                .when().post("/auth/graphql")
                .then().statusCode(200)
                .body("data.getAuthenticationEventsDaily.totalRecordCount", equalTo(1))
                .body("data.getAuthenticationEventsDaily.events[0].clientId", equalTo("client-a"))
                .body("data.getAuthenticationEventsDaily.events[0].principalOid", equalTo("oid-match"))
                .body("data.getAuthenticationEventsDaily.events[0].eventCount", equalTo(10));
    }
}
