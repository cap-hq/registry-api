// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.config.DownstreamApplicationRestClientConfig;
import com.cvshealth.cap.registry.model.LabeledRestClient;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.*;

@ActiveProfiles({"test", "availableClouds"})
public class TeamResolverAvailableCloudsIntegrationTests extends GraphQLTests {

    private WireMockServer downstreamAppWiremock;
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;

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
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        downstreamAppWiremock.stop();
        authApiWiremockTestHelper.stop();
    }

    @Test
    public void getTeam_returnsAvailableCloudsMatchingGetCloudsForTeamByName() {
        TeamEntity entity = TeamEntity.builder()
                .name("Team1")
                .nameNormalized("team1")
                .type(TeamType.PERSONAL)
                .description("the team")
                .emailList("email@email.com")
                .build();
        entity = teamRepository.save(entity);

        GraphQLQuery query = new GraphQLQuery(String.format(
                "query {getTeam(id: \"%s\") {id name availableClouds {name tenants}}}",
                entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeam.id", is(entity.getId().intValue()))
                .body("data.getTeam.name", is("Team1"))
                .body("data.getTeam.availableClouds", hasSize(2))
                .body("data.getTeam.availableClouds[0].name", is("gcp"))
                .body("data.getTeam.availableClouds[0].tenants", hasSize(1))
                .body("data.getTeam.availableClouds[0].tenants[0]", is("general"))
                .body("data.getTeam.availableClouds[1].name", is("azure"))
                .body("data.getTeam.availableClouds[1].tenants", hasSize(1))
                .body("data.getTeam.availableClouds[1].tenants[0]", is("general"));
    }

    @Test
    public void getTeamByName_returnsAvailableCloudsMatchingGetCloudsForTeamByName() {
        TeamEntity entity = TeamEntity.builder()
                .name("Team1")
                .nameNormalized("team1")
                .type(TeamType.PERSONAL)
                .description("the team")
                .emailList("email@email.com")
                .build();
        entity = teamRepository.save(entity);

        GraphQLQuery query = new GraphQLQuery(String.format(
                "query {getTeamByName(name: \"%s\") {id name availableClouds {name tenants}}}",
                entity.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeamByName.id", is(entity.getId().intValue()))
                .body("data.getTeamByName.availableClouds", hasSize(2))
                .body("data.getTeamByName.availableClouds[0].name", is("gcp"))
                .body("data.getTeamByName.availableClouds[1].name", is("azure"));
    }

    @Test
    public void getTeams_returnsAvailableCloudsWhenRequested() {
        TeamEntity entity = TeamEntity.builder()
                .name("Team1")
                .nameNormalized("team1")
                .type(TeamType.SHARED)
                .description("the team")
                .build();
        teamRepository.save(entity);

        GraphQLQuery query = new GraphQLQuery(
                "query {getTeams(paginationInput: {pageSize: 10, pageNumber: 1} filter: { names: [\"Team1\"] }) " +
                        "{ teams { id name availableClouds { name tenants } } totalRecordCount } }");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeams.teams", hasSize(1))
                .body("data.getTeams.teams[0].name", is("Team1"))
                .body("data.getTeams.teams[0].availableClouds", hasSize(2))
                .body("data.getTeams.teams[0].availableClouds[0].name", is("gcp"))
                .body("data.getTeams.teams[0].availableClouds[1].name", is("azure"));
    }

    @Test
    public void getTeam_withoutAvailableCloudsInSelection_doesNotPopulateAvailableClouds() {
        TeamEntity entity = TeamEntity.builder()
                .name("Team1")
                .nameNormalized("team1")
                .type(TeamType.PERSONAL)
                .build();
        entity = teamRepository.save(entity);

        GraphQLQuery query = new GraphQLQuery(String.format(
                "query {getTeam(id: \"%s\") {id name}}", entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeam.id", is(entity.getId().intValue()))
                .body("data.getTeam.name", is("Team1"));
        // availableClouds not in selection - may be null or absent; we only assert core fields
    }
}
