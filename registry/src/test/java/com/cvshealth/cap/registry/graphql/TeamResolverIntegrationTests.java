// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.config.DownstreamApplicationRestClientConfig;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.RestClient;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;


public class TeamResolverIntegrationTests extends GraphQLTests {

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
    public void getTeams() {
        List<TeamEntity> teams = List.of(
                TeamEntity.builder().name("ATeam1").nameNormalized("ateam1").type(TeamType.PERSONAL).description("the team").build(),
                TeamEntity.builder().name("ATeam2").nameNormalized("ateam2").type(TeamType.SHARED).build(),
                TeamEntity.builder().name("ATeam3").nameNormalized("ateam3").type(TeamType.PERSONAL).build(),
                TeamEntity.builder().name("ATeam4").nameNormalized("ateam4").type(TeamType.SHARED).build(),
                TeamEntity.builder().name("ATeam5").nameNormalized("ateam5").type(TeamType.PERSONAL).build()
        );
        teamRepository.saveAll(teams);
        GraphQLQuery query = new GraphQLQuery("query {getTeams (paginationInput: {pageSize: 2, pageNumber: 1} " +
                "filter: { type: PERSONAL }) " +
                "{teams{id name type description} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeams.teams", hasSize(2))
                .body("data.getTeams.totalPageCount", is(3))
                .body("data.getTeams.totalRecordCount", is(5)) // NOTE: 5 added above, and 2 personal added due to setUpBeforeEachTest
                .body("data.getTeams.teams.find {it.name == 'ATeam1'}.type", is(TeamType.PERSONAL.name()))
                .body("data.getTeams.teams.find {it.name == 'ATeam1'}.description", is("the team"))
                .body("data.getTeams.teams.find {it.name == 'ATeam3'}.type", is(TeamType.PERSONAL.name()));
    }

