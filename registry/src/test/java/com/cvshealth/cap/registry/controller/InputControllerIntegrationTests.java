// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import com.cvshealth.cap.registry.model.FieldConstraints;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class InputControllerIntegrationTests extends ControllerTests {

    private static final String EXTERNAL_ID = "00000000-0000-0000-0000-000000000001";
    private static final FieldConstraints APPLICATION_NAME = ValidationConstraintsFixture.applicationNameConstraints();
    private static final FieldConstraints APPLICATION_DESCRIPTION =
            ValidationConstraintsFixture.applicationDescriptionConstraints();
    private static final FieldConstraints TEAM_NAME = ValidationConstraintsFixture.teamNameConstraints();
    protected WireMockServer serviceApiWiremock;

    @BeforeEach
    void setUp() {
        setUpBeforeEachTest();
        serviceApiWiremock = new WireMockServer(options().port(8788)); // app reg auth api mock server
        serviceApiWiremock.start();
    }

    @AfterEach
    protected void setUpAfterEachTest() {
        serviceApiWiremock.stop();
    }

    @Test
    public void whenGetInputs_thenReturnSuccess() {
        given()
                .header("X-External-User-Id", EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/inputs")
                .then()
                .statusCode(200)
                .body("complianceTypes", hasSize(4))
                .body("legacyComplianceTypesEnabled", equalTo(true))
                .body("informationClassifications.name",
                        equalTo(java.util.List.of("RESTRICTED", "CONFIDENTIAL", "PROPRIETARY")))
                .body("informationClassifications.label",
                        equalTo(java.util.List.of("Restricted", "Confidential", "Proprietary")))
                .body("dataPerimeters.name", equalTo(java.util.List.of("RESTRICTED", "UNRESTRICTED")))
                .body("dataPerimeters.label", equalTo(java.util.List.of("Restricted", "Unrestricted")))
                .body("applicationPriorities", hasSize(5))
                .body("slackBotApp", equalTo("Registry-TEST"))
                .body("clouds", hasSize(2))
                // read from the fixture rather than literals - ValidationConstraintsConfigTest asserts the
                // fixture matches application.yaml, so changing a limit cannot leave this asserting the old one
                .body("validation.application.name.regex", equalTo(APPLICATION_NAME.getRegex()))
                .body("validation.application.name.minLength", equalTo(APPLICATION_NAME.getMinLength()))
                .body("validation.application.name.maxLength", equalTo(APPLICATION_NAME.getMaxLength()))
                .body("validation.application.description.maxLength", equalTo(APPLICATION_DESCRIPTION.getMaxLength()))
                .body("validation.team.name.regex", equalTo(TEAM_NAME.getRegex()))
                .body("validation.team.name.maxLength", equalTo(TEAM_NAME.getMaxLength()))
                .body("validation.team.name.normalizationRegex", equalTo(TEAM_NAME.getNormalizationRegex()))
                .body("validation.team.name.formatExemptPrefix", equalTo(TEAM_NAME.getFormatExemptPrefix()));
    }

    @Test
    public void given_NoAuth_whenGetComplianceTypes_thenReturnUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/inputs")
                .then()
                .statusCode(401);
    }
}
