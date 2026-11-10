// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Map;

@Getter
@ToString
public class JwtAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {
    private static final String DISPLAY_NAME_CLAIM = "name";
    private static final String EXTERNAL_ID_CLAIM = "externalId"; // also, oid
    private static final String EXTERNAL_ALIAS_CLAIM = "externalAlias";
    private static final String EMAIL_CLAIM = "email";
    private static final String CLIENT_ID = "clientId";

    private final String displayName;
    private final String externalId;
    private final String externalAlias;
    private final String email;
    private final String clientId;

    protected JwtAuthenticationToken(Jwt jwt) {
        super(jwt);
        this.displayName = jwt.getClaimAsString(DISPLAY_NAME_CLAIM);
        this.externalId = jwt.getClaimAsString(EXTERNAL_ID_CLAIM);
        this.externalAlias = jwt.getClaimAsString(EXTERNAL_ALIAS_CLAIM);
        this.email = jwt.getClaimAsString(EMAIL_CLAIM);
        this.clientId = jwt.getClaimAsString(CLIENT_ID);
        this.setAuthenticated(true);
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return this.getToken().getClaims();
    }

    @Override
    public Object getPrincipal() {
        String principal = isNonUser() ? this.clientId : this.externalId;
        return principal == null ? "UNKNOWN" : principal;
    }

    public boolean isUser() {
        return !StringUtils.isEmpty(externalId);
    }

    public boolean isNonUser() {
        return StringUtils.isEmpty(externalId);
    }

}
