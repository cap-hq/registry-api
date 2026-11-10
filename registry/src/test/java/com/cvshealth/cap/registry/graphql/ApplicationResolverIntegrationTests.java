// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.ExternalResourceWiremockTestHelper;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class ApplicationResolverIntegrationTests extends GraphQLTests {
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;
    private ExternalResourceWiremockTestHelper externalResourceWiremockTestHelper;

    private static final List<String> COMPLIANCE_TYPES_pci = List.of("pci");
    private static final List<String> COMPLIANCE_TYPES_pii = List.of("pii");
    private static final List<String> COMPLIANCE_TYPES_pii_pci = List.of("pii", "pci");

    private static final Integer PRIORITY_1 = 1;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        externalResourceWiremockTestHelper = new ExternalResourceWiremockTestHelper();
        externalResourceWiremockTestHelper.start();

        authApiWiremockTestHelper = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApiWiremockTestHelper.start();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        externalResourceWiremockTestHelper.stop();
        authApiWiremockTestHelper.stop();
    }

    @Test
    public void getApplications() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.PERSONAL).build();
        teamRepository.save(team);
        List<ApplicationEntity> apps = List.of(
                ApplicationEntity.builder().name("app7").teamId(team.getId()).build(),
                ApplicationEntity.builder().name("app6").teamId(team.getId()).build(),
                ApplicationEntity.builder().name("app5").teamId(team.getId()).complianceTypes(COMPLIANCE_TYPES_pci).build(),
                ApplicationEntity.builder().name("app4").teamId(team.getId()).build(),
                ApplicationEntity.builder().name("app3").teamId(team.getId()).complianceTypes(COMPLIANCE_TYPES_pii_pci).build(),
                ApplicationEntity.builder().name("app2").teamId(team.getId()).build(),
                ApplicationEntity.builder().name("App1").description("the app").priority(PRIORITY_1).teamId(team.getId()).build()
        );
        applicationRepository.saveAll(apps);
        GraphQLQuery query = new GraphQLQuery("query {getApplications(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {names: [\"app1\", \"app3\", \"app5\", \"app7\"]}) " +
                "{applications {id name description complianceTypes priority} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplications.applications", hasSize(3))
                .body("data.getApplications.totalPageCount", is(2))
                .body("data.getApplications.totalRecordCount", is(4))
                .body("data.getApplications.applications[0].name", is("App1")) // sorted 1st
                .body("data.getApplications.applications[0].description", is("the app"))
                .body("data.getApplications.applications[0].priority", is(PRIORITY_1))
                .body("data.getApplications.applications[0].complianceTypes", nullValue())
                .body("data.getApplications.applications[1].name", is("app3")) // sorted 2nd
                .body("data.getApplications.applications[1].complianceTypes", hasSize(2)) // sorted 2nd
                .body("data.getApplications.applications[2].name", is("app5"))// sorted 3rd
                .body("data.getApplications.applications[2].complianceTypes", hasSize(1))
                .body("data.getApplications.applications[2].complianceTypes[0]", is("pci"));// sorted 3rd
    }

    @Test
    public void getApplications_partialNameMatch() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.PERSONAL).build();
        teamRepository.save(team);
        List<ApplicationEntity> apps = List.of(
                ApplicationEntity.builder().name("app10").teamId(team.getId()).complianceTypes(COMPLIANCE_TYPES_pii_pci).build(),
                ApplicationEntity.builder().name("app2").teamId(team.getId()).build(),
                ApplicationEntity.builder().name("App1").description("the app").priority(PRIORITY_1).teamId(team.getId()).build()
        );
        applicationRepository.saveAll(apps);
        GraphQLQuery query = new GraphQLQuery("query {getApplications(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {name: \"app1\" partialNameMatch: true}) " +
                "{applications {id name} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplications.applications", hasSize(2))
                .body("data.getApplications.totalPageCount", is(1))
                .body("data.getApplications.totalRecordCount", is(2))
                .body("data.getApplications.applications[0].name", is("App1"))
                .body("data.getApplications.applications[1].name", is("app10"));
    }

    private void saveCloudTenantApps() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.PERSONAL).build();
        teamRepository.save(team);
        applicationRepository.saveAll(List.of(
                ApplicationEntity.builder().name("app1").teamId(team.getId()).cloud("gcp").tenant("general").build(),
                ApplicationEntity.builder().name("app2").teamId(team.getId()).cloud("azurehcd").tenant("general").build(),
                ApplicationEntity.builder().name("app3").teamId(team.getId()).cloud("azurehcd").tenant("other").build(),
                ApplicationEntity.builder().name("app4").teamId(team.getId()).build()
        ));
    }

    private GraphQLQuery cloudTenantQuery(String filter) {
        return new GraphQLQuery("query {getApplications(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                (filter == null ? "" : "filter: {" + filter + "}") +
                ") {applications {name cloud tenant} totalRecordCount}}");
    }

    @Test
    public void getApplications_cloudAndTenant() {
        saveCloudTenantApps();
        givenGraphQL(cloudTenantQuery("clouds: [\"azurehcd\"] tenants: [\"general\"]"), MEMBER1_EXTERNAL_ID)
                .body("data.getApplications.totalRecordCount", is(1))
                .body("data.getApplications.applications[0].name", is("app2"))
                .body("data.getApplications.applications[0].cloud", is("azurehcd"))
                .body("data.getApplications.applications[0].tenant", is("general"));
    }

    @Test
    public void getApplications_cloudAndTenantCaseInsensitive() {
        saveCloudTenantApps();
        givenGraphQL(cloudTenantQuery("clouds: [\"AzureHCD\"] tenants: [\"General\"]"), MEMBER1_EXTERNAL_ID)
                .body("data.getApplications.totalRecordCount", is(1))
                .body("data.getApplications.applications[0].name", is("app2"));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void getApplicationsComposite() {
        String graphQL = """
                query {getApplications(paginationInput: {pageSize: 100, pageNumber: 1}) {
                    applications {
                        id name description team {
                            name type description members {
                                name lastName firstName }}}
                    totalPageCount totalRecordCount }}
                """;
        GraphQLQuery query = new GraphQLQuery(graphQL);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplications.applications", hasSize(11))
                .body("data.getApplications.totalPageCount", is(1))
                .body("data.getApplications.totalRecordCount", is(11))
                .body("data.getApplications.applications[0].name", is("app1")) // sorted 1st
                .body("data.getApplications.applications[0].description", is("application #1"))
                .body("data.getApplications.applications[0].team.members", hasSize(1));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void getApplicationByNameComposite() {
        String graphQL = """
                query {
                    getApplicationByName(name: "app11") {
                        name
                        team {
                            name
                            externalName
                            authClients {
                                clientId
                                clientVersion
                                clientSecretExpiresAt
                                lastAccessTokenIssuedAt
                                redirectUrls
                            }
                        }
                    }
                }
                """;
        GraphQLQuery query = new GraphQLQuery(graphQL);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationByName.name", is("app11"))
                .body("data.getApplicationByName.team.name", is("Team-5"))
                .body("data.getApplicationByName.team.externalName", is("AppReg_Team-5"))
                .body("data.getApplicationByName.team.authClients", hasSize(2))
                .body("data.getApplicationByName.team.authClients[0].clientId", is("client-credentials-flow"))
                .body("data.getApplicationByName.team.authClients[0].clientVersion", is(1))
                .body("data.getApplicationByName.team.authClients[0].clientSecretExpiresAt", is(notNullValue()))
                .body("data.getApplicationByName.team.authClients[0].redirectUrls[0]", is("http://localhost:8080/callback"))
                .body("data.getApplicationByName.team.authClients[0].lastAccessTokenIssuedAt", is(notNullValue()))
                .body("data.getApplicationByName.team.authClients[1].clientId", is("client-credentials-flow"))
                .body("data.getApplicationByName.team.authClients[1].clientVersion", is(2))
                .body("data.getApplicationByName.team.authClients[1].clientSecretExpiresAt", is(notNullValue()))
                .body("data.getApplicationByName.team.authClients[1].redirectUrls[0]", is("http://localhost:8080/callback2"))
                .body("data.getApplicationByName.team.authClients[1].lastAccessTokenIssuedAt", is(notNullValue()));
    }

    @Test
    public void getApplicationsNoAuth() {
        GraphQLQuery query = new GraphQLQuery("query {getApplications {id name}}");

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getApplication() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("app1").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplication(id: \"%s\") {id name}}", entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplication.id", is(entity.getId().intValue()));
    }

    @Test
    public void getApplicationNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplication(id: \"%s\") {id name}}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getApplicationNotFound() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplication(id: \"%s\") {id name}}", new Random().nextLong()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void getApplicationByName() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("app1").description("the app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationByName(name: \"%s\") {id name description}}", entity.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationByName.id", is(entity.getId().intValue()))
                .body("data.getApplicationByName.description", is("the app"));
    }

    @Test
    public void getApplicationByName_CaseInsensitive() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("App1").description("the app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationByName(name: \"%s\") {id name description}}", "app1"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationByName.id", is(entity.getId().intValue()))
                .body("data.getApplicationByName.name", is(entity.getName()))
                .body("data.getApplicationByName.description", is("the app"));
    }

    @Test
    public void getApplicationsByExternalId() {
        String externalId = "external-Id";
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("app1").description("the app").externalId(externalId).teamId(team.getId()).build();
        applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationsByExternalId(externalId: \"%s\") {id name externalId}}", externalId));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationsByExternalId", hasSize(1))
                .body("data.getApplicationsByExternalId[0].name", is("app1"))
                .body("data.getApplicationsByExternalId[0].externalId", is(externalId));
    }

    @Test
    public void createApplication() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", description: \"the app\", complianceTypes: [\"pii\"], priority: 1, aiEnabled: false}) {id name description complianceTypes priority createUser}}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createApplication.name", is("newapp"))
                .body("data.createApplication.id", is(not(emptyString())))
                .body("data.createApplication.description", is("the app"))
                .body("data.createApplication.complianceTypes[0]", is("pii"))
                .body("data.createApplication.priority", is(PRIORITY_1))
                .body("data.createApplication.createUser", is(MEMBER1_EXTERNAL_ALIAS));
    }

    @Test
    public void createApplicationDefaultsDataPerimeterToUnrestricted() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", aiEnabled: false}) {id name dataPerimeter}}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createApplication.name", is("newapp"))
                .body("data.createApplication.dataPerimeter", is(DataPerimeter.UNRESTRICTED.toString()));
    }

    @Test
    public void createApplicationNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamId: \"%s\", aiEnabled: false}) {id name}}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createApplicationNotTeamMember() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", aiEnabled: false}) {id name}}", team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createApplicationTeamMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", aiEnabled: false}) {id name createUser}}", team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.createApplication.name", is("newapp"))
                .body("data.createApplication.id", is(not(emptyString())))
                .body("data.createApplication.createUser", is(MEMBER2_EXTERNAL_ALIAS));
    }

    @Test
    public void createApplicationNotTeamOwnerOrMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.VIEWER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", aiEnabled: false}) {id name}}", team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createApplicationComplianceTypeNotOwner() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", complianceTypes: [\"pci\"], aiEnabled: false}) {id name complianceTypes}}", team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createApplicationPriorityNotOwner() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"newapp\", teamName: \"%s\", priority: 1, aiEnabled: false}) {id name priority}}", team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createApplicationMissingName() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {teamName: \"%s\", aiEnabled: false}) {id name}}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.classification", hasItem("ValidationError"));
    }

    @Test
    public void createApplicationNameTooShort() {
        int minLength = ValidationConstraintsFixture.applicationNameConstraints().getMinLength();
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        String name = "x".repeat(minLength - 2); // 1 char — below minimum
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createApplication(application: {name: \"%s\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                name, team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createApplicationNameAtMinLength() {
        int minLength = ValidationConstraintsFixture.applicationNameConstraints().getMinLength();
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        String name = "x".repeat(minLength);
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createApplication(application: {name: \"%s\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                name, team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createApplication.name", equalTo(name));
    }

    @Test
    public void createApplicationNameTooLong() {
        int maxLength = ValidationConstraintsFixture.applicationNameConstraints().getMaxLength();
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        String name = "x".repeat(maxLength + 1);
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createApplication(application: {name: \"%s\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                name, team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_NAME_INVALID_LENGTH.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createApplicationNameAtMaxLength() {
        int maxLength = ValidationConstraintsFixture.applicationNameConstraints().getMaxLength();
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        String name = "x".repeat(maxLength);
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createApplication(application: {name: \"%s\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                name, team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createApplication.name", equalTo(name));
    }

    @Test
    public void createApplicationNameInvalidFormat() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createApplication(application: {name: \"Abc\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_NAME_INVALID_FORMAT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createApplicationDuplicateName() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        applicationRepository.save(ApplicationEntity.builder().name("app1").teamId(team.getId()).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createApplication(application: {name: \"app1\", teamName: \"%s\", aiEnabled: false}) {id name}}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_DUPLICATE_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void updateApplicationTeamTransferRequired() {
        TeamEntity team1 = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        TeamEntity team2 = teamRepository.findAllByNameNormalized(MEMBER2_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team1.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                team2.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TEAM_TRANSFER_REQUIRED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void updateApplicationNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"updatedapp\", teamId: \"%s\", aiEnabled: false}) {id name}}",
                new Random().nextLong(),
                new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void updateApplicationNotTeamMember() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateApplicationTeamMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"APP\", teamName: \"%s\", description: \"the app\", aiEnabled: false}) {id name description}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.updateApplication.name", is("app"))
                .body("data.updateApplication.id", is(entity.getId().intValue()))
                .body("data.updateApplication.description", is("the app"));
    }

    @Test
    public void updateApplicationComplianceTypeOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).complianceTypes(COMPLIANCE_TYPES_pii).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", description: \"the app\", aiEnabled: false}) {id name description complianceTypes}}",
                team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateApplication.name", is("app"))
                .body("data.updateApplication.id", is(entity.getId().intValue()))
                .body("data.updateApplication.description", is("the app"))
                .body("data.updateApplication.complianceTypes", nullValue());
    }

    @Test
    public void updateApplicationComplianceTypeNotOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).complianceTypes(COMPLIANCE_TYPES_pii).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", description: \"the app\", aiEnabled: false}) {id name description complianceTypes}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateApplicationPriorityOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).priority(PRIORITY_1).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", description: \"the app\", aiEnabled: false}) {id name description priority}}",
                team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateApplication.name", is("app"))
                .body("data.updateApplication.id", is(entity.getId().intValue()))
                .body("data.updateApplication.description", is("the app"))
                .body("data.updateApplication.priority", nullValue());
    }

    @Test
    public void updateApplicationPriorityNotOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).priority(PRIORITY_1).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", description: \"the app\", aiEnabled: false}) {id name description priority}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateApplicationAiEnabledOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).aiEnabled(false).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", aiEnabled: true}) {id name aiEnabled}}",
                team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateApplication.id", notNullValue())
                .body("data.updateApplication.name", equalTo("app"))
                .body("data.updateApplication.aiEnabled", equalTo(true));
    }

    @Test
    public void updateApplicationAiEnabledNotOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).aiEnabled(false).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", aiEnabled: true}) {id name aiEnabled}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateApplicationAiEnabledNoChange() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).aiEnabled(false).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", aiEnabled: false}) {id name aiEnabled}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.updateApplication.id", notNullValue())
                .body("data.updateApplication.name", equalTo("app"))
                .body("data.updateApplication.aiEnabled", equalTo(false));
    }

    @Test
    public void updateApplicationDataPerimeterOwner() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).dataPerimeter(DataPerimeter.UNRESTRICTED).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        externalResourceWiremockTestHelper.registerEmptyMocks(entity.getName());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", dataPerimeter: RESTRICTED}) {id name dataPerimeter}}",
                team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateApplication.id", notNullValue())
                .body("data.updateApplication.name", equalTo("app"))
                .body("data.updateApplication.dataPerimeter", equalTo(DataPerimeter.RESTRICTED.toString()));
    }

    @Test
    public void updateApplicationDataPerimeterMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).dataPerimeter(DataPerimeter.UNRESTRICTED).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        externalResourceWiremockTestHelper.registerEmptyMocks(entity.getName());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", dataPerimeter: RESTRICTED}) {id name dataPerimeter}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.updateApplication.id", notNullValue())
                .body("data.updateApplication.name", equalTo("app"))
                .body("data.updateApplication.dataPerimeter", equalTo(DataPerimeter.RESTRICTED.toString()));
    }

    @Test
    public void updateApplicationNotTeamOwnerOrMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.VIEWER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"app\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateApplicationNotFound() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateApplication(application: {name: \"notfound\", teamName: \"%s\", aiEnabled: false}) {id name}}",
                team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    private ApplicationEntity buildApplicationForDeletion() {
        var team1 = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        var teamId1 = teamRepository.save(team1).getId();
        var team2 = TeamEntity.builder().name("team-2").nameNormalized("team2").type(TeamType.SHARED).build();
        var teamId2 = teamRepository.save(team2).getId();
        var app1 = ApplicationEntity.builder().name("app1").teamId(teamId2).build();
        var appId1 = applicationRepository.save(app1).getId();
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team1.getId()).memberId(member1.getId()).build())
                .build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team2.getId()).memberId(member1.getId()).build())
                .build());
        applicationTransferRepository.save(ApplicationTransferEntity.builder()
                .applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.COMPLETED).build());
        return app1;
    }

    @Test
    public void deleteApplication() {
        var app1 = buildApplicationForDeletion();

        externalResourceWiremockTestHelper.registerEmptyMocks(app1.getName());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", app1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteApplication", is(true));
    }

    @Test
    public void deleteApplicationWithExternalServiceConstraint_graphQL_externalResources1() {
        var app1 = buildApplicationForDeletion();

        List<CapResource> capResources = List.of(CapResource.builder().type("Service").name("resource1A").environment(CapResourceEnvironment.DEV).build());
        externalResourceWiremockTestHelper.registerMocks(app1.getName(), capResources, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", app1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteApplicationWithExternalServiceConstraint_graphQL_externalResources2() {
        var app1 = buildApplicationForDeletion();

        List<CapResource> capResources = List.of(CapResource.builder().type("Bucket").name("resource2A").environment(CapResourceEnvironment.DEV).build());
        externalResourceWiremockTestHelper.registerMocks(app1.getName(), Collections.emptyList(), capResources,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", app1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteApplicationWithExternalServiceConstraint_rest_externalResources() {
        var app1 = buildApplicationForDeletion();
        List<CapResource> capResources = List.of(CapResource.builder().type("Database").name("resource3A").environment(CapResourceEnvironment.PROD).build());
        externalResourceWiremockTestHelper.registerMocks(app1.getName(), Collections.emptyList(), Collections.emptyList(),
                capResources, Collections.emptyList(), Collections.emptyList());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", app1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteApplicationWithExternalServiceConstraint_graphQL_topicResources() {
        var app1 = buildApplicationForDeletion();
        List<CapResource> capResources = List.of(CapResource.builder().type("Topic").name("topic1A").environment(CapResourceEnvironment.DEV).build());
        externalResourceWiremockTestHelper.registerMocks(app1.getName(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), capResources, Collections.emptyList());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", app1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteApplicationWithExternalServiceConstraint_graphQL_cacheResources() {
        var app1 = buildApplicationForDeletion();
        List<CapResource> capResources = List.of(CapResource.builder().type("Cache").name("cache1A").environment(CapResourceEnvironment.DEV).build());
        externalResourceWiremockTestHelper.registerMocks(app1.getName(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), capResources);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", app1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteApplicationNotFound() {
        GraphQLQuery query = new GraphQLQuery("mutation {deleteApplication(name: \"not-a-real-team\")}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void deleteApplicationNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(id: \"%s\")}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void deleteApplicationNotTeamMember() {
        TeamEntity team = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", entity.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void deleteApplicationTeamMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());

        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());

        externalResourceWiremockTestHelper.registerEmptyMocks(entity.getName());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", entity.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.deleteApplication", is(true));
    }

    @Test
    public void deleteApplicationNotTeamOwnerOrMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        ApplicationEntity entity = ApplicationEntity.builder().name("app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);

        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());

        Member otherMember = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.VIEWER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(otherMember.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteApplication(name: \"%s\")}", entity.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }
}
