// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityHelper {
    public static String UNKNOWN_USER = "UNKNOWN_USER";

    public static String getCurrentUserExternalAlias() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null) {
            return token.getExternalAlias() != null ? token.getExternalAlias() : getCurrentUserExternalIdOrClientId();
        }
        return getCurrentUserExternalIdOrClientId();
    }


    public static String getCurrentUserExternalId() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null) {
            return token.getExternalId();
        }
        return UNKNOWN_USER;
    }

    public static String getCurrentUserExternalIdOrClientId() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null) {
            return token.isUser() ? token.getExternalId() : token.isNonUser() ? token.getClientId() : UNKNOWN_USER;
        }
        return UNKNOWN_USER;
    }

    public static String getCurrentUserEmail() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null) {
            return token.getEmail();
        }
        return UNKNOWN_USER;
    }

    public static boolean isCurrentUserClientId(String clientId) {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null && token.isNonUser()) {
            String authClientName = getClaim(token, "clientName");
            return clientId != null && (clientId.equalsIgnoreCase(token.getClientId()) || clientId.equalsIgnoreCase(authClientName));
        }
        return false;
    }

    private static String getClaim(JwtAuthenticationToken token, String claimName) {
        if (token != null && token.getToken() != null && token.getToken().getClaims() != null) {
            Object claimValue = token.getToken().getClaims().get(claimName);
            if (claimValue != null) {
                return claimValue.toString();
            }
        }
        return null;
    }

    public static String getBearerToken() {
        if (getCurrentJwtToken() != null) {
            return getCurrentJwtToken().getToken().getTokenValue();
        }
        return null;
    }

    // any service calls that include oauth bearer token Authorization header will use this security context
    private static JwtAuthenticationToken getCurrentJwtToken() {
        if (getAuthentication() != null && getAuthentication() instanceof JwtAuthenticationToken) {
            return (JwtAuthenticationToken) getAuthentication();
        }
        return null;
    }

    private static Authentication getAuthentication() {
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            return SecurityContextHolder.getContext().getAuthentication();
        }
        return null;
    }
}
