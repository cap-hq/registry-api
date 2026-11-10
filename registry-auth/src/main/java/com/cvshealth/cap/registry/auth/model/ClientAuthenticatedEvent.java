// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Data
public class ClientAuthenticatedEvent extends ApplicationEvent {

    private final String clientId;
    private final String clientName;
    private final String grantType;
    private final String principalName;
    private final String principalOid;
    private final String agent;
    private final String externalAlias;
    private final String eventAttributes;
    private final Instant authenticatedAt;
    private final boolean success;

    ClientAuthenticatedEvent(Object source, String clientId, String clientName,
                                    String grantType, Instant authenticatedAt, boolean success) {
        this(source, clientId, clientName, grantType, authenticatedAt, success, null, null, null, null, null);
    }

    ClientAuthenticatedEvent(Object source, String clientId, String clientName,
                                    String grantType, Instant authenticatedAt, boolean success,
                                    String principalName, String principalOid, String agent, String externalAlias, String eventAttributes) {
        super(source);
        this.clientId = clientId;
        this.clientName = clientName;
        this.grantType = grantType;
        this.authenticatedAt = authenticatedAt;
        this.success = success;
        this.principalName = principalName;
        this.principalOid = principalOid;
        this.agent = agent;
        this.externalAlias = externalAlias;
        this.eventAttributes = eventAttributes;
    }

    public static ClientAuthenticatedEvent success(Object source, String clientId, String clientName,
                                                   String grantType) {
        return new ClientAuthenticatedEvent(source, clientId, clientName, grantType, Instant.now(), true);
    }

    public static ClientAuthenticatedEvent success(Object source, String clientId, String clientName,
                                                   String grantType, String principalName, String principalOid, String agent, String externalAlias, String eventAttributes) {
        return new ClientAuthenticatedEvent(source, clientId, clientName, grantType, Instant.now(), true,
                principalName, principalOid, agent, externalAlias, eventAttributes);
    }

    public static ClientAuthenticatedEvent failure(Object source, String clientId, String clientName,
                                                   String grantType, String eventAttributes) {
        return new ClientAuthenticatedEvent(source, clientId, clientName, grantType, Instant.now(), false, null, null, null, null, eventAttributes);
    }

    public static ClientAuthenticatedEvent failure(Object source, String clientId, String clientName,
                                                   String grantType, String principalName, String principalOid, String agent, String eventAttributes) {
        return new ClientAuthenticatedEvent(source, clientId, clientName, grantType, Instant.now(), false,
                principalName, principalOid, agent, null, eventAttributes);
    }
}
