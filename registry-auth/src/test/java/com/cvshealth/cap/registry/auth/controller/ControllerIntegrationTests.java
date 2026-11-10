// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.ClientRequest;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.service.ClientService;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ControllerIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    protected ClientService clientService;

    protected static Client initialClient;

    protected void setUpBeforeEachTest() {
        RestAssured.baseURI = "http://localhost:" + serverPort;

        if (clientService.getClients().stream().noneMatch(c -> c.getClientId().equals("test_client"))) {
            initialClient = clientService.createClient(ClientRequest.builder()
                    .clientId("test_client")
                    .type(ClientType.USER)
                    .accessTokenExpireMinutes(1L)
                    .refreshTokenExpireMinutes(5L)
                    .redirectUrls(List.of("http://localhost:1234/oauth-callback", "http://localhost:4321/foo-bar"))
                    .build());
        }
    }
}
