// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Validate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;

public class ApiSecretAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_SECRET_HEADER = "X-Api-Secret";

    private final String apiSecret;

    public ApiSecretAuthenticationFilter(String apiSecret) {
        // a blank secret would authenticate requests that send an empty X-Api-Secret header
        Validate.notBlank(apiSecret, "registry-auth.api-secret config is required");
        this.apiSecret = apiSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String secret = request.getHeader(API_SECRET_HEADER);

        if (secret == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Constant-time comparison prevents timing attacks that could reveal the secret character-by-character
        if (!MessageDigest.isEqual(apiSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API secret");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new ApiSecretAuthentication());
        filterChain.doFilter(request, response);
    }

    static class ApiSecretAuthentication implements Authentication {
        private boolean authenticated = true;

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
            return "registry";
        }

        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
            this.authenticated = isAuthenticated;
        }

        @Override
        public String getName() {
            return "registry";
        }
    }
}
