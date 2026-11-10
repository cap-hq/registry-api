// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditActor {
    private Object id;
    private String name;
    private AuditActorType actorType;

    public static AuditActor user(String externalId, String externalAlias) {
        return AuditActor.builder().actorType(AuditActorType.USER).id(externalId).name(externalAlias).build();
    }

    public static AuditActor authClient(String clientId) {
        return AuditActor.builder().actorType(AuditActorType.AUTH_CLIENT).name(AuditActorType.AUTH_CLIENT.name()).id(clientId).build();
    }

    public static AuditActor system(AuditActorType actorType) {
        return AuditActor.builder().actorType(actorType).name(actorType.name()).build();
    }
}
