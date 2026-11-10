// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.AuthClientRequest;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.AuthClientEntity;
import com.cvshealth.cap.registry.persistent.TeamAuthClientEntity;
import com.cvshealth.cap.registry.persistent.TeamAuthClientId;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;
import wiremock.com.fasterxml.jackson.databind.json.JsonMapper;
import wiremock.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.*;

public class AuthClientResolverIntegrationTests extends GraphQLTests {
    protected WireMockServer authApiWiremock;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        authApiWiremock = new WireMockServer(options().port(8787)); // app reg auth api mock server
        authApiWiremock.start();
        registerAuthApiMock();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        authApiWiremock.stop();
    }

    @Test
    public void getAuthClients() {
        List<AuthClientEntity> authClients = List.of(
                AuthClientEntity.builder().clientId("client1").clientVersion(1).description("client 1").build(),
                AuthClientEntity.builder().clientId("client2").clientVersion(1).description("client 2").build(),
                AuthClientEntity.builder().clientId("client3").clientVersion(1).description("client 3").build()
        );
        authClientRepository.saveAll(authClients);
        GraphQLQuery query = new GraphQLQuery("query {getAuthClients(paginationInput: { pageSize: 100, pageNumber: 1 } " +
                "filter: {clientIds: [\"client3\", \"client1\"]}) " +
                "{authClients {clientId clientVersion description type lastAccessTokenIssuedAt} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getAuthClients.authClients", hasSize(2))
                .body("data.getAuthClients.totalPageCount", is(1))
                .body("data.getAuthClients.totalRecordCount", is(2))
                .body("data.getAuthClients.authClients[0].clientId", is("client1")) // sorted 1st
                .body("data.getAuthClients.authClients[0].clientVersion", is(1))
                .body("data.getAuthClients.authClients[0].description", is("client 1"))
                .body("data.getAuthClients.authClients[0].lastAccessTokenIssuedAt", notNullValue())
                .body("data.getAuthClients.authClients[1].clientId", is("client3")) // sorted 2nd
                .body("data.getAuthClients.authClients[1].clientVersion", is(1))
                .body("data.getAuthClients.authClients[1].description", is("client 3"))
                .body("data.getAuthClients.authClients[1].lastAccessTokenIssuedAt", notNullValue());
    }

    @Test
    public void getAuthClients_sameClientId_multipleVersions() {
        String clientId = "client1";
        List<AuthClientEntity> authClients = List.of(
                AuthClientEntity.builder().clientId(clientId).clientVersion(1).description("client 1").build(),
                AuthClientEntity.builder().clientId(clientId).clientVersion(2).description("client 1 v2").build()
        );
        authClientRepository.saveAll(authClients);
        GraphQLQuery query = new GraphQLQuery("query {getAuthClients(paginationInput: { pageSize: 100, pageNumber: 1 } " +
                "filter: {clientIds: [\"client1\"]}) " +
                "{authClients {clientId clientVersion description type} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getAuthClients.authClients", hasSize(2))
                .body("data.getAuthClients.totalPageCount", is(1))
                .body("data.getAuthClients.totalRecordCount", is(2))
                .body("data.getAuthClients.authClients[0].clientId", is(clientId)) // sorted 1st
                .body("data.getAuthClients.authClients[0].clientVersion", is(1))
                .body("data.getAuthClients.authClients[0].description", is("client 1"))
                .body("data.getAuthClients.authClients[1].clientId", is(clientId)) // sorted 2nd
                .body("data.getAuthClients.authClients[1].clientVersion", is(2))
                .body("data.getAuthClients.authClients[1].description", is("client 1 v2"));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void getAuthClientsComposite() {
        String graphQL = """
                query {getAuthClients(paginationInput: {pageSize: 100, pageNumber: 1}) {
                    authClients {
                        clientId description type teams {
                            name type description externalName members { name } applications { name priority complianceTypes informationClassification }}}
                    totalPageCount totalRecordCount }}
                """;
        GraphQLQuery query = new GraphQLQuery(graphQL);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getAuthClients.authClients", hasSize(4))
                .body("data.getAuthClients.totalPageCount", is(1))
                .body("data.getAuthClients.totalRecordCount", is(4))
                .body("data.getAuthClients.authClients[0].clientId", is("auth-code-flow")) // sorted 1st
                .body("data.getAuthClients.authClients[0].description", is("Sample - auth code flow"))
                .body("data.getAuthClients.authClients[0].teams", hasSize(1))
                .body("data.getAuthClients.authClients[0].teams[0].name", is("Team-1"))
                .body("data.getAuthClients.authClients[0].teams[0].externalName", is("AppReg_Team-1"))
                .body("data.getAuthClients.authClients[0].teams[0].applications.find {it.name == 'app8'}.priority", is(5))
                .body("data.getAuthClients.authClients[0].teams[0].applications.find {it.name == 'app8'}.complianceTypes", is(List.of("pci")))
                .body("data.getAuthClients.authClients[0].teams[0].applications.find {it.name == 'app8'}.informationClassification", is("RESTRICTED"));
    }

    @Test
    public void getAuthClientsNoAuth() {
        GraphQLQuery query = new GraphQLQuery("query {getAuthClients {id clientId}}");

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getAuthClient_noVersion() {
        AuthClientEntity entity = AuthClientEntity.builder().clientId("client1").clientVersion(1).description("client 1").build();
        authClientRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getAuthClient(clientId: \"%s\") {clientId clientVersion}}", entity.getClientId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getAuthClient.clientId", is(entity.getClientId()))
                .body("data.getAuthClient.clientVersion", is(entity.getClientVersion()));
    }

    @Test
    public void getAuthClient_withVersion() {
        AuthClientEntity entity = AuthClientEntity.builder().clientId("client1").clientVersion(1).description("client 1").build();
        authClientRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getAuthClient(clientId: \"%s\", clientVersion: %s) {clientId clientVersion}}", entity.getClientId(), entity.getClientVersion()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getAuthClient.clientId", is(entity.getClientId()))
                .body("data.getAuthClient.clientVersion", is(entity.getClientVersion()));
    }

    @Test
    public void getAuthClientNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getAuthClient(clientId: \"%s\") {clientId}}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getAuthClientNotFound() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getAuthClient(clientId: \"%s\", clientVersion: %s) {clientId clientVersion}}", new Random().nextLong(), 1));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.AUTH_CLIENT_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void createAuthClient() {
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-test")
                .description("this is a test")
                .type(AuthClientType.NON_USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createAuthClient(authClient: { " +
                "teamName: \"%s\" " +
                "clientId: \"%s\" " +
                "description: \"%s\" " +
                "type: %s\n " +
                "}) {id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        String clientSecret = "12345";
        authApiWiremock.stubFor(post("/auth/v1/client")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                        .clientId(request.getClientId())
                                        .clientName(request.getClientId())
                                        .clientSecret(clientSecret)
                                        .clientSecretExpiresAt(Instant.now())
                                        .type(request.getType())
                                        .redirectUrls(null)
                                        .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createAuthClient.id", is(notNullValue()))
                .body("data.createAuthClient.clientId", is(request.getClientId()))
                .body("data.createAuthClient.clientVersion", is(1))
                .body("data.createAuthClient.clientSecret", is(clientSecret))
                .body("data.createAuthClient.clientSecretExpiresAt", is(notNullValue()))
                .body("data.createAuthClient.type", is(request.getType().name()))
                .body("data.createAuthClient.description", is(request.getDescription()))
                .body("data.createAuthClient.redirectUrls", is(nullValue()))
                .body("data.createAuthClient.refreshTokenExpireMinutes", is(nullValue()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void createAuthClient_version2() {
        String clientId = "auth-code-flow";
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(clientId)
                .description("auth-code-flow v2")
                .type(AuthClientType.NON_USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        authApiWiremock.stubFor(get("/auth/v1/client/" + clientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(clientId)
                                .clientName(request.getClientId())
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(request.getType())
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        String clientSecret = "12345";
        String authClientId = "auth-code-flow-v2";
        authApiWiremock.stubFor(post("/auth/v1/client")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(authClientId)
                                .clientName(request.getClientId())
                                .clientSecret(clientSecret)
                                .clientSecretExpiresAt(Instant.now())
                                .type(request.getType())
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createAuthClient.id", is(notNullValue()))
                .body("data.createAuthClient.clientId", is(request.getClientId()))
                .body("data.createAuthClient.clientVersion", is(2))
                .body("data.createAuthClient.clientSecret", is(clientSecret))
                .body("data.createAuthClient.clientSecretExpiresAt", is(notNullValue()))
                .body("data.createAuthClient.type", is(request.getType().name()))
                .body("data.createAuthClient.description", is(request.getDescription()))
                .body("data.createAuthClient.redirectUrls", is(nullValue()))
                .body("data.createAuthClient.refreshTokenExpireMinutes", is(nullValue()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void createAuthClient_version2_whenDifferentRedirectUrlsProvided_shouldBeInvalid() {
        String clientId = "auth-code-flow";
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(clientId)
                .description("auth-code-flow v2")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "redirectUrls: [\"http://localhost:8080/callback\"] " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        authApiWiremock.stubFor(get("/auth/v1/client/" + clientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(clientId)
                                .clientName(request.getClientId())
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(request.getType())
                                .redirectUrls(List.of())
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors", is(notNullValue()))
                .body("errors", hasSize(1))
                .body("errors[0].extensions.reason", is((ExceptionReason.AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE.toString())));
    }

    @Test
    public void createAuthClientNoAuth() {
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-test")
                .description("this is a test")
                .type(AuthClientType.NON_USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "type: %s\n " +
                        "}) {id clientId clientSecret description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void updateAuthClient_withNoPreviousVersion() {
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-code-flow")
                .description("this is a test")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "redirectUrls: [\"http://localhost:8080/callback\"] " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        // mock app reg auth api responses
        String authClientId = request.getClientId();
        ObjectMapper mapper = createObjectMapper();
        authApiWiremock.stubFor(get("/auth/v1/client/" + authClientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(authClientId)
                                .clientName(request.getClientId())
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(request.getType())
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        String clientSecret = "12345";
        authApiWiremock.stubFor(put("/auth/v1/client")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(authClientId)
                                .clientName(request.getClientId())
                                .clientSecret(clientSecret)
                                .type(request.getType())
                                .redirectUrls(null)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateAuthClient.id", is(notNullValue()))
                .body("data.updateAuthClient.clientId", is(request.getClientId()))
                .body("data.updateAuthClient.clientVersion", is(1))
                .body("data.updateAuthClient.clientSecret", is(clientSecret))
                .body("data.updateAuthClient.clientSecretExpiresAt", is(nullValue()))
                .body("data.updateAuthClient.type", is(request.getType().name()))
                .body("data.updateAuthClient.description", is(request.getDescription()))
                .body("data.updateAuthClient.redirectUrls", is(nullValue()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void updateAuthClient_withDifferentRedirectUrlsComparedToPreviousVersion_shouldBeInvalid() {
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-code-flow")
                .description("this is a test")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "redirectUrls: [\"http://localhost:8080/callback\"] " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        // mock app reg auth api responses
        String authClientId = request.getClientId();
        ObjectMapper mapper = createObjectMapper();
        authApiWiremock.stubFor(get("/auth/v1/client/" + authClientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(authClientId)
                                .clientName(request.getClientId())
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(request.getType())
                                .redirectUrls(List.of())
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        authApiWiremock.stubFor(get("/auth/v1/client/" + authClientId + "-v0")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(authClientId)
                                .clientName(request.getClientId())
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(request.getType())
                                .redirectUrls(List.of())
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors", is(notNullValue()))
                .body("errors", hasSize(1))
                .body("errors[0].extensions.reason", is((ExceptionReason.AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE.toString())));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void updateAuthClient_forbiddenWhenNotTeamOwner() {
        // MEMBER2 is a MEMBER (not OWNER) of Team-1, so they cannot update auth clients in Team-1
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-code-flow")
                .description("this is a test")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateAuthClientNoAuth() {
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-code-flow")
                .description("this is a test")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "type: %s\n " +
                        "}) {id clientId clientSecret description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void resetAuthClient_WithVersion() {
        String clientId = "auth-code-flow";
        Integer clientVersion = 1;
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {resetAuthClientSecret(clientId: \"%s\", clientVersion: %s) " +
                        "{id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                clientId, clientVersion));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        String clientSecret = "12345";
        authApiWiremock.stubFor(get("/auth/v1/client/" + clientId + "/secret")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(clientId)
                                .clientName(clientId)
                                .clientSecret(clientSecret)
                                .clientSecretExpiresAt(Instant.now())
                                .type(AuthClientType.USER)
                                .redirectUrls(List.of("http://localhost:8543/callback"))
                                .accessTokenExpireMinutes(5L)
                                .refreshTokenExpireMinutes(10L)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.resetAuthClientSecret.id", is(notNullValue()))
                .body("data.resetAuthClientSecret.clientId", is(clientId))
                .body("data.resetAuthClientSecret.clientVersion", is(clientVersion))
                .body("data.resetAuthClientSecret.clientSecret", is(clientSecret))
                .body("data.resetAuthClientSecret.clientSecretExpiresAt", is(notNullValue()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void resetAuthClient_noVersion() {
        String clientId = "auth-code-flow";
        Integer clientVersion = 1;
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {resetAuthClientSecret(clientId: \"%s\") " +
                        "{id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                clientId));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        String clientSecret = "12345";
        authApiWiremock.stubFor(get("/auth/v1/client/" + clientId + "/secret")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(clientId)
                                .clientName(clientId)
                                .clientSecret(clientSecret)
                                .clientSecretExpiresAt(Instant.now())
                                .type(AuthClientType.USER)
                                .redirectUrls(List.of("http://localhost:8543/callback"))
                                .accessTokenExpireMinutes(5L)
                                .refreshTokenExpireMinutes(10L)
                                .build(), JsonNode.class))));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.resetAuthClientSecret.id", is(notNullValue()))
                .body("data.resetAuthClientSecret.clientId", is(clientId))
                .body("data.resetAuthClientSecret.clientVersion", is(clientVersion))
                .body("data.resetAuthClientSecret.clientSecret", is(clientSecret))
                .body("data.resetAuthClientSecret.clientSecretExpiresAt", is(notNullValue()));
    }

    @Test
    public void resetAuthClientNoAuth() {
        String clientId = "auth-code-flow";
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {resetAuthClientSecret(clientId: \"%s\") " +
                        "{id clientId clientSecret description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                clientId));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void deleteAuthClient_withVersion() {
        String clientId = "client-credentials-flow";
        Integer clientVersion = 2;
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteAuthClient(clientId: \"%s\", clientVersion: %s)}", clientId, clientVersion));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        String authClientId = "client-credentials-flow-v2";
        authApiWiremock.stubFor(get("/auth/v1/client/" + authClientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(authClientId)
                                .clientName(clientId)
                                .clientSecret("clientSecret")
                                .clientSecretExpiresAt(Instant.now())
                                .type(AuthClientType.NON_USER)
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));
        authApiWiremock.stubFor(delete("/auth/v1/client/" + authClientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(204)));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteAuthClient", is(true));
    }

    @Test
    public void deleteAuthClientNoAuth() {
        String clientId = "auth-code-flow";
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteAuthClient(clientId: \"%s\")}", clientId));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void deleteAuthClient_noVersion() {
        String clientId = "client-credentials-flow";
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteAuthClient(clientId: \"%s\")}", clientId));

        // mock app reg auth api response
        ObjectMapper mapper = createObjectMapper();
        authApiWiremock.stubFor(get("/auth/v1/client/" + clientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(clientId)
                                .clientName(clientId)
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(AuthClientType.USER)
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));
        authApiWiremock.stubFor(get("/auth/v1/client/" + clientId + "-v2")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(clientId)
                                .clientName(clientId)
                                .clientSecret("*****")
                                .clientSecretExpiresAt(null)
                                .type(AuthClientType.USER)
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        authApiWiremock.stubFor(delete("/auth/v1/client/" + clientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(204)));
        authApiWiremock.stubFor(delete("/auth/v1/client/" + clientId + "-v2")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(204)));


        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteAuthClient", is(true));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void updateAuthClient_nonUserCaller_sameTeam_succeedsForUserClients() {
        // Register a NON_USER auth client on Team-1 to act as the caller
        String callerClientId = "team1-service-client";
        AuthClientEntity callerEntity = authClientRepository.save(
                AuthClientEntity.builder().clientId(callerClientId).clientVersion(1).description("NON_USER caller on Team-1").build());
        Long team1Id = teamRepository.findByName("Team-1").get().getId();
        teamAuthClientRepository.save(TeamAuthClientEntity.builder()
                .id(TeamAuthClientId.builder().teamId(team1Id).authClientId(callerEntity.getId()).build())
                .build());

        // Target: auth-code-flow is a USER auth client on Team-1
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-code-flow")
                .description("updated by service client")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "redirectUrls: [\"http://localhost:8080/callback\"] " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion clientSecret clientSecretExpiresAt description type redirectUrls accessTokenExpireMinutes refreshTokenExpireMinutes}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        ObjectMapper mapper = createObjectMapper();

        // Stub caller auth client lookup — NON_USER type
        authApiWiremock.stubFor(get("/auth/v1/client/" + callerClientId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(callerClientId)
                                .clientName(callerClientId)
                                .clientSecret("*****")
                                .type(AuthClientType.NON_USER)
                                .build(), JsonNode.class))));

        // Stub target auth client lookup — USER type
        authApiWiremock.stubFor(get("/auth/v1/client/" + request.getClientId())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(request.getClientId())
                                .clientName(request.getClientId())
                                .clientSecret("*****")
                                .type(AuthClientType.USER)
                                .redirectUrls(null)
                                .refreshTokenExpireMinutes(null)
                                .build(), JsonNode.class))));

        String updatedSecret = "updated-secret-789";
        authApiWiremock.stubFor(put("/auth/v1/client")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapper.convertValue(OAuthClient.builder()
                                .clientId(request.getClientId())
                                .clientName(request.getClientId())
                                .clientSecret(updatedSecret)
                                .type(AuthClientType.USER)
                                .redirectUrls(null)
                                .build(), JsonNode.class))));

        givenGraphQLNonUserClient(query, callerClientId)
                .body("data.updateAuthClient.id", is(notNullValue()))
                .body("data.updateAuthClient.clientId", is(request.getClientId()))
                .body("data.updateAuthClient.clientVersion", is(1))
                .body("data.updateAuthClient.clientSecret", is(updatedSecret))
                .body("data.updateAuthClient.type", is(request.getType().name()))
                .body("data.updateAuthClient.description", is(request.getDescription()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void updateAuthClient_nonUserCaller_differentTeam_accessDenied() {
        // client-credentials-flow is a NON_USER client on Team-5, not Team-1
        String callerClientId = "client-credentials-flow";
        AuthClientRequest request = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("auth-code-flow")
                .description("should be denied")
                .type(AuthClientType.USER)
                .build();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateAuthClient(authClient: { " +
                        "teamName: \"%s\" " +
                        "clientId: \"%s\" " +
                        "description: \"%s\" " +
                        "type: %s\n " +
                        "}) {id clientId clientVersion}}",
                request.getTeamName(), request.getClientId(), request.getDescription(), request.getType()));

        givenGraphQLNonUserClient(query, callerClientId)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    private void registerAuthApiMock() {
        authApiWiremock.stubFor(get("/auth/v1/clients")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockClients())));
        authApiWiremock.stubFor(post("/auth/v1/clients/search")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockClients())));
    }

    private JsonNode mockClients() {
        ObjectMapper mapper = createObjectMapper();
        Instant now = Instant.now();
        List<OAuthClient> oAuthClients = List.of(
                OAuthClient.builder()
                        .id("client1")
                        .clientId("client1")
                        .clientName("client1")
                        .clientSecret("*****")
                        .type(AuthClientType.USER)
                        .redirectUrls(List.of("http://localhost:8080/callback"))
                        .accessTokenExpireMinutes(5L)
                        .lastAccessTokenIssuedAt(now.minus(60, ChronoUnit.MINUTES))
                        .build(),
                OAuthClient.builder()
                        .id("client2")
                        .clientId("client2")
                        .clientName("client2")
                        .type(AuthClientType.USER_PKCE)
                        .redirectUrls(List.of("http://localhost:8080/callback"))
                        .accessTokenExpireMinutes(5L)
                        .build(),
                OAuthClient.builder()
                        .id("client3")
                        .clientId("client3")
                        .clientName("client3")
                        .clientSecret("*****")
                        .type(AuthClientType.NON_USER)
                        .accessTokenExpireMinutes(5L)
                        .lastAccessTokenIssuedAt(now.minus(4, ChronoUnit.MINUTES))
                        .build(),
                OAuthClient.builder()
                        .id("auth-code-flow")
                        .clientId("auth-code-flow")
                        .clientName("auth-code-flow")
                        .clientSecret("*****")
                        .type(AuthClientType.USER)
                        .redirectUrls(List.of("http://localhost:8080/callback"))
                        .accessTokenExpireMinutes(5L)
                        .build(),
                OAuthClient.builder()
                        .id("auth-code-flow-pkce")
                        .clientId("auth-code-flow-pkce")
                        .clientName("auth-code-flow-pkce")
                        .type(AuthClientType.USER_PKCE)
                        .redirectUrls(List.of("http://localhost:8080/callback"))
                        .accessTokenExpireMinutes(5L)
                        .build(),
                OAuthClient.builder()
                        .id("client-credentials-flow")
                        .clientId("client-credentials-flow")
                        .clientName("client-credentials-flow")
                        .clientSecret("*****")
                        .type(AuthClientType.NON_USER)
                        .accessTokenExpireMinutes(5L)
                        .build(),
                OAuthClient.builder()
                        .id("client-credentials-flow-v2")
                        .clientId("client-credentials-flow-v2")
                        .clientName("client-credentials-flow")
                        .clientSecret("*****")
                        .type(AuthClientType.NON_USER)
                        .accessTokenExpireMinutes(5L)
                        .build()
        );
        return mapper.convertValue(oAuthClients, JsonNode.class);
    }

    private ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }
}
