// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.config.DatabaseAuditConfig;
import com.cvshealth.cap.registry.model.audit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditServiceLogPublisherTests {

    private static final AuditActor userActor = AuditActor.user("actor-external-id", "actor-external-alias");
    private static final AuditActor authClientActor = AuditActor.authClient("auth-client-id");
    private static final AuditActor systemActor = AuditActor.system(AuditActorType.APP_REGISTRY);
    private static final AuditEntity team = AuditEntity.builder().entityType(AuditEntityType.TEAM).name("team-1-name").id(111111L).build();
    private static final AuditEntity team2 = AuditEntity.builder().entityType(AuditEntityType.TEAM).name("team-2-name").id(222222L).build();
    private static final AuditEntity application = AuditEntity.builder().entityType(AuditEntityType.APPLICATION).name("app-1-name").id(555555L).build();
    private static final AuditEntity member = AuditEntity.builder().entityType(AuditEntityType.MEMBER).name("member-1-external-alias").id("member-1-external-id").build();
    private static final AuditEntity api = AuditEntity.builder().entityType(AuditEntityType.API).name("api-1-name").id(777777L).build();
    private static final AuditEntity apiConsumer = AuditEntity.builder().entityType(AuditEntityType.API_CONSUMER).name("api-consumer-1-name").id(888888L).build();
    private static final AuditEntity businessLine = AuditEntity.builder().entityType(AuditEntityType.BUSINESS_LINE).name("business-line-1-name").id(999999L).build();
    private static final AuditEntity entityWithDisplayId = AuditEntity.builder().entityType(AuditEntityType.APPLICATION).name("app-with-display").id(123456L).displayId("APP-EXT-ID").build();

    @InjectMocks
    private AuditServiceLogPublisher auditService;

    @Mock
    private ChangeEventService changeEventService;
    @Mock
    private DatabaseAuditConfig databaseAuditConfig;

    // ==================== buildLogEntry tests ====================

    @Test
    public void testBuildLogEntry_create() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) created team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_update() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.UPDATE).subjectEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) updated team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_delete() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.DELETE).subjectEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) deleted team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_reset() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.RESET).subjectEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) reset team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_addApplicationToTeam() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.ADD_TO)
                .subjectEntity(application).targetEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) added application app-1-name (555555) to team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_removeApplicationFromTeam() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.REMOVE_FROM)
                .subjectEntity(application).targetEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) removed application app-1-name (555555) from team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_addMemberToTeam() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.ADD_TO)
                .subjectEntity(member).targetEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) added member member-1-external-alias (member-1-external-id) to team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_removeMemberFromTeam() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.REMOVE_FROM)
                .subjectEntity(member).targetEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) removed member member-1-external-alias (member-1-external-id) from team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_updateTeamMember() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.UPDATE_ON)
                .subjectEntity(member).targetEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) updated member member-1-external-alias (member-1-external-id) on team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_create_applicationTransfer() {
        AuditEntry auditEntry = AuditEntry.builder().actionType(AuditAction.CREATE_TRANSFER).actor(userActor)
                .subjectEntity(application).sourceEntity(team).targetEntity(team2).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) created transfer for application app-1-name (555555) from team team-1-name (111111) to team team-2-name (222222)");
    }

    @Test
    public void testBuildLogEntry_update_applicationTransfer() {
        AuditEntry auditEntry = AuditEntry.builder().actionType(AuditAction.UPDATE_TRANSFER).actor(userActor)
                .subjectEntity(application).sourceEntity(team).targetEntity(team2).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) updated transfer for application app-1-name (555555) from team team-1-name (111111) to team team-2-name (222222)");
    }

    @Test
    public void testBuildLogEntry_create_byAuthClientActor() {
        AuditEntry auditEntry = AuditEntry.builder().actor(authClientActor).actionType(AuditAction.CREATE).subjectEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user AUTH_CLIENT (auth-client-id) created team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_create_bySystem() {
        AuditEntry auditEntry = AuditEntry.builder().actor(systemActor).actionType(AuditAction.CREATE).subjectEntity(team).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user APP_REGISTRY created team team-1-name (111111)");
    }

    @Test
    public void testBuildLogEntry_withDisplayId() {
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(entityWithDisplayId).build();
        String logEntry = auditService.buildLogEntry(auditEntry);
        assertThat(logEntry).isEqualTo("[AUDIT] user actor-external-alias (actor-external-id) created application app-with-display (APP-EXT-ID)");
    }

    @Test
    public void testBuildLogEntry_invalidActionTypeWithTargetEntity() {
        // CREATE action with targetEntity should throw IllegalStateException
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE)
                .subjectEntity(application).targetEntity(team).build();
        assertThatThrownBy(() -> auditService.buildLogEntry(auditEntry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected value: CREATE");
    }

    // ==================== publish tests ====================

    @Test
    public void testPublish_auditDisabled() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(team).build();
        when(databaseAuditConfig.isEnabled()).thenReturn(false);

        // when
        auditService.publish(auditEntry);

        // then
        verifyNoInteractions(changeEventService);
    }

    @Test
    public void testPublish_auditEnabledNoChangeDetails() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(team).build();
        when(databaseAuditConfig.isEnabled()).thenReturn(true);

        // when
        auditService.publish(auditEntry, null);

        // then - null changeEventDetails should skip saving
        verifyNoInteractions(changeEventService);
    }

    @Test
    public void testPublish_auditEnabledWithChangeDetails() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(team).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(new HashMap<>())
                .newValues(Map.of("name", "team-1-name"))
                .build();
        when(databaseAuditConfig.isEnabled()).thenReturn(true);
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        ChangeEventView view = ChangeEventView.builder().build();
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(view);

        // when
        auditService.publish(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.TEAM);
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.CREATE);
        assertThat(captured.getEntityId()).isEqualTo(111111L);
        assertThat(captured.getChanges()).isSameAs(changeDetails);
    }

    // ==================== publishToAuditLog tests ====================

    @Test
    public void testPublishToAuditLog_deltasOnly() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.UPDATE).subjectEntity(team).build();
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("name", "old-name");
        oldValues.put("description", "same-desc");
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("name", "new-name");
        newValues.put("description", "same-desc");
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(oldValues)
                .newValues(newValues)
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(true);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then - deltas should only contain changed values
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getChanges().isDeltasOnly()).isTrue();
        assertThat(captured.getChanges().getOldValues()).containsKey("name");
        assertThat(captured.getChanges().getOldValues()).doesNotContainKey("description");
        assertThat(captured.getChanges().getNewValues()).containsKey("name");
        assertThat(captured.getChanges().getNewValues()).doesNotContainKey("description");
    }

    @Test
    public void testPublishToAuditLog_excludeMetadata() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.UPDATE).subjectEntity(team).build();
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("name", "old-name");
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("name", "new-name");
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(oldValues)
                .newValues(newValues)
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(true);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getChanges().isMetadataExcluded()).isTrue();
    }

    @Test
    public void testPublishToAuditLog_apiEvent() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(api).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(new HashMap<>())
                .newValues(Map.of("name", "api-1-name"))
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.API);
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.CREATE);
        assertThat(captured.getEntityId()).isEqualTo(777777L);
        assertThat(captured.getChanges()).isSameAs(changeDetails);
    }

    @Test
    public void testPublishToAuditLog_apiConsumerEvent() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.ADD_TO)
                .subjectEntity(apiConsumer).targetEntity(api).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(new HashMap<>())
                .newValues(Map.of("name", "api-consumer-1-name"))
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.API_CONSUMER);
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.ADD);
        assertThat(captured.getEntityId()).isEqualTo(777777L); // API ID, not consumer application ID
        assertThat(captured.getChanges()).isSameAs(changeDetails);
    }

    @Test
    public void testPublishToAuditLog_applicationEvent() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.UPDATE).subjectEntity(application).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(Map.of("name", "old-app-name"))
                .newValues(Map.of("name", "new-app-name"))
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.APPLICATION);
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.UPDATE);
        assertThat(captured.getEntityId()).isEqualTo(555555L);
    }

    @Test
    public void testPublishToAuditLog_businessLineEvent() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(businessLine).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(new HashMap<>())
                .newValues(Map.of("name", "business-line-1-name"))
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.BUSINESS_LINE);
        assertThat(captured.getEntityId()).isEqualTo(999999L);
    }

    @Test
    public void testPublishToAuditLog_memberEvent_noTarget() {
        // given
        AuditEntity standaloneMembe = AuditEntity.builder().entityType(AuditEntityType.MEMBER).name("member-name").id(333333L).build();
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.UPDATE).subjectEntity(standaloneMembe).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(Map.of("role", "old-role"))
                .newValues(Map.of("role", "new-role"))
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then - standalone member event
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.MEMBER);
        assertThat(captured.getEntityId()).isEqualTo(333333L);
    }

    @Test
    public void testPublishToAuditLog_teamMemberEvent() {
        // given
        AuditEntity teamMember = AuditEntity.builder().entityType(AuditEntityType.MEMBER).name("member-name").id(333333L).build();
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.ADD_TO)
                .subjectEntity(teamMember).targetEntity(team).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(new HashMap<>())
                .newValues(Map.of("memberId", 333333L))
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then - team member event uses team ID as entity ID
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(ChangeEventType.TEAM_MEMBER);
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.ADD);
        assertThat(captured.getEntityId()).isEqualTo(111111L); // uses team ID
    }

    @Test
    public void testPublishToAuditLog_deleteAction() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.DELETE).subjectEntity(team).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(Map.of("name", "team-1-name"))
                .newValues(new HashMap<>())
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.DELETE);
    }

    @Test
    public void testPublishToAuditLog_removeFromAction() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.REMOVE_FROM)
                .subjectEntity(application).targetEntity(team).build();
        ChangeEventDetails changeDetails = ChangeEventDetails.builder()
                .oldValues(Map.of("teamId", 111111L))
                .newValues(new HashMap<>())
                .build();
        when(databaseAuditConfig.isDeltasOnly()).thenReturn(false);
        when(databaseAuditConfig.isExcludeMetadata()).thenReturn(false);
        when(changeEventService.saveChangeEvent(any(ChangeEventView.class))).thenReturn(ChangeEventView.builder().build());

        // when
        auditService.publishToAuditLog(auditEntry, changeDetails);

        // then
        ArgumentCaptor<ChangeEventView> captor = ArgumentCaptor.forClass(ChangeEventView.class);
        verify(changeEventService).saveChangeEvent(captor.capture());
        ChangeEventView captured = captor.getValue();
        assertThat(captured.getEventAction()).isEqualTo(ChangeEventAction.REMOVE);
    }

    @Test
    public void testPublishToAuditLog_nullChangeDetails() {
        // given
        AuditEntry auditEntry = AuditEntry.builder().actor(userActor).actionType(AuditAction.CREATE).subjectEntity(team).build();

        // when
        auditService.publishToAuditLog(auditEntry, null);

        // then - null changeEventDetails should return early
        verifyNoInteractions(changeEventService);
    }

    // ==================== actor method tests ====================

    @Test
    public void testActor_withId() {
        String actorString = auditService.actor(userActor);
        assertThat(actorString).isEqualTo("user actor-external-alias (actor-external-id)");
    }

    @Test
    public void testActor_withoutId() {
        String actorString = auditService.actor(systemActor);
        assertThat(actorString).isEqualTo("user APP_REGISTRY");
    }

    // ==================== entity method tests ====================

    @Test
    public void testEntity_withId() {
        String entityString = auditService.entity(team);
        assertThat(entityString).isEqualTo("team team-1-name (111111)");
    }

    @Test
    public void testEntity_withDisplayId() {
        String entityString = auditService.entity(entityWithDisplayId);
        assertThat(entityString).isEqualTo("application app-with-display (APP-EXT-ID)");
    }

    @Test
    public void testEntity_apiEntityType() {
        String entityString = auditService.entity(api);
        assertThat(entityString).isEqualTo("api api-1-name (777777)");
    }

    @Test
    public void testEntity_businessLineEntityType() {
        String entityString = auditService.entity(businessLine);
        assertThat(entityString).isEqualTo("line of business business-line-1-name (999999)");
    }
}
