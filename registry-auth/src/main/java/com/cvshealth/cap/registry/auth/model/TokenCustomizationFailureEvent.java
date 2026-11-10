// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@Getter
public class TokenCustomizationFailureEvent extends ApplicationEvent {

    private final RegisteredClient registeredClient;
    private final AuthorizationGrantType grantType;
    private final String principalName;
    private final String principalOid;
    private final String agent;
    private final CustomScopeType scopeType;
    private final Exception cause;

    public TokenCustomizationFailureEvent(Object source, RegisteredClient registeredClient, AuthorizationGrantType grantType,
                                          String principalName, String principalOid, String agent, CustomScopeType scopeType, Exception cause) {
        super(source);
        this.registeredClient = registeredClient;
        this.grantType = grantType;
        this.principalName = principalName;
        this.principalOid = principalOid;
        this.agent = agent;
        this.scopeType = scopeType;
        this.cause = cause;
    }
}
