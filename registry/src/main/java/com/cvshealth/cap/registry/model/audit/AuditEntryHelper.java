// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import com.cvshealth.cap.registry.persistent.base.SecurityHelper;

public class AuditEntryHelper {

    public static AuditActor currentUser() {
        return SecurityHelper.getAuditActor();
    }

    public static AuditEntry memberAuditEntry(IdentifiableUser member, AuditAction action) {
        return AuditEntry.builder().actor(currentUser()).actionType(action).subjectEntity(memberAuditEntity(member)).build();
    }

    public static AuditEntry teamAuditEntry(IdentifiableEntity team, AuditAction action) {
        return AuditEntry.builder().actor(currentUser()).actionType(action).subjectEntity(teamAuditEntity(team)).build();
    }

    public static AuditEntry teamMemberAuditEntry(IdentifiableUser member, IdentifiableEntity team, AuditAction action) {
        return AuditEntry.builder().actor(currentUser()).actionType(action)
                .subjectEntity(memberAuditEntity(member)).targetEntity(teamAuditEntity(team)).build();
    }

    public static AuditEntry applicationAuditEntry(IdentifiableEntity application, AuditAction action) {
        AuditEntity subjectEntity = applicationAuditEntity(application);
        return AuditEntry.builder().actor(currentUser()).actionType(action).subjectEntity(subjectEntity).build();
    }

    public static AuditEntry authClientAuditEntry(IdentifiableEntity authClient, AuditAction action) {
        return AuditEntry.builder().actor(currentUser()).actionType(action).subjectEntity(authClientAuditEntity(authClient)).build();
    }

    public static AuditEntry applicationTransferAuditEntry(IdentifiableEntity application, IdentifiableEntity sourceTeam,
                                                           IdentifiableEntity targetTeam, AuditAction action) {
        AuditEntity subjectEntity = applicationAuditEntity(application);
        AuditEntity sourceEntity = teamAuditEntity(sourceTeam);
        AuditEntity targetEntity = teamAuditEntity(targetTeam);
        return AuditEntry.builder().actor(currentUser()).actionType(action).subjectEntity(subjectEntity)
                .sourceEntity(sourceEntity).targetEntity(targetEntity).build();
    }

    public static AuditEntity applicationAuditEntity(IdentifiableEntity application) {
        return AuditEntity.application(application.getId(), application.getName());
    }

    public static AuditEntity teamAuditEntity(IdentifiableEntity team) {
        return AuditEntity.team(team.getId(), team.getName());
    }

    public static AuditEntity memberAuditEntity(IdentifiableUser member) {
        return AuditEntity.member(member.getId(), member.getExternalAlias(), member.getExternalId());
    }

    public static AuditEntity authClientAuditEntity(IdentifiableEntity authClient) {
        return AuditEntity.authClient(authClient.getId(), authClient.getName());
    }

    public static AuditEntry businessLineAuditEntry(IdentifiableEntity businessLine, AuditAction action) {
        return AuditEntry.builder().actor(currentUser()).actionType(action).subjectEntity(businessLineAuditEntity(businessLine)).build();
    }

    public static AuditEntity businessLineAuditEntity(IdentifiableEntity businessLine) {
        return AuditEntity.businessLine(businessLine.getId(), businessLine.getName());
    }
}
