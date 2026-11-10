// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent.base;

import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.ModelPair;
import com.cvshealth.cap.registry.model.audit.AuditActor;
import com.cvshealth.cap.registry.model.audit.AuditActorType;
import com.cvshealth.cap.registry.security.JwtAuthenticationToken;
import com.cvshealth.cap.registry.security.SystemAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Stream;

public class SecurityHelper {
    public static String UNKNOWN_USER = "UNKNOWN_USER";
    public static String UNKNOWN_TEAMS = "UNKNOWN_TEAMS";

    public static String getCurrentUserExternalAlias() {
        Member member = getCurrentMember();
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (member != null) {
            return member.getExternalAlias();
        } else if (token != null) {
            return token.getExternalAlias() != null ? token.getExternalAlias() : getCurrentUserExternalIdOrClientId();
        }
        return getCurrentUserExternalIdOrClientId();
    }


    public static String getCurrentUserExternalId() {
        Member member = getCurrentMember();
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (member != null) {
            return member.getExternalId();
        } else if (token != null) {
            return token.getExternalId();
        }
        return UNKNOWN_USER;
    }

    public static AuditActor getAuditActor() {
        Member member = getCurrentMember();
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (member != null) {
            return AuditActor.user(member.getExternalId(), member.getExternalAlias());
        } else if (token != null) {
            if (token.isUser()) {
                // Note: New user externalAlias is not set until after initial member creation in Registry
                // Applies only to the user token during user self creation in Registry
                String externalAlias = token.getExternalAlias() != null ? token.getExternalAlias() : "NEW_USER";
                return AuditActor.user(token.getExternalId(), externalAlias);
            } if (token.isNonUser()) {
                return AuditActor.authClient(token.getClientId());
            }
        } else {
            Authentication authentication = getAuthentication();
            if (getAuthentication() != null && authentication instanceof SystemAuthentication) {
                return AuditActor.system((AuditActorType) authentication.getPrincipal());
            }
        }
        return AuditActor.system(AuditActorType.UNKNOWN_USER);
    }

    public static String getCurrentUserExternalIdOrClientId() {
        Member member = getCurrentMember();
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (member != null) {
            return member.getExternalId();
        } else if (token != null) {
            return token.isUser() ? token.getExternalId() : token.isNonUser() ? token.getClientId() : UNKNOWN_USER;
        } else {
            Authentication authentication = getAuthentication();
            if (getAuthentication() != null && authentication instanceof SystemAuthentication) {
                return authentication.getName();
            }
        }
        return UNKNOWN_USER;
    }

    public static String getCurrentUserEmail() {
        Member member = getCurrentMember();
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (member != null) {
            return member.getEmail();
        } else if (token != null) {
            return token.getEmail();
        }
        return UNKNOWN_USER;
    }

    public static String getBearerToken() {
         if (getCurrentJwtToken() != null) {
            return getCurrentJwtToken().getToken().getTokenValue();
        }
        return null;
    }

    public static ModelPair<String, String> getAuthClientIdAndName() {
        JwtAuthenticationToken jwtAuthenticationToken = getCurrentJwtToken();
        if (jwtAuthenticationToken != null) {
            String authClientName = (String) jwtAuthenticationToken.getToken().getClaims().get("clientName");
            return new ModelPair<>(jwtAuthenticationToken.getClientId(), authClientName);
        }
        return null;
    }

    public static List<String> getClientTeams() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null) {
            List<String> clientTeams = token.getToken().getClaimAsStringList("team_member");
            return clientTeams != null ? clientTeams : List.of();
        }
        return List.of();
    }

    public static List<String> getUserTeams() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        if (token != null) {
            List<String> owners  = token.getToken().getClaimAsStringList("team_owner");
            List<String> members = token.getToken().getClaimAsStringList("team_member");
            return Stream.of(
                    owners  != null ? owners  : List.<String>of(),
                    members != null ? members : List.<String>of()
            ).flatMap(List::stream).toList();
        }
        // No JWT — header-auth (X-External-User-Id) flow or unauthenticated.
        if (getCurrentMember() != null) {
            return List.of(UNKNOWN_TEAMS);
        }
        return List.of();
    }

    public static boolean isNonUserJwt() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        return token != null && token.isNonUser();
    }

    public static String getCallingClientId() {
        JwtAuthenticationToken token = getCurrentJwtToken();
        return token == null ? UNKNOWN_USER : token.getClientId();
    }

    public static void setRegistryUser() {
        SystemAuthentication auth = new SystemAuthentication(AuditActorType.APP_REGISTRY);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // any service calls that include oauth bearer token Authorization header will use this security context
    private static JwtAuthenticationToken getCurrentJwtToken() {
        if (getAuthentication() != null && getAuthentication() instanceof JwtAuthenticationToken) {
            return (JwtAuthenticationToken) getAuthentication();
        }
        return null;
    }

    // local/test service calls that include the X-External-User-Id header will use this security context
    private static Member getCurrentMember() {
        if (getAuthentication() != null && getAuthentication().getDetails() instanceof Member) {
            return (Member) getAuthentication().getDetails();
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
