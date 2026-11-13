// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import static com.cvshealth.cap.registry.util.WiremockUtils.getJsonNode;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class ExternalAppApiWiremockTestHelper {

    private final WireMockServer apiWiremock;
    private final WireMockServer authWiremock;

    public ExternalAppApiWiremockTestHelper() {
        apiWiremock = new WireMockServer(options().port(8785)); // external app api mock server
        authWiremock = new WireMockServer(options().port(8786)); // external app auth mock server
    }

    public void start() {
        apiWiremock.start();
        authWiremock.start();
    }

    public void stop() {
        apiWiremock.stop();
        authWiremock.stop();
    }

    public void mockGetApplicationResponse(String externalId) {
        apiWiremock.stubFor(get("/application?id=" + externalId)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(getJsonNode(MOCK_GET_APP_RESPONSE))));
    }

    public void mockAuthResponse() {
        authWiremock.stubFor(post("/oauth2/token")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(getJsonNode(MOCK_AUTH_RESPONSE))));
    }

    public void verifyAuthResponse(int count) {
        authWiremock.verify(count, RequestPatternBuilder.newRequestPattern()
                .withUrl("/oauth2/token"));
    }

    public void verifyGetApplicationResponse(int count, String externalId) {
        apiWiremock.verify(count, RequestPatternBuilder.newRequestPattern()
                .withUrl("/application?id=" + externalId));
    }

    private static final String MOCK_GET_APP_RESPONSE = """
        {
          "name": "App 12345",
          "correlationId": "APM0012345",
          "itpmId": "12345",
          "id": "APM0012345",
          "acronym": "app1234",
          "active": true,
          "status": "Production",
          "technicalOwner": {
            "name": "Tech Manager"
          },
          "businessOwner": {
            "name": "Bus Manager"
          },
          "operationalOwner": {
            "name": "Ops Manager"
          },
          "svp": {
            "name": "Svp Manager"
          },
          "vp": {
            "name": "VP Manager"
          }
        }
        """;

    private static final String MOCK_AUTH_RESPONSE = """
            {
               "token_type":"Bearer",
               "expires_in":3599,
               "ext_expires_in":3599,
               "access_token":"accessTokenValue"
            }
            """;
}
