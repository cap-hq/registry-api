// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.controller;

import com.cvshealth.cap.registry.rollup.RegistryIntegrationTests;
import com.cvshealth.cap.registry.rollup.security.AuthenticationToken;
import io.restassured.RestAssured;
import org.springframework.security.core.context.SecurityContextHolder;

public class ControllerTests extends RegistryIntegrationTests {
    protected void setUpBeforeEachTest() {
        RestAssured.baseURI = "http://localhost:" + serverPort;

        SecurityContextHolder.getContext().setAuthentication(new AuthenticationToken("integration-test"));
    }
}
