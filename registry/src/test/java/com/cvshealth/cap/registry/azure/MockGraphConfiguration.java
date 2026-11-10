// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.azure;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Replaces the real Microsoft Graph client in integration tests with one that talks to a local WireMock server, so no
 * test calls Azure or needs tenant credentials. Responses come from {@link MockGraphRouter}.
 *
 * <p>Imported by {@code RegistryIntegrationTests}, so every integration test gets it.
 */
@TestConfiguration
public class MockGraphConfiguration {

    @Bean(destroyMethod = "stop")
    public WireMockServer mockGraphServer() {
        WireMockServer server = new WireMockServer(options()
                .dynamicPort()
                .extensions(new MockGraphTransformer()));
        server.stubFor(any(anyUrl()).willReturn(aResponse()));
        server.start();
        return server;
    }

    /**
     * Named differently from the production {@code graphServiceClient} bean so both definitions exist. {@code @Primary}
     * then makes this one win for every injection point, regardless of bean registration order.
     */
    @Bean
    @Primary
    public GraphServiceClient mockGraphServiceClient(WireMockServer mockGraphServer) {
        GraphServiceClient client = new GraphServiceClient(new AnonymousAuthenticationProvider());
        client.getRequestAdapter().setBaseUrl("http://localhost:" + mockGraphServer.port() + "/v1.0");
        return client;
    }
}
