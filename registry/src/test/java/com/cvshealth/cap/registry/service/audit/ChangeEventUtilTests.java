// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.persistent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.beans.IntrospectionException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class ChangeEventUtilTests {

    @Test
    public void updatableEntityTest() throws IntrospectionException {
        Set<String> keys = ChangeEventUtil.updatableEntityKeys();
        assertThat(keys.size()).isEqualTo(5);
    }

    @Test
    public void snapshot_forTeam() {
        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .version(0)
                .type(TeamType.SHARED)
                .name("Team-1")
                .description("description")
                .nameNormalized("team1")
                .emailList("myteam1@example.com")
                .slackChannel("slack")
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();
        Map<String, Object> values = ChangeEventUtil.snapshot(team);

        assertThat(values).isNotNull();
        assertThat(values.keySet()).isEqualTo(Set.of("id","name","description","slackChannel","type","nameNormalized","emailList",
                "version", "updateUser", "createUser", "updateTimestamp", "createTimestamp"));

        ChangeEventUtil.excludeMetadata(values);
        assertThat(values.keySet()).isEqualTo(Set.of("id","name","description","slackChannel","type","nameNormalized","emailList"));
    }

    @Test
    public void snapshot_forTeamMember() {
        TeamMember teamMember = TeamMember.builder()
                .teamId(1L)
                .memberId(10L)
                .version(0)
                .role(TeamMemberRole.OWNER)
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();
        Map<String, Object> values = ChangeEventUtil.snapshot(teamMember);

        // then
        assertThat(values).isNotNull();

        assertThat(values.keySet()).isEqualTo(Set.of("teamId", "memberId", "role",
                "version", "updateUser", "createUser", "updateTimestamp", "createTimestamp"));

        ChangeEventUtil.excludeMetadata(values);
        assertThat(values.keySet()).isEqualTo(Set.of("teamId", "memberId", "role"));
    }

    @Test
    public void snapshot_forTeamMemberEntity() {
        TeamMemberEntity teamMember = TeamMemberEntity.builder()
                .id(TeamMemberId.builder().memberId(10L).teamId(1L).build())
                .version(0)
                .role(TeamMemberRole.OWNER)
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();
        Map<String, Object> values = ChangeEventUtil.snapshot(teamMember);

        assertThat(values).isNotNull();
        assertThat(values.keySet()).isEqualTo(Set.of("teamId", "memberId", "role",
                "version", "updateUser", "createUser", "updateTimestamp", "createTimestamp"));

        ChangeEventUtil.excludeMetadata(values);
        assertThat(values.keySet()).isEqualTo(Set.of("teamId", "memberId", "role"));
    }

    @Test
    public void identifiableSnapshot_forIdentifiableEntity() {
        // given
        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .version(0)
                .type(TeamType.SHARED)
                .name("Team-1")
                .description("description")
                .nameNormalized("team1")
                .emailList("myteam1@example.com")
                .slackChannel("slack")
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();

        // when
        Map<String, Object> values = ChangeEventUtil.identifiableSnapshot(team);

        // then
        assertThat(values).isNotNull();
        assertThat(values.keySet()).isEqualTo(Set.of("id", "name"));
    }

    @Test
    public void identifiableSnapshot_forIdentifiableUser() {
        // given
        MemberEntity member = MemberEntity.builder()
                .id(1L)
                .version(0)
                .name("lastName, firstName")
                .firstName("firstName")
                .lastName("lastName")
                .email("first.last@example.com")
                .employeeId("1234567")
                .externalAlias("C123456")
                .externalId("externalId")
                .build();

        // when
        Map<String, Object> values = ChangeEventUtil.identifiableSnapshot(member);

        // then
        assertThat(values).isNotNull();
        assertThat(values.keySet()).isEqualTo(Set.of("id", "email", "externalId", "externalAlias", "employeeId"));
    }

    @Test
    public void delta_forTeamEntity() {
        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .version(0)
                .type(TeamType.SHARED)
                .name("Team-1")
                .nameNormalized("team1")
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();
        Map<String, Object> oldValues = ChangeEventUtil.snapshot(team);
        // updates
        team.setDescription("description");
        team.setEmailList("myteam1@example.com");
        team.setSlackChannel("slack");
        Map<String, Object> newValues = ChangeEventUtil.snapshot(team);

        // when
        Map<String, ModelPair<Object, Object>> changes = ChangeEventUtil.delta(oldValues, newValues);

        // then
        assertThat(changes).isNotNull();
        assertThat(changes.keySet()).isEqualTo(Set.of("description", "emailList", "slackChannel"));
    }

    @Test
    public void delta_forTeamMemberEntity() {
        // given
        TeamMemberEntity teamMember = TeamMemberEntity.builder()
                .id(TeamMemberId.builder().memberId(10L).teamId(1L).build())
                .version(0)
                .role(TeamMemberRole.OWNER)
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();
        Map<String, Object> oldValues = ChangeEventUtil.snapshot(teamMember);
        teamMember.setRole(TeamMemberRole.MEMBER);
        Map<String, Object> newValues = ChangeEventUtil.snapshot(teamMember);

        // when
        Map<String, ModelPair<Object, Object>> changes = ChangeEventUtil.delta(oldValues, newValues);

        // then
        assertThat(changes).isNotNull();
        assertThat(changes.keySet()).isEqualTo(Set.of("role"));
        assertThat(changes.get("role").getFirst()).isEqualTo(TeamMemberRole.OWNER);
        assertThat(changes.get("role").getSecond()).isEqualTo(TeamMemberRole.MEMBER);
    }

    @Test
    public void toChangeEventType_forTeam() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.TEAM).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.TEAM);
    }

    @Test
    public void toChangeEventType_forTeamMember() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.MEMBER).build())
                .targetEntity(AuditEntity.builder().entityType(AuditEntityType.TEAM).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.TEAM_MEMBER);
    }

    @Test
    public void toChangeEventType_forApplication() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.APPLICATION).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.APPLICATION);
    }

    @Test
    public void toChangeEventType_forAuthClient() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.AUTH_CLIENT).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.AUTH_CLIENT);
    }

    @Test
    public void toChangeEventType_forApi() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.API).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.API);
    }

    @Test
    public void toChangeEventType_forApiConsumer() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.API_CONSUMER).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.API_CONSUMER);
    }


    @Test
    public void toChangeEventType_forMember() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .subjectEntity(AuditEntity.builder().entityType(AuditEntityType.MEMBER).build())
                .build();

        // when
        ChangeEventType eventType = ChangeEventUtil.toChangeEventType(auditEntry);

        // then
        assertThat(eventType).isNotNull();
        assertThat(eventType).isEqualTo(ChangeEventType.MEMBER);
    }

    @Test
    public void toChangeEventAction_forCreate() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.CREATE)
                .build();

        // when
        ChangeEventAction eventAction = ChangeEventUtil.toChangeEventAction(auditEntry);

        // then
        assertThat(eventAction).isNotNull();
        assertThat(eventAction).isEqualTo(ChangeEventAction.CREATE);
    }

    @Test
    public void toChangeEventAction_forUpdate() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.UPDATE)
                .build();

        // when
        ChangeEventAction eventAction = ChangeEventUtil.toChangeEventAction(auditEntry);

        // then
        assertThat(eventAction).isNotNull();
        assertThat(eventAction).isEqualTo(ChangeEventAction.UPDATE);
    }

    @Test
    public void toChangeEventAction_forUpdateOn() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.UPDATE_ON)
                .build();

        // when
        ChangeEventAction eventAction = ChangeEventUtil.toChangeEventAction(auditEntry);

        // then
        assertThat(eventAction).isNotNull();
        assertThat(eventAction).isEqualTo(ChangeEventAction.UPDATE);
    }

    @Test
    public void toChangeEventAction_forDelete() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.DELETE)
                .build();

        // when
        ChangeEventAction eventAction = ChangeEventUtil.toChangeEventAction(auditEntry);

        // then
        assertThat(eventAction).isNotNull();
        assertThat(eventAction).isEqualTo(ChangeEventAction.DELETE);
    }

    @Test
    public void toChangeEventAction_forAddTo() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.ADD_TO)
                .build();

        // when
        ChangeEventAction eventAction = ChangeEventUtil.toChangeEventAction(auditEntry);

        // then
        assertThat(eventAction).isNotNull();
        assertThat(eventAction).isEqualTo(ChangeEventAction.ADD);
    }

    @Test
    public void toChangeEventAction_forRemoveFrom() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.REMOVE_FROM)
                .build();

        // when
        ChangeEventAction eventAction = ChangeEventUtil.toChangeEventAction(auditEntry);

        // then
        assertThat(eventAction).isNotNull();
        assertThat(eventAction).isEqualTo(ChangeEventAction.REMOVE);
    }

    @Test
    public void toChangeEventDetails_withoutContext() {
        // given
        Map<String, Object> before = Map.of("id", 1L, "name", "Team1");
        TeamEntity teamEntity = TeamEntity.builder().id(1L).name("Team1")
                .slackChannel("SlackChannel1")
                .emailList("EmailList")
                .description("Description")
                .build();

        // when
        ChangeEventDetails details = ChangeEventUtil.toChangeEventDetails(before, teamEntity);

        // then
        assertThat(details).isNotNull();
        assertThat(details.getContext()).isNull();
        assertThat(details.getOldValues().keySet()).isEqualTo(Set.of("id","name"));
        assertThat(details.getNewValues().keySet()).isEqualTo(Set.of("id","name","slackChannel", "emailList", "description"));
    }

    @Test
    public void reduce_deltasOnly() {
        // given
        Map<String, Object> before = Map.of("id", 1L, "name", "Team1");
        TeamEntity teamEntity = TeamEntity.builder().id(1L).name("Team1")
                .slackChannel("SlackChannel1")
                .emailList("EmailList")
                .description("Description")
                .build();

        // when
        ChangeEventDetails details = ChangeEventUtil.toChangeEventDetails(before, teamEntity);

        // then
        assertThat(details).isNotNull();
        assertThat(details.getContext()).isNull();
        assertThat(details.getOldValues().keySet()).isEqualTo(Set.of("id","name"));
        assertThat(details.getNewValues().keySet()).isEqualTo(Set.of("id","name","slackChannel", "emailList", "description"));

        ChangeEventDetails reduced = ChangeEventUtil.reduce(details, true, false);
        assertThat(reduced).isNotNull();
        assertThat(reduced.isDeltasOnly()).isTrue();
        assertThat(reduced.isMetadataExcluded()).isFalse();
        assertThat(reduced.getContext()).isNull();
        assertThat(reduced.getOldValues().keySet()).isEqualTo(Set.of("slackChannel", "emailList", "description"));
        assertThat(reduced.getNewValues().keySet()).isEqualTo(Set.of("slackChannel", "emailList", "description"));
    }

    @Test
    public void reduce_excludeMetadata() {
        // given
        TeamEntity teamEntity = TeamEntity.builder()
                .id(1L)
                .version(0)
                .type(TeamType.SHARED)
                .name("Team1")
                .nameNormalized("team1")
                .createUser("test")
                .updateUser("test")
                .createTimestamp(Instant.now())
                .updateTimestamp(Instant.now())
                .build();
        Map<String, Object> before = ChangeEventUtil.snapshot(teamEntity);
        teamEntity.setSlackChannel("SlackChannel1");
        teamEntity.setEmailList("EmailList");
        teamEntity.setDescription("Description");

        // when
        ChangeEventDetails details = ChangeEventUtil.toChangeEventDetails(before, teamEntity);

        // then
        assertThat(details).isNotNull();
        assertThat(details.getContext()).isNull();
        assertThat(details.getOldValues().keySet()).isEqualTo(Set.of("id","name","nameNormalized","type",
                "version", "updateUser", "createUser", "updateTimestamp", "createTimestamp"));
        assertThat(details.getNewValues().keySet()).isEqualTo(Set.of("id","name","nameNormalized","type",
                "slackChannel", "emailList", "description",
                "version", "updateUser", "createUser", "updateTimestamp", "createTimestamp"));

        ChangeEventDetails reduced = ChangeEventUtil.reduce(details, false, true);
        assertThat(reduced).isNotNull();
        assertThat(reduced.isMetadataExcluded()).isTrue();
        assertThat(reduced.isDeltasOnly()).isFalse();
        assertThat(reduced.getContext()).isNull();
        assertThat(reduced.getOldValues().keySet()).isEqualTo(Set.of("id","name","nameNormalized","type"));
        assertThat(reduced.getNewValues().keySet()).isEqualTo(Set.of("id","name","nameNormalized","type",
                "slackChannel", "emailList", "description"));
    }

    @Test
    public void toChangeEventDetails_withContext() {
        // given
        Map<String, Object> before = Map.of();
        TeamEntity teamEntity = TeamEntity.builder().build();
        Map<String, Object> context = Map.of();

        // when
        ChangeEventDetails details = ChangeEventUtil.toChangeEventDetails(before, teamEntity, context);

        // then
        assertThat(details).isNotNull();
        assertThat(details.getContext()).isEqualTo(context);
        assertThat(details.getOldValues()).isEqualTo(before);
    }

    @Test
    public void toChangeEntityId_forTeam() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.CREATE)
                .subjectEntity(AuditEntity.builder().id(1L).entityType(AuditEntityType.TEAM).build())
                .build();

        // when
        Long result = ChangeEventUtil.toChangeEntityId(auditEntry);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(1L);
    }

    @Test
    public void toChangeEntityId_forApplication() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.CREATE)
                .subjectEntity(AuditEntity.builder().id(1L).entityType(AuditEntityType.APPLICATION).build())
                .build();

        // when
        Long result = ChangeEventUtil.toChangeEntityId(auditEntry);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(1L);
    }

    @Test
    public void toChangeEntityId_forMember() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.CREATE)
                .subjectEntity(AuditEntity.builder().id(1L).entityType(AuditEntityType.MEMBER).build())
                .build();

        // when
        Long result = ChangeEventUtil.toChangeEntityId(auditEntry);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(1L);
    }

    @Test
    public void toChangeEntityId_forTeamMember() {
        // given
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.ADD_TO)
                .subjectEntity(AuditEntity.builder().id(10L).entityType(AuditEntityType.MEMBER).build())
                .targetEntity(AuditEntity.builder().id(1L).entityType(AuditEntityType.TEAM).build())
                .build();

        // when
        Long result = ChangeEventUtil.toChangeEntityId(auditEntry);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(1L);
    }

    @Test
    public void toChangeEntityId_forApiConsumer_usesApiId() {
        // given - API_CONSUMER event with API as target entity (mirrors TEAM_MEMBER pattern)
        AuditEntry auditEntry = AuditEntry.builder()
                .actionType(AuditAction.ADD_TO)
                .subjectEntity(AuditEntity.builder().id(200L).entityType(AuditEntityType.API_CONSUMER).build())
                .targetEntity(AuditEntity.builder().id(100L).entityType(AuditEntityType.API).build())
                .build();

        // when
        Long result = ChangeEventUtil.toChangeEntityId(auditEntry);

        // then - should return API ID (100), not consumer application ID (200)
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(100L);
    }

    @Test
    public void teamContext_forTeam() {
        // given
        Team team = Team.builder().id(1L).name("Team1")
                .type(TeamType.SHARED)
                .externalName("AppReg_Team1")
                .build();

        // when
        Map<String, Object> result = ChangeEventUtil.teamContext(team);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(1L);
        assertThat(result.get("name")).isEqualTo("Team1");
        assertThat(result.get("type")).isEqualTo(TeamType.SHARED);
        assertThat(result.get("externalName")).isEqualTo("AppReg_Team1");
    }

    @Test
    public void teamContext_forTeamView() {
        // given
        TeamView team = TeamView.builder().id(2L).name("Team2")
                .type(TeamType.PERSONAL)
                .build();

        // when
        Map<String, Object> result = ChangeEventUtil.teamContext(team);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(2L);
        assertThat(result.get("name")).isEqualTo("Team2");
        assertThat(result.get("type")).isEqualTo(TeamType.PERSONAL);
        assertThat(result.get("externalName")).isNull();
    }

    @Test
    public void teamContext_forMemberViewTeam() {
        // given
        MemberViewTeam team = MemberViewTeam.builder().id(2L).name("Team2")
                .type(TeamType.PERSONAL)
                .build();

        // when
        Map<String, Object> result = ChangeEventUtil.teamContext(team);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(2L);
        assertThat(result.get("name")).isEqualTo("Team2");
        assertThat(result.get("type")).isEqualTo(TeamType.PERSONAL);
        assertThat(result.get("externalName")).isNull();
    }

    @Test
    public void test_isnapshot_when_given_invalidInterface() {
        // given
        TeamEntity entity = TeamEntity.builder().build();

        // when
        assertThrows(ApiException.class, () -> ChangeEventUtil.isnapshot(entity, Object.class));
    }
}