// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.RegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomPermissionEvaluator implements PermissionEvaluator {
    private final RegistryService registryService;

    private static final String PERMISSION_CREATE = "create";
    private static final String PERMISSION_UPDATE = "update";
    private static final String PERMISSION_DELETE = "delete";
    private static final String PERMISSION_MODIFY_COMPLIANCE_TYPE = "modify-compliance-type";
    private static final String PERMISSION_MODIFY_INFORMATION_CLASSIFICATION = "modify-information-classification";
    private static final String PERMISSION_MODIFY_PRIORITY = "modify-priority";
    private static final String PERMISSION_MODIFY_APP_EXTERNAL_ID = "modify-app-external-id";
    private static final String PERMISSION_RUN = "run";
    private static final String PERMISSION_MODIFY_EMAIL_LIST = "modify_email_list";
    private static final String PERMISSION_MODIFY_SLACK_CHANNEL = "modify-slack-channel";
    private static final String PERMISSION_SYNC_CHANGE_EVENT = "sync-change-event";
    private static final String PERMISSION_MODIFY_AI_ENABLED = "modify-ai-enabled";

    private static final String TARGET_APPLICATION = "Application";
    private static final String TARGET_TEAM = "Team";
    private static final String TARGET_TEAM_MEMBER = "TeamMember";
    private static final String TARGET_MEMBER = "Member";
    private static final String TARGET_APPLICATION_TRANSFER = "ApplicationTransfer";
    private static final String TARGET_AUTH_CLIENT = "AuthClient";
    private static final String TARGET_ADMIN_TASK = "AdminTask";
    private static final String TARGET_CHANGE_EVENT = "ChangeEvent";

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        throw new UnsupportedOperationException("hasPermission(Authentication, Object, Object) not currently supported by %s".formatted(CustomPermissionEvaluator.class.getSimpleName()));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable target, String targetType, Object permission) {
        // incoming targetId could be a Long or String, or AuthClientRequest
        var idKey = getEffectiveTargetId(target, targetType);
        log.info("checking permission for %s %s %s".formatted(idKey, targetType, permission));

        String externalIdOrNonUserId = (String) authentication.getPrincipal();
        boolean isNonUser = (authentication instanceof JwtAuthenticationToken) && ((JwtAuthenticationToken)authentication).isNonUser();

        // incoming auth token could be a member or non-user JWT
        var memberIdOrNonUserId = isNonUser ? externalIdOrNonUserId : registryService.getMemberByExternalId(externalIdOrNonUserId).getId();

        boolean isAuthorized = false;

        if (permission instanceof String) {
            validateAuthorizedUser(isNonUser);

            if (PERMISSION_CREATE.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeCreatePermission(targetType, idKey, memberIdOrNonUserId, isNonUser, target);
            } else if (PERMISSION_UPDATE.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeUpdatePermission(targetType, idKey, memberIdOrNonUserId, isNonUser, target);
            } else if (PERMISSION_DELETE.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeDeletePermission(targetType, idKey, memberIdOrNonUserId, isNonUser);
            } else if (PERMISSION_MODIFY_COMPLIANCE_TYPE.equalsIgnoreCase((String) permission)
                    || PERMISSION_MODIFY_INFORMATION_CLASSIFICATION.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeModifyApplicationOwnerFieldPermission(targetType, idKey, memberIdOrNonUserId);
            } else if (PERMISSION_MODIFY_PRIORITY.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeModifyApplicationPriorityPermission(targetType, idKey, memberIdOrNonUserId, isNonUser);
            } else if (PERMISSION_MODIFY_APP_EXTERNAL_ID.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeModifyApplicationExternalIdPermission(targetType, idKey, memberIdOrNonUserId);
            } else if (PERMISSION_RUN.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeRunJobPermission(targetType);
            } else if (PERMISSION_MODIFY_EMAIL_LIST.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeModifyAEmailListPermission(targetType, idKey, memberIdOrNonUserId, isNonUser);
            } else if (PERMISSION_MODIFY_SLACK_CHANNEL.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeModifySlackChannelPermission(targetType, idKey, memberIdOrNonUserId, isNonUser);
            } else if (PERMISSION_SYNC_CHANGE_EVENT.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeSyncChangeEventPermission(targetType, idKey, memberIdOrNonUserId, isNonUser);
            } else if (PERMISSION_MODIFY_AI_ENABLED.equalsIgnoreCase((String) permission)) {
                isAuthorized = authorizeModifyAiEnabledPermission(targetType, idKey, memberIdOrNonUserId, isNonUser);
            }

        }

        return isAuthorized;
    }

    private Serializable getEffectiveTargetId(Serializable target, String targetType) {
        if ((target instanceof Long) || (target instanceof String)) {
            return target;
        }
        if (target instanceof AuthClientRequest authClientRequest && TARGET_AUTH_CLIENT.equalsIgnoreCase(targetType)) {
            return authClientRequest.getTeamName();
        }
        return null;
    }

    /**
     * CREATE
     * <ul>
     * <li>APPLICATION          - Team OWNER & MEMBER role can create applications
     * <li>TEAM                 - Any user can add a team - creating team member is assigned OWNER (non-user CANNOT create teams)
     * <li>TEAM-MEMBER          - Team OWNER role can add a team member
     * <li>MEMBER               - Anyone can add members
     * <li>APPLICATION-TRANSFER - Team OWNER can transfer application
     * <li>AUTH-CLIENT          - Team OWNER (user) can create an auth client; auth client callers may create only if their
     *                            registry type is NON_USER, they belong to the target team, and the request is for a new
     *                            version of an auth client that already exists
     * </ul>
     */
    private <T, E> boolean authorizeCreatePermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser, Serializable target) {
        var isAuthorized = false;
        if (Stream.of(TARGET_TEAM_MEMBER, TARGET_APPLICATION_TRANSFER).anyMatch(targetType::equalsIgnoreCase)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, TeamMemberRole.OWNER);
        } else if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER));
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType) && !isNonUser) {
            isAuthorized = true;
        } else if (TARGET_MEMBER.equalsIgnoreCase(targetType)) {
            isAuthorized = true;
        } else if (TARGET_AUTH_CLIENT.equalsIgnoreCase(targetType)) {
            if (isNonUser) {
                isAuthorized = verifyAuthClientCreatePermission(idKey, memberIdOrNonUserId, target);
            } else {
                isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, TeamMemberRole.OWNER);
            }
        }
        return isAuthorized;
    }

    private <T, E> boolean verifyAuthClientCreatePermission(T idKey, E memberIdOrNonUserId, Serializable target) {
        // verify that the caller is a MEMBER of the target team
        if (!verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER))) { // NOTE: Auth client default role is 'MEMBER'
            return false;
        }
        // verify that the caller is a NON_USER auth client and the requested auth client exists
        // caller can create a new version of the existing auth client to self-update their credentials or for another auth client on the same team.
        AuthClientType callerType = memberIdOrNonUserId instanceof String nonUserId ? resolveCallingAuthClientType(nonUserId) : null;
        if (callerType == AuthClientType.NON_USER) {
            AuthClientRequest authClientRequest = getRequestedAuthClient(target);
            if (authClientRequest == null) {
                return false;
            }
            AuthClientView existingAuthClient = registryService.getAuthClient(authClientRequest.getClientId());
            if (existingAuthClient != null) {
                return true;
            }
        }
        return false;
    }

    // Return the caller client name (base client id without the version suffix)
    private String getClientName(String clientId) {
        ModelPair<String, String> callerClientIdAndName = SecurityHelper.isNonUserJwt() ? SecurityHelper.getAuthClientIdAndName() : null;
        if (callerClientIdAndName == null || callerClientIdAndName.getSecond() == null) {
            return clientId.replaceAll("-v\\d+$", "");
        }
        return callerClientIdAndName.getSecond();
    }

    private AuthClientView resolveCallingAuthClient(String clientId) {
        String clientName = getClientName(clientId);
        return registryService.getAuthClient(clientName);
    }

    private AuthClientType resolveCallingAuthClientType(String clientId) {
        String clientName = getClientName(clientId);
        AuthClientView authClientView = registryService.getAuthClient(clientName);
        return authClientView == null ? null : authClientView.getType();
    }

    private AuthClientRequest getRequestedAuthClient(Serializable target) {
        if (target instanceof AuthClientRequest authClientRequest) {
            return authClientRequest;
        }
        return null;
    }

    /**
     * UPDATE
     * <ul>
     * <li>APPLICATION          - Team OWNER & MEMBER role can update application data
     * <li>TEAM                 - Team OWNER & MEMBER role can update team data
     * <li>TEAM-MEMBER          - Team OWNER can add a team member
     * <li>MEMBER               - The individual member has full authority to their own member data
     * <li>APPLICATION-TRANSFER - Team OWNER can accept/reject/cancel transfer application
     * <li>AUTH-CLIENT          - Team OWNER can update/reset an auth client;
     *                            a NON_USER auth client may update a USER or USER_PKCE auth client on the same team
     * </ul>
     */
    private <T, E> boolean authorizeUpdatePermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser, Serializable target) {
        var isAuthorized = false;
        if (Stream.of(TARGET_TEAM_MEMBER, TARGET_APPLICATION_TRANSFER).anyMatch(targetType::equalsIgnoreCase)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, TeamMemberRole.OWNER);
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER));
        } else if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByApplicationIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER));
        } else if (TARGET_MEMBER.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyMemberOwnerByMemberIdKey(idKey, memberIdOrNonUserId);
        } else if (TARGET_AUTH_CLIENT.equalsIgnoreCase(targetType)) {
            if (isNonUser) {
                isAuthorized = verifyAuthClientUpdatePermission(idKey, memberIdOrNonUserId, target);
            } else if (target instanceof AuthClientRequest authClientRequest) {
                // use the auth client id from the request
                isAuthorized = verifyTeamRoleByAuthClientIdKey(authClientRequest.getClientId(), memberIdOrNonUserId, TeamMemberRole.OWNER);
            } else {
                // resetAuthClientSecret passes a clientId string
                isAuthorized = verifyTeamRoleByAuthClientIdKey(idKey, memberIdOrNonUserId, TeamMemberRole.OWNER);
            }
        }
        return isAuthorized;
    }

    /**
     * Grants a NON_USER auth client permission to update a USER or USER_PKCE auth client on the same team.
     * Checks:
     * <ol>
     *   <li>Caller's auth client type must be {@link AuthClientType#NON_USER}</li>
     *   <li>Target auth client type must be {@link AuthClientType#USER} or {@link AuthClientType#USER_PKCE}</li>
     *   <li>Caller and target must belong to the same team</li>
     * </ol>
     */
    private <T, E> boolean verifyAuthClientUpdatePermission(T idKey, E memberIdOrNonUserId, Serializable target) {
        // verify that the caller permission on the target team
        if (!verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER))) { // NOTE: Auth client default role is 'MEMBER'
            return false;
        }
        // verify that the caller is a NON_USER auth client and the requested auth client exists
        // caller can update the existing auth client redirect uris for another USER/USER_PKCE auth client on the same team.
        AuthClientView callerAuthClient = memberIdOrNonUserId instanceof String nonUserId ? resolveCallingAuthClient(nonUserId) : null;
        if (callerAuthClient != null && AuthClientType.NON_USER.equals(callerAuthClient.getType())) {
            AuthClientRequest authClientRequest = getRequestedAuthClient(target);
            if (authClientRequest == null) {
                return false;
            }
            AuthClientView existingAuthClient = registryService.getAuthClient(authClientRequest.getClientId());
            return existingAuthClient != null
                    && List.of(AuthClientType.USER, AuthClientType.USER_PKCE).contains(existingAuthClient.getType())
                    && isAuthClientOnTeam(existingAuthClient, authClientRequest.getTeamName())
                    && isAuthClientOnTeam(callerAuthClient, authClientRequest.getTeamName());
        }
        return false;
    }

    private boolean isAuthClientOnTeam(AuthClientView authClientView, String teamName) {
        return !CollectionUtils.isEmpty(authClientView.getTeams())
                && authClientView.getTeams().stream()
            .anyMatch(team -> team.getName() != null && team.getName().equalsIgnoreCase(teamName));
    }

    private <T, E> boolean authorizeModifyApplicationOwnerFieldPermission(String targetType, T idKey, E memberIdOrNonUserId) {
        var isAuthorized = false;
        if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByApplicationIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        }
        return isAuthorized;
    }

    private <T, E> boolean authorizeModifyApplicationPriorityPermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser) {
        var isAuthorized = false;
        if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByApplicationIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        }
        return isAuthorized;
    }

    private <T, E> boolean authorizeModifyApplicationExternalIdPermission(String targetType, T idKey, E memberIdOrNonUserId) {
        var isAuthorized = false;
        if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByApplicationIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        }
        return isAuthorized;
    }

    private <T, E> boolean authorizeModifyAiEnabledPermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser) {
        var isAuthorized = false;
        if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByApplicationIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        }
        return isAuthorized;
    }

    /**
     * DELETE
     * <ul>
     * <li>APPLICATION          - Team OWNER & MEMBER role can delete an application
     * <li>TEAM                 - Team OWNER role can delete a team
     * <li>TEAM-MEMBER          - Team OWNER can remove a team member
     * <li>MEMBER               - The individual member has full authority to their own member data.
     * <li>APPLICATION-TRANSFER - No one (currently) can delete a transfer application
     * <li>AUTH-CLIENT          - Team OWNER can delete an auth client
     * </ul>
     */
    private <T, E> boolean authorizeDeletePermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser) {
        var isAuthorized = false;
        if (TARGET_TEAM_MEMBER.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, TeamMemberRole.OWNER);
        } else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
        } else if (TARGET_APPLICATION.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByApplicationIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER));
        } else if (TARGET_MEMBER.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyMemberOwnerByMemberIdKey(idKey, memberIdOrNonUserId);
        } else if (TARGET_AUTH_CLIENT.equalsIgnoreCase(targetType)) {
            isAuthorized = verifyTeamRoleByAuthClientIdKey(idKey, memberIdOrNonUserId, TeamMemberRole.OWNER);
        }

        return isAuthorized;
    }

    private <T, E> boolean authorizeModifyAEmailListPermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser) {
        var isAuthorized = false;
        if (isNonUser) {
            isAuthorized = false;
        }
        else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
           if (idKey == null){
               isAuthorized = true;
           } else {
               isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
           }
       }
       return isAuthorized;
    }

    private <T, E> boolean authorizeModifySlackChannelPermission(String targetType, T idKey, E memberIdOrNonUserId, boolean isNonUser) {
        var isAuthorized = false;
        if (isNonUser) {
            isAuthorized = false;
        }
        else if (TARGET_TEAM.equalsIgnoreCase(targetType)) {
            if (idKey == null){
                isAuthorized = true;
            } else {
                isAuthorized = verifyTeamRoleByTeamIdKey(idKey, memberIdOrNonUserId, List.of(TeamMemberRole.OWNER));
            }
        }
        return isAuthorized;
    }

    private boolean authorizeRunJobPermission(String targetType) {
        var isAuthorized = false;
        if (TARGET_ADMIN_TASK.equalsIgnoreCase(targetType)) {
            validateAdminAuthorizedUser();
            isAuthorized = true;
        }
        return isAuthorized;
    }

    private <T, E> boolean verifyTeamRoleByAuthClientIdKey(T clientId, E memberIdOrNonUserId, TeamMemberRole role) {
        boolean isOwner = false;
        if ((clientId instanceof String || clientId == null) && memberIdOrNonUserId instanceof Long) {
            isOwner = registryService.verifyAuthClientMemberRoles((String) clientId, (Long)memberIdOrNonUserId, role);
        }
        return isOwner;
    }

    private <T, E> boolean verifyTeamRoleByTeamIdKey(T teamIdOrName, E memberIdOrNonUserId, TeamMemberRole role) {
        return verifyTeamRoleByTeamIdKey(teamIdOrName, memberIdOrNonUserId, List.of(role));
    }

    private <T, E> boolean verifyTeamRoleByTeamIdKey(T teamIdOrName, E memberIdOrNonUserId, List<TeamMemberRole> roles) {
        boolean isOwner = false;
        if (teamIdOrName instanceof Long && memberIdOrNonUserId instanceof Long) {
            isOwner = registryService.verifyTeamMemberRoles((Long) teamIdOrName, (Long) memberIdOrNonUserId, roles);
        } else if ((teamIdOrName instanceof String || teamIdOrName == null) && memberIdOrNonUserId instanceof Long) {
            isOwner = registryService.verifyTeamMemberRoles((String) teamIdOrName, (Long) memberIdOrNonUserId, roles);
        } else if (teamIdOrName instanceof Long && memberIdOrNonUserId instanceof String) {
            isOwner = registryService.verifyTeamAuthClientRoles((Long) teamIdOrName, (String) memberIdOrNonUserId, roles);
        } else if ((teamIdOrName instanceof String || teamIdOrName == null) && memberIdOrNonUserId instanceof String) {
            isOwner = registryService.verifyTeamAuthClientRoles((String) teamIdOrName, (String) memberIdOrNonUserId, roles);
        }

        return isOwner;
    }

    private <T, E> boolean verifyTeamRoleByApplicationIdKey(T applicationIdOrName, E memberIdOrNonUserId, List<TeamMemberRole> roles) {
        boolean isOwner = false;
        if (applicationIdOrName instanceof Long && memberIdOrNonUserId instanceof Long) {
            isOwner = registryService.verifyApplicationRoles((Long) applicationIdOrName, (Long) memberIdOrNonUserId, roles);
        } else if ((applicationIdOrName instanceof String || applicationIdOrName == null) && memberIdOrNonUserId instanceof Long) {
            isOwner = registryService.verifyApplicationRoles((String) applicationIdOrName, (Long) memberIdOrNonUserId, roles);
        } else if (applicationIdOrName instanceof Long && memberIdOrNonUserId instanceof String) {
            isOwner = registryService.verifyApplicationRoles((Long) applicationIdOrName, (String) memberIdOrNonUserId, roles);
        } else if ((applicationIdOrName instanceof String || applicationIdOrName == null) && memberIdOrNonUserId instanceof String) {
            isOwner = registryService.verifyApplicationRoles((String) applicationIdOrName, (String) memberIdOrNonUserId, roles);
        }
        return isOwner;
    }

    private <T, E> boolean verifyMemberOwnerByMemberIdKey(T memberIdOrExternalIdOrExternalAlias, E memberIdOrNonUserId) {
        boolean isOwner = false;
        if (memberIdOrExternalIdOrExternalAlias instanceof Long) {
            isOwner = memberIdOrNonUserId.equals(memberIdOrExternalIdOrExternalAlias);
        } else if (memberIdOrExternalIdOrExternalAlias instanceof String && memberIdOrNonUserId instanceof Long) {
            isOwner = registryService.verifyMemberOwner((String) memberIdOrExternalIdOrExternalAlias, (Long) memberIdOrNonUserId);
        }
        return isOwner;
    }

    private void validateAuthorizedUser(boolean isNonUser) {
        // verify that authenticated user/non-user is in the authorized team list
        List<TeamView> teams = registryService.getAuthorizedTeams();
        String externalIdOrNonUserId = SecurityHelper.getCurrentUserExternalIdOrClientId();
        boolean isValid = true;
        if (!teams.isEmpty()) {
            if (isNonUser) {
                List<TeamViewAuthClient> authClients = new ArrayList<>();
                teams.forEach(t -> Optional.ofNullable(t.getAuthClients()).ifPresent(authClients::addAll));
                ModelPair<String, String> authClientIdAndName = SecurityHelper.getAuthClientIdAndName();
                isValid = !authClients.isEmpty() && authClientIdAndName != null && authClients.stream()
                        .anyMatch(m -> m.getClientId().equalsIgnoreCase(authClientIdAndName.getFirst()) ||
                                m.getClientId().equalsIgnoreCase(authClientIdAndName.getSecond()));
            } else {
                List<TeamViewMember> members = new ArrayList<>();
                teams.forEach(t -> Optional.ofNullable(t.getMembers()).ifPresent(members::addAll));
                isValid = !members.isEmpty() && members.stream()
                        .anyMatch(m -> m.getExternalId().equalsIgnoreCase(externalIdOrNonUserId));
            }
        }
        if (!isValid) {
            log.warn(ExceptionReason.NOT_IN_AUTHORIZED_TEAMS_LIST.label, externalIdOrNonUserId);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "authorized-teams-override",
                    ExceptionReason.NOT_IN_AUTHORIZED_TEAMS_LIST,
                    String.format(ExceptionReason.NOT_IN_AUTHORIZED_TEAMS_LIST.label, externalIdOrNonUserId));
        }
    }

    // verify that authenticated user/non-user is in the authorized team list and is an OWNER or MEMBER of the team
    private void validateAdminAuthorizedUser() {
        boolean isValid = false;
        List<TeamView> teams = registryService.getAdminAuthorizedTeams();
        if (!teams.isEmpty()) {
            List<TeamViewMember> members = new ArrayList<>();
            teams.forEach(t -> members.addAll(t.getMembers()));
            isValid = !members.isEmpty() && members.stream()
                    .anyMatch(m -> m.getExternalId().equalsIgnoreCase(SecurityHelper.getCurrentUserExternalIdOrClientId()) &&
                            (m.getRole() == TeamMemberRole.OWNER || m.getRole() == TeamMemberRole.MEMBER));
        }
        if (!isValid) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "admin-authorized-teams",
                    ExceptionReason.NOT_IN_ADMIN_AUTHORIZED_TEAMS_LIST,
                    String.format(ExceptionReason.NOT_IN_ADMIN_AUTHORIZED_TEAMS_LIST.label, SecurityHelper.getCurrentUserEmail()));
        }
    }

    private <T, E> boolean authorizeSyncChangeEventPermission(String targetType, T clientId, E memberIdOrNonUserId, boolean isNonUser) {
        var isAuthorized = false;
        if (TARGET_CHANGE_EVENT.equalsIgnoreCase(targetType)) {
            if (isNonUser) {
                validateSyncChangeEventForNonUser((String) clientId, (String) memberIdOrNonUserId);
            } else {
                validateAdminAuthorizedUser();
            }
            isAuthorized = true;
        }
        return isAuthorized;
    }

    private void validateSyncChangeEventForNonUser(String requestedClientId, String authClientId) {
        if (!isClientIdValid(requestedClientId, authClientId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "sync-change-event-client-id",
                    ExceptionReason.NOT_AUTHORIZED_TO_UPDATE_SYNC_CHANGE_EVENT,
                    String.format(ExceptionReason.NOT_AUTHORIZED_TO_UPDATE_SYNC_CHANGE_EVENT.label, authClientId, requestedClientId));
        }
    }

    static boolean isClientIdValid(String requestedClientId, String authClientId) {
        if (StringUtils.isBlank(requestedClientId) || StringUtils.isBlank(authClientId)) {
            return false;
        }
        ModelPair<String, String> authClientIdAndName = SecurityHelper.getAuthClientIdAndName();
        if (authClientIdAndName == null) {
            return requestedClientId.equalsIgnoreCase(authClientId);
        }
        return requestedClientId.equalsIgnoreCase(authClientIdAndName.getFirst()) ||
                requestedClientId.equalsIgnoreCase(authClientIdAndName.getSecond());
    }
}
