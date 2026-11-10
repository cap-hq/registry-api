// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Slf4j
public class AuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    protected AuthenticationFilter(AuthenticationManager authenticationManager) {
        super(AnyRequestMatcher.INSTANCE, authenticationManager);
        this.setContinueChainBeforeSuccessfulAuthentication(true);
        this.setAuthenticationSuccessHandler((request, response, authentication) -> {
        });
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        String externalUserId = request.getHeader("X-External-User-Id");
        log.debug(String.format("X-External-User-Id: %s", externalUserId));

        if (externalUserId != null && !externalUserId.isEmpty()) {
            // Create our Authentication and let Spring know about it
            Authentication auth = new AuthenticationToken(externalUserId);
            SecurityContextHolder.getContext().setAuthentication(auth);

            return this.getAuthenticationManager().authenticate(auth);
        }

        throw new BadCredentialsException("missing X-External-User-Id header");
    }

    @Override
    protected boolean requiresAuthentication(HttpServletRequest request, HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null || !auth.isAuthenticated()) && super.requiresAuthentication(request, response);
    }
}
