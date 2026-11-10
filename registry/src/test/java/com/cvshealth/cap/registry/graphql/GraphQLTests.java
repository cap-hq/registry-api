// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.repository.*;
import com.cvshealth.cap.registry.security.AuthenticationToken;
import com.cvshealth.cap.registry.service.RegistryService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import static io.restassured.RestAssured.given;

public class GraphQLTests extends RegistryIntegrationTests {
    @Autowired
    protected ApplicationRepository applicationRepository;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected TeamRepository teamRepository;
    @Autowired
    protected TeamMemberRepository teamMemberRepository;
    @Autowired
    protected ApplicationTransferRepository applicationTransferRepository;
    @Autowired
    protected AuthClientRepository authClientRepository;
    @Autowired
    protected TeamAuthClientRepository teamAuthClientRepository;
    @Autowired
    protected ChangeEventRepository changeEventRepository;
    @Autowired
    protected RegistryService registryService;

    protected static final String GRAPHQL_PATH = "/api/graphql";

    // You can find AD users from Azure Portal https://portal.azure.com/#view/Microsoft_AAD_UsersAndTenants/UserManagementMenuBlade/~/AllUsers
    protected static final String MEMBER1_EXTERNAL_ID = "00000000-0000-0000-0000-000000000001"; // "Tester, One" user id
    protected static final String MEMBER1_EXTERNAL_ALIAS = "onetester"; // "Tester, One" mail nickname
    protected static final String MEMBER1_TEAM_NAME = "Personal-OneTester";
    protected static final String MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME = "Personal-OneTester".toLowerCase().replaceAll("[^a-z0-9]", "");

    protected static final String MEMBER2_EXTERNAL_ID = "00000000-0000-0000-0000-000000000002"; // "Tester, Two" user id
    protected static final String MEMBER2_EXTERNAL_ALIAS = "twotester"; // "Tester, Two" mail nickname
    protected static final String MEMBER2_NORMALIZED_PERSONAL_TEAM_NAME = "Personal-TwoTester".toLowerCase().replaceAll("[^a-z0-9]", "");

    protected Member member1;
    protected Member member2;

    protected void setUpBeforeEachTest() {
        RestAssured.baseURI = "http://localhost:" + serverPort;

        SecurityContextHolder.getContext().setAuthentication(new AuthenticationToken("integration-test"));
        member1 = registryService.getMemberByExternalId(MEMBER1_EXTERNAL_ID); // create member & personal team
        member2 = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID); // create member & personal team
    }

    protected void setUpAfterEachTest() {
        applicationTransferRepository.deleteAll();
        teamMemberRepository.deleteAll();
        memberRepository.deleteAll();
        applicationRepository.deleteAll();
        teamAuthClientRepository.deleteAll();
        authClientRepository.deleteAll();
        teamRepository.deleteAll();
        changeEventRepository.deleteAll();
    }

    ValidatableResponse givenGraphQL(GraphQLQuery query, String externalIdHeader) {
        ValidatableResponse response = given()
                .header("X-External-User-Id", externalIdHeader)
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(GRAPHQL_PATH)
                .then()
                .statusCode(200)
                .and();
        // log graphql response - useful for debugging
        System.out.println(response.extract().as(Object.class));
        return response;
    }

    ValidatableResponse givenGraphQLNonUserClient(GraphQLQuery query, String clientId) {
        ValidatableResponse response = given()
                .header("X-Auth-Client-Id", clientId)
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(GRAPHQL_PATH)
                .then()
                .statusCode(200)
                .and();
        // log graphql response - useful for debugging
        System.out.println(response.extract().as(Object.class));
        return response;
    }

    ValidatableResponse givenGraphQLNoAuthThen401(GraphQLQuery query) {
        // no auth header is provided
        return given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(GRAPHQL_PATH)
                .then()
                .statusCode(401);
    }

}
