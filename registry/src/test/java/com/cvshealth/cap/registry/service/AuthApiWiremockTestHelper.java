// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.json.JsonMapper;
import wiremock.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class AuthApiWiremockTestHelper {

    private final WireMockServer wiremockServer;
    private final List<OAuthClient> mockOAuthClients;

    public AuthApiWiremockTestHelper(OAuthClient client) {
        this(List.of(client));
    }

    public AuthApiWiremockTestHelper(List<OAuthClient> clients) {
        wiremockServer = new WireMockServer(options().port(8787));
        mockOAuthClients = clients;
    }

    public void start() {
        wiremockServer.start();
        wiremockServer.stubFor(get("/auth/v1/clients")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapMockClients(mockOAuthClients))));
        wiremockServer.stubFor(post("/auth/v1/clients/search")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mapMockClients(mockOAuthClients))));
    }

    public void stop() {
        wiremockServer.stop();
    }

    private static JsonNode mapMockClients(List<OAuthClient> clients) {
        wiremock.com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        return mapper.convertValue(clients, JsonNode.class);
    }

    public static OAuthClient mockAuthCodeFlowClient() {
        return OAuthClient.builder()
                        .id("auth-code-flow")
                        .clientId("auth-code-flow")
                        .clientName("auth-code-flow")
                        .clientSecret("*****")
                        .clientSecretExpiresAt(Instant.now())
                        .type(AuthClientType.USER)
                        .redirectUrls(List.of("http://localhost:8080/callback"))
                        .accessTokenExpireMinutes(5L)
                        .build();
    }

    public static List<OAuthClient> mockClientCredentialsFlowClient() {
        return List.of(
                OAuthClient.builder()
                        .id("client-credentials-flow")
                        .clientId("client-credentials-flow")
                        .clientName("client-credentials-flow")
                        .clientSecret("*****")
                        .clientSecretExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                        .type(AuthClientType.USER)
                        .redirectUrls(List.of("http://localhost:8080/callback"))
                        .accessTokenExpireMinutes(5L)
                        .lastAccessTokenIssuedAt(Instant.now().plus(1, ChronoUnit.HOURS))
                        .build(),
                OAuthClient.builder()
                        .id("client-credentials-flow-2")
                        .clientId("client-credentials-flow-v2")
                        .clientName("client-credentials-flow")
                        .clientSecret("*****")
                        .clientSecretExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                        .type(AuthClientType.USER)
                        .redirectUrls(List.of("http://localhost:8080/callback2"))
                        .accessTokenExpireMinutes(5L)
                        .lastAccessTokenIssuedAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .build()
        );
    }
}
