// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

@Getter
public class AuthenticationToken extends AbstractAuthenticationToken {
    private final String externalId;

    public AuthenticationToken(String externalId) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.externalId = externalId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return this.externalId;
    }
}