    @Test
    public void getTeams_partialNameMatch() {
        List<TeamEntity> teams = List.of(
                TeamEntity.builder().name("ATeam1").nameNormalized("ateam1").type(TeamType.SHARED).description("the team").build(),
                TeamEntity.builder().name("ATeam2").nameNormalized("ateam2").type(TeamType.SHARED).build(),
                TeamEntity.builder().name("ATeam3").nameNormalized("ateam3").type(TeamType.SHARED).build(),
                TeamEntity.builder().name("ATeam10").nameNormalized("ateam10").type(TeamType.SHARED).build(),
                TeamEntity.builder().name("ATeam5").nameNormalized("ateam5").type(TeamType.SHARED).build()
        );
        teamRepository.saveAll(teams);
        GraphQLQuery query = new GraphQLQuery("query {getTeams (paginationInput: {pageSize: 100, pageNumber: 1} " +
                "filter: { name: \"team1\" partialNameMatch: true }) " +
                "{teams{id name type} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeams.teams", hasSize(2))
                .body("data.getTeams.totalPageCount", is(1))
                .body("data.getTeams.totalRecordCount", is(2))
                .body("data.getTeams.teams.find {it.name == 'ATeam1'}.type", is(TeamType.SHARED.name()))
                .body("data.getTeams.teams.find {it.name == 'ATeam10'}.type", is(TeamType.SHARED.name()));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void getTeamsComposite() {
        String graphQL = """
                query {getTeams (paginationInput: {pageSize: 100, pageNumber: 1}) {
                    teams {
                        id name type description
                        members { name lastName firstName }
                        applications { name description complianceTypes priority informationClassification }
                        authClients { clientId clientVersion clientSecretExpiresAt redirectUrls lastAccessTokenIssuedAt }
                    }
                    totalPageCount totalRecordCount }}
                """;
        GraphQLQuery query = new GraphQLQuery(graphQL);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeams.teams", hasSize(9))
                .body("data.getTeams.totalPageCount", is(1))
                .body("data.getTeams.totalRecordCount", is(9))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.type", is(TeamType.SHARED.name()))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.description", is("team #1"))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.applications", hasSize(3))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.applications.find {it.name == 'app8'}.priority", is(5))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.applications.find {it.name == 'app8'}.complianceTypes", is(List.of("pci")))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.applications.find {it.name == 'app8'}.informationClassification", is("RESTRICTED"))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.members", hasSize(2))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients", hasSize(2))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[0].clientId", is("client-credentials-flow"))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[0].clientVersion", is(1))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[0].clientSecretExpiresAt", is(notNullValue()))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[0].lastAccessTokenIssuedAt", is(notNullValue()))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[0].redirectUrls[0]", is("http://localhost:8080/callback"))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[1].clientId", is("client-credentials-flow"))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[1].clientVersion", is(2))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[1].clientSecretExpiresAt", is(notNullValue()))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[1].lastAccessTokenIssuedAt", is(notNullValue()))
                .body("data.getTeams.teams.find {it.name == 'Team-5'}.authClients[1].redirectUrls[0]", is("http://localhost:8080/callback2"));
    }

    @Test
    public void getTeamsNoAuth() {
        givenGraphQLNoAuthThen401(new GraphQLQuery("query {getTeams {id name type}}"));
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
    public void getTeamWithEmail() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).emailList("email@email.com").build();
        entity = teamRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeam(id: \"%s\") {id name type emailList}}", entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeam.id", is(entity.getId().intValue()))
                .body("data.getTeam.emailList", is(entity.getEmailList()));
    }

    @Test
    public void getTeamNoAuth() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).build();
        entity = teamRepository.save(entity);

        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeam(id: \"%s\") {id name type}}", entity.getId()));
        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getTeamNotFound() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeam(id: \"%s\") {id name type}}", new Random().nextLong()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void getTeamByName() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).description("the team").emailList("email@email.com").build();
        entity = teamRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeamByName(name: \"%s\") {id name type description emailList}}", entity.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeamByName.id", is(entity.getId().intValue()))
                .body("data.getTeamByName.description", is("the team"))
                .body("data.getTeamByName.emailList", is(entity.getEmailList()));

    }

    @Test
    public void getTeamByName_CaseInsensitive() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).description("the team").build();
        entity = teamRepository.save(entity);

        // given lower case team name
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeamByName(name: \"%s\") {id name type description}}", entity.getName().toUpperCase()));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeamByName.id", is(entity.getId().intValue()))
                .body("data.getTeamByName.description", is("the team"));

        // given lower case team name
        query = new GraphQLQuery(String.format("query {getTeamByName(name: \"%s\") {id name type description}}", entity.getName().toLowerCase()));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeamByName.id", is(entity.getId().intValue()))
                .body("data.getTeamByName.description", is("the team"));
    }

    @Test
    public void getTeamsByFilter_CaseInsensitive() {
        TeamEntity entity = TeamEntity.builder().name("Team-1").nameNormalized("team1").type(TeamType.PERSONAL).description("the team").build();
        entity = teamRepository.save(entity);
        TeamEntity entity2 = TeamEntity.builder().name("team2").nameNormalized("team2").type(TeamType.PERSONAL).description("the second team").build();
        entity2 = teamRepository.save(entity2);
        TeamEntity entity3 = TeamEntity.builder().name("TEAM3").nameNormalized("team3").type(TeamType.PERSONAL).description("the third team").build();
        entity3 = teamRepository.save(entity3);

        // given lower case team name
        GraphQLQuery query = new GraphQLQuery(String.format("query {getTeams(paginationInput: { pageSize: 100, pageNumber: 1 } filter: { names: [\"%s\", \"%s\"] }) { teams {id name type description}}}", "team-1", "team3"));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeams.teams", hasSize(2))
                .body("data.getTeams.teams.find {it.name == 'Team-1'}.name", is("Team-1"))
                .body("data.getTeams.teams.find {it.name == 'TEAM3'}.name", is("TEAM3"));
    }

    @Test
    public void createTeam() {
        teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeam(team: {name: \"newteam\", type: %s, description: \"the team\"}) {id name description externalName}}", TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createTeam.name", is("newteam"))
                .body("data.createTeam.id", is(not(emptyString())))
                .body("data.createTeam.description", is("the team"))
                .body("data.createTeam.externalName", is("Registry_newteam"));
    }

    @Test
    public void createTeamWithEmail() {
        teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeam(team: {name: \"newteam\", type: %s, description: \"the team\"}) {id name description}}", TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createTeam.name", is("newteam"))
                .body("data.createTeam.id", is(not(emptyString())))
                .body("data.createTeam.description", is("the team"));
    }

    @Test
    public void createTeamNotInAuthorizedTeamsList() {
        TeamEntity team = teamRepository.save(TeamEntity.builder().name("Test-Auth-Team").nameNormalized("testauthteam").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("user2").externalAlias("C22222").email("notinauthlist@example.com").name("user-1").build());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeam(team: {name: \"newteam\", type: %s, description: \"the team\"}) {id name description}}", TeamType.SHARED));

        givenGraphQL(query, member.getExternalId())
                .body("errors.extensions.reason", hasItem(ExceptionReason.NOT_IN_AUTHORIZED_TEAMS_LIST.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.UNAUTHORIZED.name()));
    }

    @Test
    public void createTeamNoAuth() {
        teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {addTeam(team: {name: \"newteam\", type: %s}) {id name}}", TeamType.SHARED));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createTeamMissingName() {
        teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());

        GraphQLQuery query = new GraphQLQuery("mutation {createTeam(team: {}) {id name}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.classification", hasItem("ValidationError"));
    }

    @Test
    public void createTeamNameTooLong() {
        int maxLength = ValidationConstraintsFixture.teamNameConstraints().getMaxLength();
        String name = "A" + "a".repeat(maxLength); // maxLength + 1 chars
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createTeam(team: {name: \"%s\", type: %s}) {id name}}", name, TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NAME_INVALID_LENGTH.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createTeamNameAtMaxLength() {
        int maxLength = ValidationConstraintsFixture.teamNameConstraints().getMaxLength();
        String name = "A" + "a".repeat(maxLength - 1); // exactly maxLength chars
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createTeam(team: {name: \"%s\", type: %s}) {id name}}", name, TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createTeam.name", org.hamcrest.Matchers.equalTo(name));
    }

    @Test
    public void createTeamNameInvalidFormat() {
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createTeam(team: {name: \"-BadStart\", type: %s}) {id name}}", TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NAME_INVALID_FORMAT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createTeamSharedNamePersonalPrefixInvalid() {
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createTeam(team: {name: \"Personal-foo\", type: %s}) {id name}}", TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NAME_INVALID_FORMAT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createTeamSharedNamePersonalPrefixCaseInsensitive() {
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createTeam(team: {name: \"personal-foo\", type: %s}) {id name}}", TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NAME_INVALID_FORMAT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createTeamPersonalNameInvalidFormat() {
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createTeam(team: {name: \"NotPersonal\", type: %s}) {id name}}", TeamType.PERSONAL));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NAME_PERSONAL_INVALID_FORMAT.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createTeamDuplicateName() {
        teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeam(team: {name: \"Team1\", type: %s}) {id name}}", TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_DUPLICATE_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createTeam_ExceedMaxAllowedTeamMemberships() {
        Member owner = createMemberWithMaxAllowedTeamMemberships();

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeam(team: {name: \"newteam\", type: %s, description: \"the team\"}) {id name description}}", TeamType.SHARED));

        givenGraphQL(query, owner.getExternalId())
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));

        Optional<TeamEntity> newTeam = teamRepository.findByName("newteam");
        assertThat(newTeam).isEmpty();
    }

    @Test
    public void updateTeam() {
        TeamEntity entity = teamRepository.findByName(MEMBER1_TEAM_NAME).get();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeam(team: {name: \"%s\", type: %s, description: \"my team\"}) {id name type description externalName}}",
                entity.getName().toUpperCase(), entity.getType()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateTeam.name", is(entity.getName()))
                .body("data.updateTeam.id", is(entity.getId().intValue()))
                .body("data.updateTeam.type", is("PERSONAL"))
                .body("data.updateTeam.description", is("my team"))
                .body("data.updateTeam.externalName", is(nullValue()));
    }

    @Test
    public void updateTeam_Normalized() {
        TeamEntity entity = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeam(team: {name: \"%s\", type: %s, description: \"my team\"}) {id name description}}",
                entity.getName(), entity.getType()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateTeam.name", is(entity.getName()))
                .body("data.updateTeam.id", is(entity.getId().intValue()))
                .body("data.updateTeam.description", is("my team"));
    }

    @Test
    public void updateTeamNotTeamMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeam(team: {name: \"%s\", type: %s}) {id name}}",
                team.getName(), team.getType()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateTeamTeamMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeam(team: {name: \"%s\", type: %s}) {id name}}",
                team.getName(), team.getType()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.updateTeam.name", is(team.getName()))
                .body("data.updateTeam.id", is(team.getId().intValue()));
    }

    @Test
    public void updateTeamNotTeamOwnerOrMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.VIEWER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeam(team: {name: \"%s\", type: %s}) {id name}}",
                team.getName(), team.getType()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void updateTeamNotFound() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeam(team: {name: \"foo\", type: %s}) {id name}}",
                TeamType.SHARED));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void deleteTeam() {
        var team1 = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        var teamId1 = teamRepository.save(team1).getId();
        var team2 = TeamEntity.builder().name("team-2").nameNormalized("team2").type(TeamType.SHARED).build();
        var teamId2 = teamRepository.save(team2).getId();
        var app1 = ApplicationEntity.builder().name("app1").teamId(teamId2).build();
        var appId1 = applicationRepository.save(app1).getId();
        // only member1 is associated to the team
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team1.getId()).memberId(member1.getId()).build())
                .build());
        applicationTransferRepository.save(ApplicationTransferEntity.builder()
                .applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.COMPLETED).build());

        // mock DownstreamRestClientConfig
        LabeledRestClient client = LabeledRestClient.builder()
                .label("App1 Name")
                .restClient(RestClient.builder()
                        .baseUrl("http://localhost:7777/teamcheck")
                        .defaultHeader("token", "app1testtoken")
                        .build()).build();

        List<LabeledRestClient> clients = List.of(client);

        when(downstreamApplicationRestClientConfig.downstreamApplicationRestClients()).thenReturn(clients);

        // mock the endpoint called by DownstreamRestClientConfig
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = null;
        try {
            jsonBody = mapper.writeValueAsString(new DownstreamAppResponse(false));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        downstreamAppWiremock.stubFor(get(urlPathEqualTo("/teamcheck/" + team1.getName()))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(jsonBody)));

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(name: \"%s\")}", team1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteTeam", is(true));
    }

    @Test
    public void deleteTeamWithAssociatedApplication() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        applicationRepository.save(ApplicationEntity.builder().name("app").teamId(team.getId()).build());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(name: \"%s\")}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_DELETE_WITH_APPLICATION.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteTeamWithAssociatedMembers() {
        var team1 = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team1);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team1.getId()).memberId(member1.getId()).build())
                .build());
        // team member that is not the current user still associated to the team - causes the validation error
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team1.getId()).memberId(member2.getId()).build())
                .build());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(name: \"%s\")}", team1.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAM_DELETE_WITH_MEMBERS.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void deleteTeamNoAuth() {
        TeamEntity entity = teamRepository.findAllByNameNormalized(MEMBER1_NORMALIZED_PERSONAL_TEAM_NAME).get(0);
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(id: \"%s\")}", entity.getId()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void deleteTeamNotTeamMember() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(name: \"%s\")}", team.getName()));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void deleteTeamTeamMemberNotAllowed() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.MEMBER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(name: \"%s\")}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void deleteTeamTeamViewerNotAllowed() {
        TeamEntity team = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamRepository.save(team);
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.VIEWER)
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeam(name: \"%s\")}", team.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createTeamMember() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("user2").externalAlias("C11111").email("email-1").name("user-1").build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\" role: %s) {teamId memberId}}",
                team.getName(), member.getExternalAlias(), TeamMemberRole.OWNER));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createTeamMemberByExternalAlias.teamId", is(team.getId().intValue()))
                .body("data.createTeamMemberByExternalAlias.memberId", is(member.getId().intValue()));
    }

    @Test
    public void createTeamMemberByExternalAlias_ExceedMaxAllowedTeamMemberships() {
        Member member = createMemberWithMaxAllowedTeamMemberships();

        var team = teamRepository.save(TeamEntity.builder().name("newteam").nameNormalized("newteam").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\" role: %s) {teamId memberId}}",
                team.getName(), member.getExternalAlias(), TeamMemberRole.OWNER));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    private Member createMemberWithMaxAllowedTeamMemberships() {
        List<TeamEntity> teamEntities = new ArrayList<>();
        for (int i = 0; i < maxAllowedTeamMemberships; i++) {
            teamEntities.add(TeamEntity.builder().name("Team" + i).nameNormalized("team" + i).type(TeamType.SHARED).build());
        }
        var teams = teamRepository.saveAll(teamEntities);

        List<TeamMemberEntity> teamMembers = new ArrayList<>();
        teams.forEach(team -> {
            teamMembers.add(TeamMemberEntity.builder()
                    .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                    .role(TeamMemberRole.OWNER)
                    .build());
        });
        teamMemberRepository.saveAll(teamMembers);
        return member1;
    }

    @Test
    public void createTeamMemberNoAuth() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("user2").externalAlias("C11111").email("email-1").name("user-1").build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeamMember(teamId: \"%s\" memberId: \"%s\" role: %s) {teamId memberId}}",
                team.getId(), member.getId(), TeamMemberRole.OWNER));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createTeamMemberNotTeamOwner() {
        var team = teamRepository.save(buildTeam("Team1", TeamType.SHARED));
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("user2").externalAlias("C22222").email("testemail@example.com").name("user-1").build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\" role: %s) {teamId memberId}}",
                team.getName(), member.getExternalAlias(), TeamMemberRole.OWNER));

        givenGraphQL(query, member.getExternalId())
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createTeamMemberByExternalId() {
        var team = teamRepository.save(buildTeam("Team1", TeamType.SHARED));
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        Member member = registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID);

        GraphQLQuery query = new GraphQLQuery();
        query.setQuery(String.format("mutation { createTeamMemberByExternalId (externalId: \"%s\", teamName: \"%s\", role: %s) { memberId, teamId, role }}",
                MEMBER2_EXTERNAL_ID, team.getName(), TeamMemberRole.MEMBER));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createTeamMemberByExternalId.teamId", is(team.getId().intValue()))
                .body("data.createTeamMemberByExternalId.memberId", is(member.getId().intValue()));
    }

    @Test
    public void createTeamMemberByExternalIdNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation { createTeamMemberByExternalId (externalId: \"%s\", teamId: \"%s\", role: %s) { memberId, teamId, role }}",
                MEMBER2_EXTERNAL_ID, new Random().nextLong(), TeamMemberRole.MEMBER));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createTeamMemberByExternalIdNotTeamOwner() {
        var team = teamRepository.save(buildTeam("Team1", TeamType.SHARED));
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        registryService.getMemberByExternalId(MEMBER2_EXTERNAL_ID); // create member & personal team

        GraphQLQuery query = new GraphQLQuery(String.format("mutation { createTeamMemberByExternalId (externalId: \"%s\", teamName: \"%s\", role: %s) { memberId, teamId, role }}",
                MEMBER2_EXTERNAL_ID, team.getName(), TeamMemberRole.OWNER));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createTeamMemberByExternalId_ExceedMaxAllowedTeamMemberships() {
        var member = createMemberWithMaxAllowedTeamMemberships();
        var team = teamRepository.save(TeamEntity.builder().name("newteam").nameNormalized("newteam").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());

        GraphQLQuery query = new GraphQLQuery(String.format("mutation { createTeamMemberByExternalId (externalId: \"%s\", teamName: \"%s\", role: %s) { memberId, teamId, role }}",
                member.getExternalId(), team.getName(), TeamMemberRole.OWNER));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void updateTeamMemberByExternalAlias() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\" role: %s) {teamId memberId}}",
                team.getName(), member2.getExternalAlias(), TeamMemberRole.MEMBER));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateTeamMemberByExternalAlias.teamId", is(team.getId().intValue()))
                .body("data.updateTeamMemberByExternalAlias.memberId", is(member2.getId().intValue()));
    }

    @Test
    public void updateTeamMemberByExternalAliasNoAuth() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeamMember(teamId: \"%s\" memberId: \"%s\" role: %s) {teamId memberId}}",
                team.getId(), member2.getId(), TeamMemberRole.MEMBER));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void updateTeamMemberByExternalAliasNoTeamOwners() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\" role: %s) {teamId memberId}}",
                team.getName(), member1.getExternalAlias(), TeamMemberRole.MEMBER));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.TEAMMEMBER_ONE_OWNER_REQUIRED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void updateTeamMemberByExternalId() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateTeamMemberByExternalId(teamName: \"%s\" externalId: \"%s\" role: %s) {teamId memberId}}",
                team.getName(), member2.getExternalId(), TeamMemberRole.MEMBER));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateTeamMemberByExternalId.teamId", is(team.getId().intValue()))
                .body("data.updateTeamMemberByExternalId.memberId", is(member2.getId().intValue()));
    }

    @Test
    public void deleteTeamMemberByExternalAliasMember() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("login-id-1").externalAlias("C22222").email("email-1").name("user-1").build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\")}",
                team.getName(), member.getExternalAlias()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteTeamMemberByExternalAlias", is(true));
    }

    @Test
    public void deleteTeamMemberByExternalAliasNoAuth() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("login-id-1").externalAlias("C11111").email("email-1").name("user-1").build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeamMember(teamId: \"%s\" memberId: \"%s\")}",
                team.getId(), member.getId()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void deleteTeamMemberByExternalAliasNotTeamOwner() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("login-id-1").externalAlias("C11111").email("testemail@example.com").name("user-1").build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeamMemberByExternalAlias(teamName: \"%s\" externalAlias: \"%s\")}",
                team.getName(), member.getExternalAlias()));

        givenGraphQL(query, member.getExternalId())
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void deleteTeamMemberByExternalIdMember() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("login-id-1").externalAlias("C22222").email("email-1").name("user-1").build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeamMemberByExternalId(teamName: \"%s\" externalId: \"%s\")}",
                team.getName(), member.getExternalId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteTeamMemberByExternalId", is(true));
    }

    @Test
    public void deleteTeamMemberByExternalIdNotTeamOwner() {
        var team = teamRepository.save(TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        var member = memberRepository.save(MemberEntity.builder().externalId("login-id-1").externalAlias("C11111").email("testemail@example.com").name("user-1").build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteTeamMemberByExternalId(teamName: \"%s\" externalId: \"%s\")}",
                team.getName(), member.getExternalId()));

        givenGraphQL(query, member.getExternalId())
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void getCloudsForTeamByName() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).description("the team").emailList("email@email.com").build();
        entity = teamRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getCloudsForTeamByName(teamName: \"%s\") {name tenants}}", entity.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getCloudsForTeamByName", hasSize(2))
                .body("data.getCloudsForTeamByName[0].name", is("gcp"))
                .body("data.getCloudsForTeamByName[0].tenants", hasSize(1))
                .body("data.getCloudsForTeamByName[0].tenants[0]", is("general"))
                .body("data.getCloudsForTeamByName[1].name", is("azure"))
                .body("data.getCloudsForTeamByName[1].tenants", hasSize(1))
                .body("data.getCloudsForTeamByName[1].tenants[0]", is("general"));
    }

    @Test
    public void getCloudsForTeamByName_caseInsensitive() {
        TeamEntity entity = TeamEntity.builder().name("Team1").nameNormalized("team1").type(TeamType.PERSONAL).description("the team").emailList("email@email.com").build();
        teamRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery("query {getCloudsForTeamByName(teamName: \"team1\") {name tenants}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getCloudsForTeamByName", hasSize(2));
    }

}
