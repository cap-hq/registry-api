// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.WebClientConfig;
import com.cvshealth.cap.registry.rollup.security.SecurityHelper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mockStatic;

public class RegistryServiceTests {

    private WireMockServer wireMock;
    private RegistryService service;
    private RegistryService serviceWithApiSecret;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor(wireMock.port());

        String baseUrl = wireMock.baseUrl();

        WebClient plainClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        service = new RegistryService(plainClient);

        WebClient clientWithSecret = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(WebClientConfig.API_SECRET, "test-secret")
                .build();
        serviceWithApiSecret = new RegistryService(clientWithSecret);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void constructGraphQLClient_withBearerToken_addsAuthorizationHeader() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{}}")));

        try (MockedStatic<SecurityHelper> mocked = mockStatic(SecurityHelper.class)) {
            mocked.when(SecurityHelper::getBearerToken).thenReturn("my-jwt-token");
            mocked.when(SecurityHelper::getCurrentUserExternalId).thenReturn("ext-123");

            service.constructGraphQLClient()
                    .document("{teams{id}}")
                    .execute()
                    .block();
        }

        wireMock.verify(postRequestedFor(urlEqualTo("/"))
                .withHeader("Authorization", equalTo("Bearer my-jwt-token"))
                .withoutHeader("X-External-User-Id"));
    }

    @Test
    void constructGraphQLClient_noBearerToken_withExternalId_addsExternalUserIdHeader() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{}}")));

        try (MockedStatic<SecurityHelper> mocked = mockStatic(SecurityHelper.class)) {
            mocked.when(SecurityHelper::getBearerToken).thenReturn(null);
            mocked.when(SecurityHelper::getCurrentUserExternalId).thenReturn("ext-123");

            service.constructGraphQLClient()
                    .document("{teams{id}}")
                    .execute()
                    .block();
        }

        wireMock.verify(postRequestedFor(urlEqualTo("/"))
                .withHeader("X-External-User-Id", equalTo("ext-123"))
                .withoutHeader("Authorization"));
    }

    @Test
    void constructGraphQLClient_noBearerToken_unknownUser_skipsAllAuthHeaders() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{}}")));

        try (MockedStatic<SecurityHelper> mocked = mockStatic(SecurityHelper.class)) {
            mocked.when(SecurityHelper::getBearerToken).thenReturn(null);
            mocked.when(SecurityHelper::getCurrentUserExternalId).thenReturn("UNKNOWN_USER");

            service.constructGraphQLClient()
                    .document("{teams{id}}")
                    .execute()
                    .block();
        }

        wireMock.verify(postRequestedFor(urlEqualTo("/"))
                .withoutHeader("Authorization")
                .withoutHeader("X-External-User-Id"));
    }

    @Test
    void constructGraphQLClient_noBearerToken_nullExternalId_skipsAllAuthHeaders() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{}}")));

        try (MockedStatic<SecurityHelper> mocked = mockStatic(SecurityHelper.class)) {
            mocked.when(SecurityHelper::getBearerToken).thenReturn(null);
            mocked.when(SecurityHelper::getCurrentUserExternalId).thenReturn(null);

            service.constructGraphQLClient()
                    .document("{teams{id}}")
                    .execute()
                    .block();
        }

        wireMock.verify(postRequestedFor(urlEqualTo("/"))
                .withoutHeader("Authorization")
                .withoutHeader("X-External-User-Id"));
    }

    @Test
    void constructGraphQLClient_withApiSecretHeader_skipsAuthHeaders() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{}}")));

        try (MockedStatic<SecurityHelper> mocked = mockStatic(SecurityHelper.class)) {
            mocked.when(SecurityHelper::getBearerToken).thenReturn("my-jwt-token");
            mocked.when(SecurityHelper::getCurrentUserExternalId).thenReturn("ext-123");

            serviceWithApiSecret.constructGraphQLClient()
                    .document("{teams{id}}")
                    .execute()
                    .block();
        }

        wireMock.verify(postRequestedFor(urlEqualTo("/"))
                .withHeader(WebClientConfig.API_SECRET, equalTo("test-secret"))
                .withoutHeader("Authorization")
                .withoutHeader("X-External-User-Id"));
    }
}
