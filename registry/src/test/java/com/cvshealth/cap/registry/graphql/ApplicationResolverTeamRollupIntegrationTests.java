// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.ExternalResourceWiremockTestHelper;
import com.cvshealth.cap.registry.service.RollupApiWiremockTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static org.hamcrest.Matchers.*;

@ActiveProfiles({"test", "teamRollup"})
public class ApplicationResolverTeamRollupIntegrationTests extends GraphQLTests {
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;
    private ExternalResourceWiremockTestHelper externalResourceWiremockTestHelper;
    private RollupApiWiremockTestHelper rollupApiWiremockTestHelper;

    private static final Integer PRIORITY_1 = 1;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        externalResourceWiremockTestHelper = new ExternalResourceWiremockTestHelper();
        externalResourceWiremockTestHelper.start();

        authApiWiremockTestHelper = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApiWiremockTestHelper.start();

        rollupApiWiremockTestHelper = new RollupApiWiremockTestHelper(8789);
        rollupApiWiremockTestHelper.start();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        externalResourceWiremockTestHelper.stop();
        authApiWiremockTestHelper.stop();
        if (rollupApiWiremockTestHelper != null) {
            rollupApiWiremockTestHelper.stop();
        }
    }

    @Test
    public void getApplications() {
        String teamName = "team-1";
        TeamEntity team = teamRepository.save(TeamEntity.builder().name(teamName).nameNormalized("team1").type(TeamType.SHARED).build());
        List<ApplicationEntity> apps = List.of(
                ApplicationEntity.builder().name("app1").teamId(team.getId()).build(),
                ApplicationEntity.builder().name("app2").teamId(team.getId()).build()
        );
        applicationRepository.saveAll(apps);
        GraphQLQuery query = new GraphQLQuery("query {getApplications(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "filter: {names: [\"app1\", \"app2\"]}) " +
                "{applications {id name description complianceTypes priority team{name teamRollup { managers {lastName}} }} totalPageCount totalRecordCount}}");

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplications.applications", hasSize(2))
                .body("data.getApplications.totalPageCount", is(1))
                .body("data.getApplications.totalRecordCount", is(2))
                .body("data.getApplications.applications[0].name", is("app1")) // sorted 1st
                .body("data.getApplications.applications[1].name", is("app2")) // sorted 2nd
                .body("data.getApplications.applications[0].team.teamRollup.managers[0].lastName", is("Manager"));
    }

    @Test
    public void getApplicationByName() {
        String teamName = "team-1";
        TeamEntity team = teamRepository.save(TeamEntity.builder().name(teamName).nameNormalized("team1").type(TeamType.SHARED).build());
        ApplicationEntity entity = ApplicationEntity.builder().name("app1").description("the app").teamId(team.getId()).build();
        entity = applicationRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationByName(name: \"%s\") {id name description team{name teamRollup { managers {lastName}} } }}", entity.getName()));

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationByName.id", is(entity.getId().intValue()))
                .body("data.getApplicationByName.description", is("the app"))
                .body("data.getApplicationByName.team.teamRollup.managers[0].lastName", is("Manager"));
    }
}
