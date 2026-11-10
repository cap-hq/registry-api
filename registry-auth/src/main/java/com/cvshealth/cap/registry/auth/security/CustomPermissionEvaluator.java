// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import com.cvshealth.cap.registry.auth.model.AuthClient;
import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.Member;
import com.cvshealth.cap.registry.auth.model.Team;
import com.cvshealth.cap.registry.auth.service.RegistryApiService;
import com.cvshealth.cap.registry.auth.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomPermissionEvaluator implements PermissionEvaluator {
    private final RegistryApiService registryApiService;
    private final ClientService clientService;

    @Value("${app.admin.authorized-teams}")
    private List<String> adminAuthorizedTeams;

    private static final String ADMIN = "Admin";
    private static final List<String> ADMIN_QUERY_ROLES = List.of("OWNER", "MEMBER");
    private static final List<String> ADMIN_MUTATION_ROLES = List.of("OWNER");

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        throw new UnsupportedOperationException("hasPermission(Authentication, Object, Object) not currently supported by %s".formatted(CustomPermissionEvaluator.class.getSimpleName()));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable target, String targetType, Object permission) {
        var idKey = getEffectiveTargetId(target);
        if (idKey == null && !ADMIN.equals(targetType)) {
            log.warn("hasPermission called with unsupported target type {}: denying", target == null ? "null" : target);
            return false;
        }
        log.info("checking permission for %s %s %s".formatted(idKey, targetType, permission));

        if (authentication instanceof ApiSecretAuthenticationFilter.ApiSecretAuthentication) {
            return ADMIN.equals(targetType);
        }

        String externalIdOrNonUserId;
        boolean isNonUser;
        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuthToken) {
            JwtAuthenticationToken registryToken = new JwtAuthenticationToken(jwtAuthToken.getToken());
            isNonUser = registryToken.isNonUser();
            externalIdOrNonUserId = isNonUser ? registryToken.getClientId() : registryToken.getExternalId();

            if (ADMIN.equals(targetType)) {
                return authorizeAdminPermission(idKey, externalIdOrNonUserId, isNonUser, (String) permission);
            }
        }
        return false;
    }

    private <T, E> boolean authorizeAdminPermission(T idKey, String memberIdOrNonUserId, boolean isNonUser, String permission) {
        List<String> authorizedRoles = "query".equals(permission) ? ADMIN_QUERY_ROLES : ADMIN_MUTATION_ROLES;
        return getUserTeams(memberIdOrNonUserId, isNonUser).stream()
                .filter(team -> authorizedRoles.contains(team.getRole()))
                .anyMatch(t -> adminAuthorizedTeams.contains(t.getName()));
    }

    private List<Team> getUserTeams(String externalIdOrNonUserId, boolean isNonUser) {
        if (isNonUser) {
            Client client = clientService.getClient(externalIdOrNonUserId);
            if (client != null) {
                AuthClient authClient = registryApiService.getAuthClient(
                        clientService.getBaseClientId(client), clientService.getClientVersion(client), false);
                if (authClient != null) {
                    return authClient.getTeams();
                }
            }
        } else {
            Member member = registryApiService.getMember(externalIdOrNonUserId, true);
            if (member != null) {
                return member.getTeams();
            }
        }
        return Collections.emptyList();
    }

    private Serializable getEffectiveTargetId(Serializable target) {
        if ((target instanceof Long) || (target instanceof String)) {
            return target;
        }
        return null;
    }
}
