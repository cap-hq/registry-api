// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Random;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static org.hamcrest.Matchers.*;

public class MemberResolverIntegrationTests extends GraphQLTests {
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();

        authApiWiremockTestHelper = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApiWiremockTestHelper.start();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        authApiWiremockTestHelper.stop();
    }

    @Test
    public void getMembers() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("333333").externalAlias("C333333").email("email3@example.com").name("AA3").lastName("lastNameAA3").firstName("firstNameAA3").build(),
                MemberEntity.builder().externalId("555555").employeeId("555555").externalAlias("C555555").email("email5@example.com").name("AA5").lastName("lastNameAA5").firstName("firstNameAA5").build(),
                MemberEntity.builder().externalId("444444").employeeId("444444").externalAlias("C444444").email("email4@example.com").name("AA4").lastName("lastNameAA4").firstName("firstNameAA4").build(),
                MemberEntity.builder().externalId("222222").employeeId("222222").externalAlias("C222222").email("email2@example.com").name("AA2").lastName("lastNameAA2").firstName("firstNameAA2").build(),
                MemberEntity.builder().externalId("777777").employeeId("777777").externalAlias("C777777").email("email7@example.com").name("AA7").lastName("lastNameAA7").firstName("firstNameAA7").build(),
                MemberEntity.builder().externalId("111111").employeeId("111111").externalAlias("C111111").email("email1@example.com").name("AA1").lastName("lastNameAA1").firstName("firstNameAA1").build(),
                MemberEntity.builder().externalId("666666").employeeId("666666").externalAlias("C666666").email("email6@example.com").name("AA6").lastName("lastNameAA6").firstName("firstNameAA6").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {names: [\"AA3\", \"AA4\", \"AA7\", \"AA6\", \"AA2\"] externalIds: [\"111111\", \"333333\", \"222222\"] externalAlias: \"C333333\"}) " +
                "{members {id externalId externalAlias name lastName firstName} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1)) // NOTE: 7 added above, but 2 members added due to setUpBeforeEachTest
                .body("data.getMembers.members[0].name", is("AA3")); // sort 1st
    }

    @Test
    public void getMembers_CaseInsensitive() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("333333").externalAlias("C333333").email("email3@example.com").name("Last, First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {names: [\"last, first\"]}) " +
                "{members {id name} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1)) // NOTE: 7 added above, but 2 members added due to setUpBeforeEachTest
                .body("data.getMembers.members[0].name", is("Last, First")); // sort 1st
    }

    @Test
    public void getMembers_partialNameMatch() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("333333").externalAlias("C333333").email("email3@example.com").name("Last, First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {name: \"last\" partialNameMatch: true}) " +
                "{members {id name} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1))
                .body("data.getMembers.members[0].name", is("Last, First"));
    }

    @Test
    public void getMembers_partialName_NoPartialNameMatch() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("333333").externalAlias("C333333").email("email3@example.com").name("Last, First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {name: \"last\" partialNameMatch: false}) " +
                "{members {id name} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(0));
    }

    @Test
    public void getMembers_filterLastName() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("333333").externalAlias("C333333").email("email3@example.com").name("Last, First").lastName("Last").firstName("First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {lastName: \"last\"}) " +
                "{members {id name lastName firstName} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1)) // NOTE: 7 added above, but 2 members added due to setUpBeforeEachTest
                .body("data.getMembers.members[0].name", is("Last, First")) // sort 1st
                .body("data.getMembers.members[0].lastName", is("Last")) // sort 1st
                .body("data.getMembers.members[0].firstName", is("First")); // sort 1st
    }

    @Test
    public void getMembers_filterLastNames() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("3333333").externalAlias("C333333").email("email3@example.com").name("Last, First").lastName("Last").firstName("First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {lastNames: [\"last\"]}) " +
                "{members {id name lastName firstName} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1)) // NOTE: 7 added above, but 2 members added due to setUpBeforeEachTest
                .body("data.getMembers.members[0].name", is("Last, First")) // sort 1st
                .body("data.getMembers.members[0].lastName", is("Last")) // sort 1st
                .body("data.getMembers.members[0].firstName", is("First")); // sort 1st
    }

    @Test
    public void getMembers_filterEmployeeId() {
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("3333333").externalAlias("C333333").email("email3@example.com").name("Last, First").lastName("Last").firstName("First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {employeeIds: [\"3333333\"]}) " +
                "{members {id name lastName firstName employeeId} totalPageCount totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1)) // NOTE: 7 added above, but 2 members added due to setUpBeforeEachTest
                .body("data.getMembers.members[0].name", is("Last, First")) // sort 1st
                .body("data.getMembers.members[0].lastName", is("Last")) // sort 1st
                .body("data.getMembers.members[0].firstName", is("First")) // sort 1st
                .body("data.getMembers.members[0].employeeId", is("3333333")); // sort 1st
    }

    @Test
    public void getMembers_filterExternalAlias() {
        String externalAlias = "JaneDoe";
        List<MemberEntity> members = List.of(
                MemberEntity.builder().externalId("333333").employeeId("3333333").externalAlias(externalAlias).email("email3@example.com").name("Last, First").lastName("Last").firstName("First").build()
        );
        memberRepository.saveAll(members);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMembers(paginationInput: { pageSize: 3, pageNumber: 1 } " +
                "filter: {externalAlias: \"%s\"}) " +
                "{members {id name externalAlias} totalPageCount totalRecordCount}}", externalAlias.toLowerCase()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(1))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(1))
                .body("data.getMembers.members[0].name", is("Last, First"))
                .body("data.getMembers.members[0].externalAlias", is(externalAlias));
    }

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void getMembersComposite() {
        String graphQL = """
                query {getMembers(paginationInput: {pageSize: 100, pageNumber: 1}) {
                    members {
                        externalId externalAlias name lastName firstName
                        teams {
                            name type description memberCount 
                            applications {
                                name description priority complianceTypes informationClassification
                            }
                            authClients { clientId clientVersion clientSecretExpiresAt redirectUrls lastAccessTokenIssuedAt}
                        }
                    }
                    totalPageCount totalRecordCount }}
                """;
        GraphQLQuery query = new GraphQLQuery(graphQL);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMembers.members", hasSize(4))
                .body("data.getMembers.totalPageCount", is(1))
                .body("data.getMembers.totalRecordCount", is(4))
                .body("data.getMembers.members[1].name", is("Tester, One")) // sort 1st
                .body("data.getMembers.members[1].teams", hasSize(6))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-1'}.applications.find {it.name == 'app8'}.priority", is(5))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-1'}.applications.find {it.name == 'app8'}.complianceTypes", is(List.of("pci")))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-1'}.applications.find {it.name == 'app8'}.informationClassification", is("RESTRICTED"))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-1'}.memberCount", is(nullValue())) // memberCount is ignored
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients", hasSize(2))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[0].clientId", is("client-credentials-flow"))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[0].clientVersion", is(1))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[0].clientSecretExpiresAt", is(notNullValue()))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[0].redirectUrls[0]", is("http://localhost:8080/callback"))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[1].clientId", is("client-credentials-flow"))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[1].clientVersion", is(2))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[1].clientSecretExpiresAt", is(notNullValue()))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[1].lastAccessTokenIssuedAt", is(notNullValue()))
                .body("data.getMembers.members[1].teams.find {it.name == 'Team-5'}.authClients[1].redirectUrls[0]", is("http://localhost:8080/callback2"));
    }

    @Test
    public void getMembersNoAuth() {
        GraphQLQuery query = new GraphQLQuery("query {getMembers {id externalId externalAlias}}");

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getMember() {
        MemberEntity entity = MemberEntity.builder().externalId("login-id-1").employeeId("1111111").externalAlias("C111111").email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMember(id: \"%s\") {id externalId externalAlias teams {name memberCount}}}", entity.getId()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMember.id", is(entity.getId().intValue()));
    }

    @Test
    public void getMemberNotFound() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMember(id: \"%s\") {id externalId externalAlias}}", new Random().nextLong()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.MEMBER_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void getMemberNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMember(id: \"%s\") {id externalId externalAlias}}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }


    @Test
    public void getMemberByExternalId() {
        String externalId = "login-id-1".toUpperCase();
        MemberEntity entity = MemberEntity.builder().externalId(externalId).employeeId("1111111").externalAlias("C111111").email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        // create new team with member
        TeamEntity teamEntity = TeamEntity.builder().name("Team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        teamEntity = teamRepository.save(teamEntity);
        TeamMemberEntity teamMemberEntity = TeamMemberEntity.builder().id(TeamMemberId.builder().memberId(entity.getId()).teamId(teamEntity.getId()).build()).role(TeamMemberRole.OWNER).build();
        teamMemberRepository.save(teamMemberEntity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByExternalId(externalId: \"%s\") {id externalId externalAlias teams {name memberCount}}}", externalId));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberByExternalId.id", is(entity.getId().intValue()))
                .body("data.getMemberByExternalId.teams.find {it.name == 'Team-1'}.name", is("Team-1"))
                .body("data.getMemberByExternalId.teams.find {it.name == 'Team-1'}.memberCount", is(1));
    }

    @Test
    public void getMemberByExternalIdNotFound() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByExternalId(externalId: \"%s\") {id externalId externalAlias}}", "foo"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.EXTERNAL_USER_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void getMemberByExternalIdNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByExternalId(externalId: \"%s\") {id externalId externalAlias}}", "foo"));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getMemberByExternalAlias() {
        String externalAlias = "C11111".toUpperCase();
        MemberEntity entity = MemberEntity.builder().externalId("external-id").employeeId("1111111").externalAlias(externalAlias).email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByExternalAlias(externalAlias: \"%s\") {id externalId externalAlias}}", externalAlias));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberByExternalAlias.id", is(entity.getId().intValue()));
    }

    @Test
    public void getMemberByExternalAlias_withLowercaseAlias() {
        String externalAlias = "c11111".toUpperCase();
        MemberEntity entity = MemberEntity.builder().externalId("external-id").employeeId("1111111").externalAlias(externalAlias).email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByExternalAlias(externalAlias: \"%s\") {id externalId externalAlias}}", externalAlias));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberByExternalAlias.id", is(entity.getId().intValue()));
    }

    @Test
    public void getMemberByExternalAlias_withLegacyAlias() {
        String externalAlias = "JaneDoe".toUpperCase();
        MemberEntity entity = MemberEntity.builder().externalId("external-id").employeeId("1111111").externalAlias(externalAlias).email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByExternalAlias(externalAlias: \"%s\") {id externalId externalAlias}}", externalAlias.toLowerCase()));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberByExternalAlias.id", is(entity.getId().intValue()));
    }

    @Test
    public void getMemberByEmail() {
        String externalAlias = "C11111".toUpperCase();
        MemberEntity entity = MemberEntity.builder().externalId("external-id").employeeId("1111111").externalAlias(externalAlias).email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        // create new team with member
        TeamEntity teamEntity = TeamEntity.builder().name("Team-1").nameNormalized("team1").type(TeamType.SHARED).externalName("AppReg_Team-1").build();
        teamEntity = teamRepository.save(teamEntity);
        TeamMemberEntity teamMemberEntity = TeamMemberEntity.builder().id(TeamMemberId.builder().memberId(entity.getId()).teamId(teamEntity.getId()).build()).role(TeamMemberRole.OWNER).build();
        teamMemberRepository.save(teamMemberEntity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByEmail(email: \"%s\") {id externalId externalAlias teams {name externalName memberCount}}}", "email-1@example.com"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberByEmail.id", is(entity.getId().intValue()))
                .body("data.getMemberByEmail.teams.find {it.name == 'Team-1'}.name", is("Team-1"))
                .body("data.getMemberByEmail.teams.find {it.name == 'Team-1'}.externalName", is("AppReg_Team-1"))
                .body("data.getMemberByEmail.teams.find {it.name == 'Team-1'}.memberCount", is(1));
    }

    @Test
    public void getMemberByEmail_CaseInsensitive() {
        MemberEntity entity = MemberEntity.builder().externalId("external-id").employeeId("1111111").externalAlias("C11111").email("email-1@example.com").name("user-1").build();
        entity = memberRepository.save(entity);
        GraphQLQuery query = new GraphQLQuery(String.format("query {getMemberByEmail(email: \"%s\") {id externalId externalAlias}}", "Email-1@example.com"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberByEmail.id", is(entity.getId().intValue()));
    }

    @Test
    public void createMemberByExternalAlias() {
        String externalAlias = "onetester";
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createMemberByExternalAlias(externalAlias: \"%s\") {id externalId externalAlias}}", externalAlias));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createMemberByExternalAlias.externalAlias", is(externalAlias))
                .body("data.createMemberByExternalAlias.id", is(not(emptyString())));
    }

    @Test
    public void createMemberByExternalId() {
        String externalId = "00000000-0000-0000-0000-000000000005";
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createMemberByExternalId(externalId: \"%s\") {id externalId externalAlias}}", externalId));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createMemberByExternalId.externalId", is(externalId))
                .body("data.createMemberByExternalId.id", is(not(emptyString())));
    }

    @Test
    public void ccreateMemberByExternalIdNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {createMember(externalId: \"%s\") {id externalId externalAlias}}", new Random().nextLong()));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void createMemberByExternalIdMissingExternalId() {
        GraphQLQuery query = new GraphQLQuery("mutation {createMember(externalId: null) {id externalId externalAlias}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.classification", hasItem("ValidationError"));
    }

    @Test
    public void updateMemberByExternalAlias() {
        MemberEntity entity = memberRepository.findMemberEntityByExternalId(MEMBER1_EXTERNAL_ID).get();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateMemberByExternalAlias(member: {externalAlias: \"%s\" email: \"%s\" name: \"%s\" lastName: \"%s\" firstName: \"%s\"}) {id externalId externalAlias email}}",
                member1.getExternalAlias(), "email-1@example.com", "user-1", "Last", "First"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateMemberByExternalAlias.email", is("email-1@example.com"))
                .body("data.updateMemberByExternalAlias.id", is(entity.getId().intValue()));
    }

    @Test
    public void updateMemberByExternalId() {
        MemberEntity entity = memberRepository.findMemberEntityByExternalId(MEMBER1_EXTERNAL_ID).get();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateMemberByExternalId(member: {externalId: \"%s\" email: \"%s\" name: \"%s\" lastName: \"%s\" firstName: \"%s\"}) {id externalId externalAlias email}}",
                MEMBER1_EXTERNAL_ID, "email-1@example.com", "user-1", "Last", "First"));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateMemberByExternalId.email", is("email-1@example.com"))
                .body("data.updateMemberByExternalId.id", is(entity.getId().intValue()));
    }

    @Test
    public void updateMemberByExternalIdNoAuth() {
        MemberEntity entity = memberRepository.findMemberEntityByExternalId(MEMBER1_EXTERNAL_ID).get();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateMember(member: {id: \"%s\" externalId: \"%s\" email: \"%s\" name: \"%s\" lastName: \"%s\" firstName: \"%s\"}) {id externalId externalAlias email}}",
                entity.getId(), MEMBER1_EXTERNAL_ID, "email-1@example.com", "user-1", "Last", "First"));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void updateMemberByExternalIdUnauthorized() {
        MemberEntity entity = memberRepository.findMemberEntityByExternalId(MEMBER1_EXTERNAL_ID).get();
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {updateMemberByExternalId(member: {externalId: \"%s\" email: \"%s\" name: \"%s\" lastName: \"%s\" firstName: \"%s\"}) {id externalId externalAlias email}}",
                MEMBER1_EXTERNAL_ID, "email-1@example.com", "user-1", "Last", "First"));

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.ACCESS_DENIED.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.FORBIDDEN.name()));
    }

}
