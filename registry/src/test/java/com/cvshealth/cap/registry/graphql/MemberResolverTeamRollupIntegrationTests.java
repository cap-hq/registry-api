// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.RollupApiWiremockTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static org.hamcrest.Matchers.*;

@ActiveProfiles({"test", "teamRollup"})
public class MemberResolverTeamRollupIntegrationTests extends GraphQLTests {
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;
    private RollupApiWiremockTestHelper rollupApiWiremockTestHelper;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();

        authApiWiremockTestHelper = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApiWiremockTestHelper.start();

        rollupApiWiremockTestHelper = new RollupApiWiremockTestHelper(8789);
        rollupApiWiremockTestHelper.start();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        authApiWiremockTestHelper.stop();
        if (rollupApiWiremockTestHelper != null) {
            rollupApiWiremockTestHelper.stop();
        }
    }

    @Test
    public void getMembers() {
        String teamName = uniqueName("Team");
        TeamEntity team = teamRepository.save(TeamEntity.builder().name(teamName).nameNormalized(normalizeName(teamName)).type(TeamType.SHARED).build());
        List<MemberEntity> members = memberRepository.saveAll(List.of(
                MemberEntity.builder().externalId("333333").employeeId("333333").externalAlias("C333333").email("email3@example.com").name("AA3").lastName("lastNameAA3").firstName("firstNameAA3").build(),
                MemberEntity.builder().externalId("555555").employeeId("555555").externalAlias("C555555").email("email5@example.com").name("AA5").lastName("lastNameAA5").firstName("firstNameAA5").build()
        ));
        teamMemberRepository.saveAll(List.of(
                TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(team.getId()).memberId(members.get(0).getId()).build()).role(TeamMemberRole.OWNER).build(),
                TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(team.getId()).memberId(members.get(1).getId()).build()).role(TeamMemberRole.OWNER).build()
        ));
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {names: [\"AA3\", \"AA5\"] }) " +
                "{members {id externalId externalAlias name lastName firstName teams{name teamRollup { managers {lastName}} }} totalPageCount totalRecordCount}}");

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(2))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(2))
                .body("data.getMembers.members[0].name", is("AA3")) // sort 1st
                .body("data.getMembers.members[0].teams[0].teamRollup.managers[0].lastName", is("Manager"));
    }

    @Test
    public void getMember() {
        String teamName = uniqueName("Team");
        TeamEntity team = teamRepository.save(TeamEntity.builder().name(teamName).nameNormalized(normalizeName(teamName)).type(TeamType.SHARED).build());
        MemberEntity entity = MemberEntity.builder().externalId("login-id-1").employeeId("1111111").externalAlias("C111111").email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        teamMemberRepository.save(TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(team.getId()).memberId(entity.getId()).build()).role(TeamMemberRole.OWNER).build());

        GraphQLQuery query = new GraphQLQuery(String.format("query {getMember(id: \"%s\") {id externalId externalAlias teams {name teamRollup { managers {lastName}} }}}", entity.getId()));

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMember.id", is(entity.getId().intValue()))
                .body("data.getMember.teams[0].teamRollup.managers[0].lastName", is("Manager"));
    }
}
