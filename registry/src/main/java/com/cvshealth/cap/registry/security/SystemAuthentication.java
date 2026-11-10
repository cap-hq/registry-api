// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import com.cvshealth.cap.registry.model.audit.AuditActorType;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

@Getter
public class SystemAuthentication implements Authentication {
    private final AuditActorType auditActor;
    private boolean authenticated;

    public SystemAuthentication(AuditActorType auditActor) {
        if (auditActor == null || !auditActor.isSystemUser()) {
            throw new IllegalArgumentException("System authentication requires a system user");
        }
        this.auditActor = auditActor;
        setAuthenticated(true);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return auditActor;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean authenticated) throws IllegalArgumentException {
        this.authenticated = authenticated;
    }

    @Override
    public String getName() {
        return auditActor.name();
    }
}
