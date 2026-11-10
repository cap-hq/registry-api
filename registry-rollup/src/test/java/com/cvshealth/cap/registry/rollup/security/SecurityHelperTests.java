// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityHelperTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void isCurrentUserClientId_whenNoAuthentication_returnsFalse() {
        assertThat(SecurityHelper.isCurrentUserClientId("some-client-id")).isFalse();
    }

    @Test
    public void isCurrentUserClientId_whenUserToken_returnsFalse() {
        // user tokens have externalId set → isNonUser() is false
        setSecurityContext(buildJwt(Map.of("clientId", "some-client-id", "externalId", "C123456")));
        assertThat(SecurityHelper.isCurrentUserClientId("some-client-id")).isFalse();
    }

    @Test
    public void isCurrentUserClientId_whenNullClientIdArg_returnsFalse() {
        setSecurityContext(buildJwt(Map.of("clientId", "some-client-id")));
        assertThat(SecurityHelper.isCurrentUserClientId(null)).isFalse();
    }

    @Test
    public void isCurrentUserClientId_whenClientIdMatches_returnsTrue() {
        setSecurityContext(buildJwt(Map.of("clientId", "registry-rollup-jobs")));
        assertThat(SecurityHelper.isCurrentUserClientId("registry-rollup-jobs")).isTrue();
    }

    @Test
    public void isCurrentUserClientId_whenClientIdMatchesCaseInsensitive_returnsTrue() {
        setSecurityContext(buildJwt(Map.of("clientId", "REGISTRY-ROLLUP-JOBS")));
        assertThat(SecurityHelper.isCurrentUserClientId("registry-rollup-jobs")).isTrue();
    }

    @Test
    public void isCurrentUserClientId_whenClientIdDoesNotMatch_returnsFalse() {
        setSecurityContext(buildJwt(Map.of("clientId", "other-client")));
        assertThat(SecurityHelper.isCurrentUserClientId("registry-rollup-jobs")).isFalse();
    }

    @Test
    public void isCurrentUserClientId_whenClientNameClaimMatches_returnsTrue() {
        setSecurityContext(buildJwt(Map.of("clientId", "different-id", "clientName", "registry-rollup-jobs")));
        assertThat(SecurityHelper.isCurrentUserClientId("registry-rollup-jobs")).isTrue();
    }

    private void setSecurityContext(Jwt jwt) {
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private Jwt buildJwt(Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
