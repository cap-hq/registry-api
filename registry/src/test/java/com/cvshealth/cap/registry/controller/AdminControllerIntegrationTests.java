// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.ExternalAppApiWiremockTestHelper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;

public class AdminControllerIntegrationTests extends ControllerTests {

    private static final String EXTERNAL_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_EXTERNAL_ID = "00000000-0000-0000-0000-000000000002";

    protected WireMockServer serviceApiWiremock;

    @Autowired
    private RegistryService registryService;

    private ExternalAppApiWiremockTestHelper wiremockTestHelper;

    @BeforeEach
    void setUp() {
        setUpBeforeEachTest();
        serviceApiWiremock = new WireMockServer(options().port(8788)); // app reg auth api mock server
        serviceApiWiremock.start();
        wiremockTestHelper = new ExternalAppApiWiremockTestHelper();
        wiremockTestHelper.start();

        List<TeamEntity> teams = List.of(
                buildTeam("Team1", TeamType.PERSONAL),
                buildTeam("Team2", TeamType.SHARED)
        );
        teamRepository.saveAll(teams);
        Member member = registryService.getMemberByExternalId(EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(teams.get(0).getId()).memberId(member.getId()).build())
                .build());
    }

    @AfterEach
    protected void setUpAfterEachTest() {
        serviceApiWiremock.stop();
        wiremockTestHelper.stop();
    }

    @Test
    public void syncAzureUsers() {
        // then
        given()
                .header("X-External-User-Id", EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/admin/job/syncAzureUsers")
                .then()
                .statusCode(200);
    }

    @Test
    public void syncAzureUsers_Unauthorized() {
        // then/when
        given()
                .header("X-External-User-Id", OTHER_EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/admin/job/syncAzureUsers")
                .then()
                .statusCode(401);
    }

    @Test
    public void authProviderCredentialInfo() {
        given()
                .header("X-External-User-Id", EXTERNAL_ID)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/admin/authProvider/credentialInfo")
                .then()
                .statusCode(200);
    }

    @Test
    public void authProviderCredentialInfo_Unauthorized() {
        // then/when
        given()
                .header("X-External-User-Id", OTHER_EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/admin/authProvider/credentialInfo")
                .then()
                .statusCode(401);
    }

    @Test
    public void authClientExpirationCheck() {
        given()
                .header("X-External-User-Id", EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .accept(ContentType.TEXT)
                .when()
                .post("/api/v1/admin/job/authClientExpirationCheck")
                .then()
                .statusCode(200);
    }

    @Test
    public void authClientExpirationCheck_Unauthorized() {
        // then/when
        given()
                .header("X-External-User-Id", OTHER_EXTERNAL_ID)
                .contentType(ContentType.JSON)
                .accept(ContentType.TEXT)
                .when()
                .post("/api/v1/admin/job/authClientExpirationCheck")
                .then()
                .statusCode(401);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD) // Clear cached auth token
    public void getExternalApp() {
        // given
        String externalId = "APM0012345";
        wiremockTestHelper.mockAuthResponse();
        wiremockTestHelper.mockGetApplicationResponse(externalId);

        // external app admin returns success
        given()
                .header("X-External-User-Id", EXTERNAL_ID)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/admin/externalApp/" + externalId)
                .then()
                .statusCode(200);

        wiremockTestHelper.verifyAuthResponse(1);
        wiremockTestHelper.verifyGetApplicationResponse(1, externalId);
    }

    @Test
    public void getExternalApp_Unauthorized() {
        // then/when
        given()
                .header("X-External-User-Id", OTHER_EXTERNAL_ID)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/admin/externalApp/APM12345")
                .then()
                .statusCode(401);
    }
}
