// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

/**
 * Test-only extension of {@link AuthenticationFilter} that supports authenticating as a
 * non-user (service/client) by providing a client ID via the {@code X-Auth-Client-Id} request
 * header. A synthetic JWT with {@code alg=none} is created and placed in the security context.
 *
 * <p>This filter must never be used outside of the test context. It is wired only through
 * {@link TestSecurityConfiguration}, which is loaded exclusively when the {@code test} profile
 * is active.
 */
class TestAuthenticationFilter extends AuthenticationFilter {

    TestAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        String authClientId = request.getHeader("X-Auth-Client-Id");
        if (authClientId != null && !authClientId.isEmpty()) {
            Instant now = Instant.now();
            Jwt jwt = new Jwt(
                    authClientId,
                    now,
                    now.plusSeconds(3600),
                    Map.of("alg", "none"),
                    Map.of("clientId", authClientId));
            JwtAuthenticationToken jwtToken = new JwtAuthenticationToken(jwt);
            SecurityContextHolder.getContext().setAuthentication(jwtToken);
            return jwtToken;
        }

        return super.attemptAuthentication(request, response);
    }
}
