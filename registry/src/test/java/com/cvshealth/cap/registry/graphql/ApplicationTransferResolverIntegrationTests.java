// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.ApplicationTransferStatus;
import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Random;

import static org.hamcrest.Matchers.*;

public class ApplicationTransferResolverIntegrationTests extends GraphQLTests {
    private Long teamId1;
    private String teamName1;
    private Long appId1;
    private String appName1;
    private Long teamId2;
    private String teamName2;
    private Long appId2;
    private String appName2;

    private final String ADD_TRANSFER_QUERY = "mutation {createApplicationTransfer(applicationTransfer: {applicationName: \"%s\", sourceTeamName: \"%s\", receiveTeamName: \"%s\"}) {id status}}";
    private final String ACCEPT_TRANSFER_QUERY = "mutation {acceptApplicationTransfer(applicationTransfer: {applicationName: \"%s\", sourceTeamName: \"%s\", receiveTeamName: \"%s\"}) {id status}}";
    private final String REJECT_TRANSFER_QUERY = "mutation {rejectApplicationTransfer(applicationTransfer: {applicationName: \"%s\", sourceTeamName: \"%s\", receiveTeamName: \"%s\"}) {id status}}";
    private final String CANCEL_TRANSFER_QUERY = "mutation {cancelApplicationTransfer(applicationTransfer: {applicationName: \"%s\", sourceTeamName: \"%s\", receiveTeamName: \"%s\"}) {id status}}";

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        var team1 = TeamEntity.builder().name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamId1 = teamRepository.save(team1).getId();
        teamName1 = team1.getName();
        var team2 = TeamEntity.builder().name("team-2").nameNormalized("team2").type(TeamType.SHARED).build();
        teamId2 = teamRepository.save(team2).getId();
        teamName2 = team2.getName();
        var app1 = ApplicationEntity.builder().name("app1").teamId(teamId1)
                .informationClassification(InformationClassification.PUBLIC).build();
        appId1 = applicationRepository.save(app1).getId();
        appName1 = app1.getName();
        var app2 = ApplicationEntity.builder().name("app2").teamId(teamId2).build();
        appId2 = applicationRepository.save(app2).getId();
        appName2 = app2.getName();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
    }

    @Test
    public void getApplicationTransfer() {
        var entity = ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build();
        entity = applicationTransferRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationTransfer (id: \"%s\") {id status}}", entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.getApplicationTransfer.status", is(ApplicationTransferStatus.PENDING.toString()));
    }

    @Test
    public void getApplicationTransferNoAuth() {
        var entity = ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build();
        entity = applicationTransferRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationTransfer (id: \"%s\" {id status}}", entity.getId()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getApplicationTransfers() {
        List<ApplicationTransferEntity> transfers = List.of(
                ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId2).sourceTeamId(teamId2).receiveTeamId(teamId1).status(ApplicationTransferStatus.PENDING).build()

        );
        List<ApplicationTransferEntity> entities = applicationTransferRepository.saveAll(transfers);
        GraphQLQuery query = new GraphQLQuery("query {getApplicationTransfers {id status}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationTransfers", hasSize(2))
                .body("data.getApplicationTransfers.id", hasItem(entities.get(0).getId().intValue()))
                .body("data.getApplicationTransfers.id", hasItem(entities.get(1).getId().intValue()));
    }

    @Test
    public void getApplicationTransfersNoAuth() {
        GraphQLQuery query = new GraphQLQuery();
        query.setQuery("query {getApplicationTransfers {id status}}");

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getApplicationTransfersForTeam() {
        var team3 = TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build();
        Long teamId3 = teamRepository.save(team3).getId();
        var app3 = ApplicationEntity.builder().name("app3").teamId(teamId3).build();
        Long appId3 = applicationRepository.save(app3).getId();
        List<ApplicationTransferEntity> transfers = List.of(
                ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId2).sourceTeamId(teamId2).receiveTeamId(teamId1).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId3).sourceTeamId(teamId3).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build()
        );
        List<ApplicationTransferEntity> entities = applicationTransferRepository.saveAll(transfers);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationTransfersForTeam (teamId: \"%s\") {id status}}", teamId1));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationTransfersForTeam", hasSize(2))
                .body("data.getApplicationTransfersForTeam.id", hasItem(entities.get(0).getId().intValue()))
                .body("data.getApplicationTransfersForTeam.id", hasItem(entities.get(1).getId().intValue()));
    }

    @Test
    public void getApplicationTransfersForTeamByName() {
        var team3 = TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build();
        Long teamId3 = teamRepository.save(team3).getId();
        var app3 = ApplicationEntity.builder().name("app3").teamId(teamId3).build();
        Long appId3 = applicationRepository.save(app3).getId();
        List<ApplicationTransferEntity> transfers = List.of(
                ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId2).sourceTeamId(teamId2).receiveTeamId(teamId1).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId3).sourceTeamId(teamId3).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build()
        );
        List<ApplicationTransferEntity> entities = applicationTransferRepository.saveAll(transfers);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationTransfersForTeamByName (teamName: \"%s\") {id status application{id name informationClassification} sourceTeam{id name} receiveTeam{id name} }}", "team-1"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationTransfersForTeamByName", hasSize(2))
                .body("data.getApplicationTransfersForTeamByName.id", hasItem(entities.get(0).getId().intValue()))
                .body("data.getApplicationTransfersForTeamByName.id", hasItem(entities.get(1).getId().intValue()))
                .body("data.getApplicationTransfersForTeamByName.find {it.id == " + entities.get(0).getId() + "}.application.name", is("app1"))
                .body("data.getApplicationTransfersForTeamByName.find {it.id == " + entities.get(0).getId() + "}.application.informationClassification", is("PUBLIC"))
                .body("data.getApplicationTransfersForTeamByName.find {it.id == " + entities.get(0).getId() + "}.sourceTeam.name", is("team-1"))
                .body("data.getApplicationTransfersForTeamByName.find {it.id == " + entities.get(0).getId() + "}.receiveTeam.name", is("team-2"));
    }

    @Test
    public void getApplicationTransfersForTeamsByName() {
        var team3 = TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build();
        Long teamId3 = teamRepository.save(team3).getId();
        var app3 = ApplicationEntity.builder().name("app3").teamId(teamId3).build();
        Long appId3 = applicationRepository.save(app3).getId();
        List<ApplicationTransferEntity> transfers = List.of(
                ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId2).sourceTeamId(teamId2).receiveTeamId(teamId1).status(ApplicationTransferStatus.PENDING).build(),
                ApplicationTransferEntity.builder().applicationId(appId3).sourceTeamId(teamId3).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build()
        );
        List<ApplicationTransferEntity> entities = applicationTransferRepository.saveAll(transfers);
        GraphQLQuery query = new GraphQLQuery("query {getApplicationTransfersForTeamsByName(paginationInput: { pageNumber: 1, pageSize: 10 } " +
                "filter: {teamNames: [\"team-1\", \"team-2\"]}) " +
                "{applicationTransfers {id status application{id name description updateTimestamp} sourceTeam{id name} receiveTeam{id name} } totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers", hasSize(3))
                .body("data.getApplicationTransfersForTeamsByName.totalPageCount", is(1))
                .body("data.getApplicationTransfersForTeamsByName.totalRecordCount", is(3))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[0].application.name", is("app1"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[0].sourceTeam.name", is("team-1"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[0].receiveTeam.name", is("team-2"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[1].application.name", is("app2"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[1].sourceTeam.name", is("team-2"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[1].receiveTeam.name", is("team-1"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[2].application.name", is("app3"))
                .body("data.getApplicationTransfersForTeamsByName.applicationTransfers[2].receiveTeam.name", is("team-2"));
    }

    @Test
    public void getApplicationTransfersForTeamNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getApplicationTransfersForTeam (teamId: \"%s\") {id status}}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createApplicationTransfer() {
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ADD_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createApplicationTransfer.id", is(not(emptyString())))
                .body("data.createApplicationTransfer.status", is(ApplicationTransferStatus.PENDING.toString()));
    }

    @Test
    public void createApplicationTransferSameSourceAndReceiveTeam() {
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ADD_TRANSFER_QUERY, appName1, teamName1, teamName1));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_SAME_TEAM_IDS.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void createApplicationTransferNoAuth() {
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ADD_TRANSFER_QUERY, appId1, teamId1, teamId2));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createApplicationTransferNotTeamMember() {
        GraphQLQuery query = new GraphQLQuery(String.format(ADD_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void createApplicationTransferApplicationAlreadyBeingTransferred() {
        applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId2).receiveTeamId(teamId1).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ADD_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_ALREADY_EXISTS.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void acceptApplicationTransferSourceTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName1, teamName1.toUpperCase(), teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.acceptApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.acceptApplicationTransfer.status", is(ApplicationTransferStatus.COMPLETED.toString()));
        var transferred = applicationRepository.findById(appId1).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(transferred.getTeamId()).isEqualTo(teamId2);
        org.assertj.core.api.Assertions.assertThat(transferred.getInformationClassification())
                .isEqualTo(InformationClassification.PUBLIC);
        givenGraphQL(new GraphQLQuery("""
                query {getApplicationByName(name: "app1") {informationClassification team {name}}}
                """), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.getApplicationByName.informationClassification", is("PUBLIC"))
                .body("data.getApplicationByName.team.name", is(teamName2));
    }

    @Test
    public void rejectApplicationTransferSourceTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.rejectApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.rejectApplicationTransfer.status", is(ApplicationTransferStatus.REJECTED.toString()));
    }

    @Test
    public void cancelApplicationTransferSourceTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.cancelApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.cancelApplicationTransfer.status", is(ApplicationTransferStatus.CANCELLED.toString()));
    }

    @Test
    public void acceptApplicationTransferReceiveTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.acceptApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.acceptApplicationTransfer.status", is(ApplicationTransferStatus.COMPLETED.toString()));
    }

    @Test
    public void rejectApplicationTransferReceiveTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.rejectApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.rejectApplicationTransfer.status", is(ApplicationTransferStatus.REJECTED.toString()));
    }

    @Test
    public void cancelApplicationTransferReceiveTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member2.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.cancelApplicationTransfer.id", is(entity.getId().intValue()))
                .body("data.cancelApplicationTransfer.status", is(ApplicationTransferStatus.CANCELLED.toString()));
    }

    @Test
    public void acceptApplicationTransferNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appId1, teamId1, teamId2));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void rejectApplicationTransferNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appId1, teamId1, teamId2));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void cancelApplicationTransferNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appId1, teamId1, teamId2));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void acceptApplicationTransferNotTeamMember() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void rejectApplicationTransferNotTeamMember() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void cancelApplicationTransferNotTeamMember() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void acceptApplicationTransferNotTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void rejectApplicationTransferNotTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void cancelApplicationTransferNotTeamOwner() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName1, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

    @Test
    public void acceptApplicationTransferNotFound() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName2, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void rejectApplicationTransferNotFound() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName2, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void cancelApplicationTransferNotFound() {
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName2, teamName1, teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void acceptApplicationTransferChangingSourceTeam() {
        var team3 = teamRepository.save(TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build());
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName1, team3.getName(), teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void rejectApplicationTransferChangingSourceTeam() {
        var team3 = teamRepository.save(TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build());
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName1, team3.getName(), teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void cancelApplicationTransferChangingSourceTeam() {
        var team3 = teamRepository.save(TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build());
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName1, team3.getName(), teamName2));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void acceptApplicationTransferChangingReceiveTeam() {
        var team3 = teamRepository.save(TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build());
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(ACCEPT_TRANSFER_QUERY, appName1, teamName1, team3.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void rejectApplicationTransferChangingReceiveTeam() {
        var team3 = teamRepository.save(TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build());
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(REJECT_TRANSFER_QUERY, appName1, teamName1, team3.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

    @Test
    public void cancelApplicationTransferChangingReceiveTeam() {
        var team3 = teamRepository.save(TeamEntity.builder().name("team-3").nameNormalized("team3").type(TeamType.SHARED).build());
        var entity = applicationTransferRepository.save(ApplicationTransferEntity.builder().applicationId(appId1).sourceTeamId(teamId1).receiveTeamId(teamId2).status(ApplicationTransferStatus.PENDING).build());
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        GraphQLQuery query = new GraphQLQuery(String.format(CANCEL_TRANSFER_QUERY, appName1, teamName1, team3.getName()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.BAD_REQUEST.name()));
    }

}
