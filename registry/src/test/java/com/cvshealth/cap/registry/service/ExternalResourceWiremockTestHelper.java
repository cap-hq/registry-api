// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class ExternalResourceWiremockTestHelper {

    private final Map<String, WireMockServer> wireMockServers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTERNAL_RESOURCE1 = "resource1-graphql";
    private static final String EXTERNAL_RESOURCE2 = "resource2-graphql";
    private static final String EXTERNAL_RESOURCE3 = "resource3-graphql";
    private static final String EXTERNAL_RESOURCE4 = "resource4-graphql";
    private static final String EXTERNAL_RESOURCE5 = "resource5-graphql";

    public ExternalResourceWiremockTestHelper() {
        this.wireMockServers = new HashMap<>(Map.of(
                EXTERNAL_RESOURCE1, new WireMockServer(options().port(8788)),
                EXTERNAL_RESOURCE2, new WireMockServer(options().port(8790)),
                EXTERNAL_RESOURCE3, new WireMockServer(options().port(8791)),
                EXTERNAL_RESOURCE4, new WireMockServer(options().port(8792)),
                EXTERNAL_RESOURCE5, new WireMockServer(options().port(8793))
        ));
    }

    public void start() {
        for (WireMockServer wireMockServer : wireMockServers.values()) {
            wireMockServer.start();
        }
    }

    public void stop() {
        for (WireMockServer wireMockServer : wireMockServers.values()) {
            wireMockServer.stop();
        }
    }



    public void registerEmptyMocks(String applicationName) {
        registerCapResourceMock(EXTERNAL_RESOURCE1, Collections.emptyList());
        registerCapResourceMock(EXTERNAL_RESOURCE2, Collections.emptyList());
        registerCapResourceRestMock(EXTERNAL_RESOURCE3, applicationName, Collections.emptyList());
        registerCapResourceMock(EXTERNAL_RESOURCE4, Collections.emptyList());
        registerCapResourceMock(EXTERNAL_RESOURCE5, Collections.emptyList());
    }

    public void registerMocks(String applicationName, List<CapResource> external1Resources,
                              List<CapResource> external2Resources, List<CapResource> external3Resources,
                              List<CapResource> external4Resources, List<CapResource> external5Resources) {
        registerCapResourceMock(EXTERNAL_RESOURCE1, external1Resources);
        registerCapResourceMock(EXTERNAL_RESOURCE2, external2Resources);
        registerCapResourceRestMock(EXTERNAL_RESOURCE3, applicationName, external3Resources);
        registerCapResourceMock(EXTERNAL_RESOURCE4, external4Resources);
        registerCapResourceMock(EXTERNAL_RESOURCE5, external5Resources);
    }

    public void registerCapResourceMock(String wireMockName, List<CapResource> resources) {
        wireMockServers.get(wireMockName).stubFor(post("/graphql")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockCapResources(resources))));
    }

    public void registerCapResourceRestMock(String wireMockName, String applicationName, List<CapResource> resources) {
        wireMockServers.get(wireMockName).stubFor(get(String.format("/api/getCapResources/%s", applicationName))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockRestResources(resources))));
    }

    private JsonNode mockCapResources(List<CapResource> resources) {
        String json = """
                {
                	"data": {
                		"getCapResources": %s
                	}
                }
                """;
        return toJsonNode(json, resources);
    }

    private JsonNode mockRestResources(List<CapResource> resources) {
        String json = "%s";
        return toJsonNode(json, resources);
    }

    private JsonNode toJsonNode(String json, Object model) {
        try {
            if (model != null) {
                json = String.format(json, objectMapper.writeValueAsString(model));
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            //ignore
        }
        return null;
    }
}
