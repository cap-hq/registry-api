// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.config.DownstreamApplicationRestClientConfig;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.RollupApiWiremockTestHelper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.*;

@ActiveProfiles({"test", "teamRollup"})
public class TeamResolverRollupIntegrationTests extends GraphQLTests {

    private WireMockServer downstreamAppWiremock;
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;
    private RollupApiWiremockTestHelper rollupApiWiremockTestHelper;

    @MockitoBean
    private DownstreamApplicationRestClientConfig downstreamApplicationRestClientConfig;

    @Value("${app.max-allowed-team-memberships}")
    int maxAllowedTeamMemberships;

    @Mock
    protected List<LabeledRestClient> downstreamApplicationRestClients;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        downstreamAppWiremock = new WireMockServer(options().port(7777));
        downstreamAppWiremock.start();

        downstreamApplicationRestClients = new ArrayList<>();
        downstreamApplicationRestClients.add(new LabeledRestClient(RestClient.builder().build(), "App1 Name"));

        authApiWiremockTestHelper = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApiWiremockTestHelper.start();

        rollupApiWiremockTestHelper = new RollupApiWiremockTestHelper(8789);
        rollupApiWiremockTestHelper.start();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        downstreamAppWiremock.stop();
        authApiWiremockTestHelper.stop();
        if (rollupApiWiremockTestHelper != null) {
            rollupApiWiremockTestHelper.stop();
        }
    }

    @Test
    public void getTeams() {
        String teamName = "ATeam1";
        List<TeamEntity> teams = List.of(
                TeamEntity.builder().name(teamName).nameNormalized("ateam1").type(TeamType.SHARED).build()
        );
        teamRepository.saveAll(teams);
        GraphQLQuery query = new GraphQLQuery("query {getTeams (paginationInput: {pageSize: 10, pageNumber: 1} " +
                "filter: { type: SHARED }) " +
                "{teams{id name type description teamRollup { managers {lastName}} } totalPageCount totalRecordCount}}");

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeams.teams", hasSize(1))
                .body("data.getTeams.totalPageCount", is(1))
                .body("data.getTeams.totalRecordCount", is(1))
                .body("data.getTeams.teams.find {it.name == 'ATeam1'}.type", is(TeamType.SHARED.name()))
                .body("data.getTeams.teams.find {it.name == 'ATeam1'}.teamRollup.managers[0].lastName", is("Manager"));
    }

    @Test
    public void getTeam() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).build();
        entity = teamRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeam(id: \"%s\") {id name type}}", entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeam.id", is(entity.getId().intValue()));
    }

    @Test
    public void getTeamByName() {
        String teamName = "Team1";
        TeamEntity entity = TeamEntity.builder().name(teamName).nameNormalized("team1").type(TeamType.PERSONAL).description("the team").emailList("email@email.com").build();
        entity = teamRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeamByName(name: \"%s\") {id name type description emailList teamRollup { managers {lastName}} }}", entity.getName()));

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeamByName.id", is(entity.getId().intValue()))
                .body("data.getTeamByName.description", is("the team"))
                .body("data.getTeamByName.emailList", is(entity.getEmailList()))
                .body("data.getTeamByName.teamRollup.managers[0].lastName", is("Manager"));

    }
}
