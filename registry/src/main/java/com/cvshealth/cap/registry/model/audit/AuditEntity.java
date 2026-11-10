// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditEntity {
    private Object id;
    private String name;
    private Object displayId;
    private AuditEntityType entityType;

    public static AuditEntity of(Object id, String name, AuditEntityType entityType) {
        return AuditEntity.builder().entityType(entityType).id(id).name(name).build();
    }

    public static AuditEntity of(Object id, String name, AuditEntityType entityType, Object externalId) {
        return AuditEntity.builder().entityType(entityType).id(id).name(name).displayId(externalId).build();
    }

    public static AuditEntity application(Object id, String name) {
        return AuditEntity.of(id, name, AuditEntityType.APPLICATION);
    }

    public static AuditEntity team(Object id, String name) {
        return AuditEntity.of(id, name, AuditEntityType.TEAM);
    }

    public static AuditEntity member(Object id, String name, Object externalId) {
        return AuditEntity.of(id, name, AuditEntityType.MEMBER, externalId);
    }

    public static AuditEntity authClient(Object id, String name) {
        return AuditEntity.of(id, name, AuditEntityType.AUTH_CLIENT);
    }

    public static AuditEntity businessLine(Object id, String name) {
        return AuditEntity.of(id, name, AuditEntityType.BUSINESS_LINE);
    }
}
