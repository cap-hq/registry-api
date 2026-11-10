// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.config.DatabaseAuditConfig;
import com.cvshealth.cap.registry.model.audit.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditServiceLogPublisher implements AuditService {
    private static final String LOG_TEMPLATE = "[AUDIT] ${actor} ${action} ${subjectEntity}";
    private static final String LOG_TEMPLATE_TARGET_ENTITY = "[AUDIT] ${actor} ${action} ${subjectEntity} ${targetAction} ${targetEntity}";
    private static final String LOG_TEMPLATE_SOURCE_TARGET_ENTITY = "[AUDIT] ${actor} ${action} for ${subjectEntity} from ${sourceEntity} to ${targetEntity}";
    private static final String ENTITY_LOG_TEMPLATE = "%s %s (%s)";

    private final DatabaseAuditConfig databaseAuditConfig;
    private final ChangeEventService changeEventService;

    @Override
    public void publish(AuditEntry auditEntry, ChangeEventDetails changeEventDetails) {
        log.info(buildLogEntry(auditEntry));

        if (databaseAuditConfig.isEnabled()) {
            publishToAuditLog(auditEntry, changeEventDetails);
        }
    }

    void publishToAuditLog(AuditEntry auditEntry, ChangeEventDetails changeEventDetails) {
        if (changeEventDetails == null) {
            return;
        }
        if (databaseAuditConfig.isDeltasOnly() || databaseAuditConfig.isExcludeMetadata()) {
            changeEventDetails = reduce(changeEventDetails, databaseAuditConfig.isDeltasOnly(), databaseAuditConfig.isExcludeMetadata());
        }
        ChangeEventType changeEventType = toChangeEventType(auditEntry);
        if (changeEventType != null) {
            changeEventService.saveChangeEvent(ChangeEventView.builder()
                    .entityId(toChangeEntityId(auditEntry))
                    .eventType(changeEventType)
                    .eventAction(toChangeEventAction(auditEntry))
                    .changes(changeEventDetails)
                    .build());
        }
    }

    String buildLogEntry(AuditEntry auditEntry) {
        Map<String, Object> params = new HashMap<>();
        params.put("actor", actor(auditEntry.getActor()));
        params.put("action", auditEntry.getActionType().getCompletedAction());
        params.put("subjectEntity", entity(auditEntry.getSubjectEntity()));

        String template = LOG_TEMPLATE;
        if (auditEntry.getSourceEntity() != null && auditEntry.getTargetEntity() != null) {
            template = LOG_TEMPLATE_SOURCE_TARGET_ENTITY;
            params.put("sourceEntity", entity(auditEntry.getSourceEntity()));
            params.put("targetEntity", entity(auditEntry.getTargetEntity()));
        } else if (auditEntry.getTargetEntity() != null) {
            template = LOG_TEMPLATE_TARGET_ENTITY;
            params.put("targetEntity", entity(auditEntry.getTargetEntity()));
            switch (auditEntry.getActionType()) {
                case ADD_TO -> params.put("targetAction", "to");
                case REMOVE_FROM -> params.put("targetAction", "from");
                case UPDATE, UPDATE_ON -> params.put("targetAction", "on");
                default -> throw new IllegalStateException("Unexpected value: " + auditEntry.getActionType());
            }
        }
        return StringSubstitutor.replace(template, params, "${", "}");
    }

    String actor(AuditActor actor) {
        if (actor.getId() == null) {
            return "user " + actor.getName();
        }
        return String.format(ENTITY_LOG_TEMPLATE, "user", actor.getName(), actor.getId());
    }

    String entity(AuditEntity entity) {
        return String.format(ENTITY_LOG_TEMPLATE, entity.getEntityType().getDisplayName(), entity.getName(),
                entity.getDisplayId() != null ? entity.getDisplayId() : entity.getId());
    }
}
