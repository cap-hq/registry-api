// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;
import com.cvshealth.cap.registry.auth.config.ClientPrivilegeConfig;
import com.cvshealth.cap.registry.auth.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.auth.model.CustomScopeType.REGISTRY;

@Service
@Slf4j
public class RegistryService {
    private final RegistryApiService apiService;

    private final List<CustomClaim> customClaims;
    private final List<CustomClaim> customIdentityClaims;
    private final ClientPrivilegeConfig clientPrivilegeConfig;

    public RegistryService(RegistryApiService apiService, @Qualifier("customClaims") List<CustomClaim> customClaims,
                              @Qualifier("customIdentityClaims") List<CustomClaim> customIdentityClaims, ClientPrivilegeConfig clientPrivilegeConfig) {
        this.apiService = apiService;
        this.customClaims = customClaims == null ? null : customClaims.stream().filter(CustomClaim::isSourceRegistry).toList();
        this.customIdentityClaims = customIdentityClaims == null ? null : customIdentityClaims.stream().filter(CustomClaim::isSourceRegistry).toList();
        this.clientPrivilegeConfig = clientPrivilegeConfig;
    }

    public Map<String, Object> getClaims(String externalId, CustomScopeType scopeType) {
        Map<String, Object> result = new HashMap<>();
        Member member = apiService.getMember(externalId, REGISTRY.equals(scopeType));
        if (member != null) {
            // member claims
            result.put("externalId", member.getExternalId());
            result.put("externalAlias", member.getExternalAlias());
            result.put(StandardClaimNames.NAME, member.getFirstName() + " " + member.getLastName());
            result.put(StandardClaimNames.GIVEN_NAME, member.getFirstName());
            result.put(StandardClaimNames.FAMILY_NAME, member.getLastName());

            if (REGISTRY.equals(scopeType)) {
                List<Team> teams = member.getTeams() == null ? Collections.emptyList() : member.getTeams();
                // team claims
                result.put("team_owner", teams.stream().filter(t -> Team.OWNER.equals(t.getRole()))
                        .map(Team::getName).collect(Collectors.toList()));
                result.put("team_member", teams.stream().filter(t -> Team.MEMBER.equals(t.getRole()))
                        .map(Team::getName).collect(Collectors.toList()));
                result.put("team_viewer", teams.stream().filter(t -> Team.VIEWER.equals(t.getRole()))
                        .map(Team::getName).collect(Collectors.toList()));
                result.put("team_personal", teams.stream().filter(t -> Team.PERSONAL.equals(t.getType()))
                        .map(Team::getName).collect(Collectors.toList()));
                // application claims
                result.put("app_owner", teams.stream().filter(t -> Team.OWNER.equals(t.getRole()))
                        .flatMap(t -> t.getApplications().stream()).map(Application::getName).collect(Collectors.toList()));
                result.put("app_member", teams.stream().filter(t -> Team.MEMBER.equals(t.getRole()))
                        .flatMap(t -> t.getApplications().stream()).map(Application::getName).collect(Collectors.toList()));
                result.put("app_viewer", teams.stream().filter(t -> Team.VIEWER.equals(t.getRole()))
                        .flatMap(t -> t.getApplications().stream()).map(Application::getName).collect(Collectors.toList()));
                result.put("app_personal", teams.stream().filter(t -> Team.PERSONAL.equals(t.getType()))
                        .flatMap(t -> t.getApplications().stream()).map(Application::getName).collect(Collectors.toList()));
            }
            if (customClaims != null) {
                for (CustomClaim customClaim : customClaims) {
                    if (customClaim.getScopeType() == null || customClaim.getScopeType().equals(scopeType)) {
                        if (customClaim.isMemberClaim()) {
                            result.put(customClaim.getName(), resolveCustomClaims(customClaim, member));
                        }
                    }
                }
            }
        }
        return result;
    }

    public Map<String, Object> getClaimsForClientId(String clientId, int clientVersion, CustomScopeType scopeType) {
        Map<String, Object> result = new HashMap<>();
        AuthClient authClient = apiService.getAuthClient(clientId, clientVersion, REGISTRY.equals(scopeType));
        if (authClient == null) {
            return result;
        }
        if (REGISTRY.equals(scopeType)) {
            result.put("team_owner", new ArrayList<>());
            result.put("team_member", teamsWithMemberRole(authClient));
            result.put("team_viewer", new ArrayList<>());
            result.put("team_personal", new ArrayList<>());
            // application claims
            result.put("app_owner", new ArrayList<>());
            result.put("app_member", applicationsWithMemberRole(authClient));
            result.put("app_viewer", new ArrayList<>());
            result.put("app_personal", new ArrayList<>());
        }

        if (customClaims != null) {
            for (CustomClaim customClaim : customClaims) {
                if (customClaim.getScopeType() == null || customClaim.getScopeType().equals(scopeType)) {
                    if (CustomClaim.TYPE_AUTH_CLIENT.equals(customClaim.getType())) {
                        result.put(customClaim.getName(), resolveCustomClaims(customClaim, authClient));
                    } else if (CustomClaim.TYPE_APPLICATION.equals(customClaim.getType())) {
                        result.put(customClaim.getName(), resolveCustomClaims(customClaim, authClient.getTeams()));
                    }
                }
            }
        }
        return result;
    }

    // any team associations are deemed to be "MEMBER" role for clients
    private List<String> teamsWithMemberRole(AuthClient authClient) {
        if (CollectionUtils.isEmpty(authClient.getTeams())) {
            return new ArrayList<>();
        }
        return authClient.getTeams().stream().map(Team::getName).collect(Collectors.toList());
    }

    // any apps owned by associated teams are deemed to be "MEMBER" role for clients
    private List<String> applicationsWithMemberRole(AuthClient authClient) {
        if (CollectionUtils.isEmpty(authClient.getTeams())) {
            return new ArrayList<>();
        }
        return authClient.getTeams().stream().filter(t -> t.getApplications() != null).flatMap(t -> t.getApplications().stream())
                .map(Application::getName).collect(Collectors.toList());
    }

    public Map<String, Object> getIdentityClaims(String externalId, CustomScopeType scopeType) {
        if (CollectionUtils.isEmpty(customIdentityClaims)) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        Member member = apiService.getMember(externalId, REGISTRY.equals(scopeType));
        if (member != null) {
            for (CustomClaim customClaim : customIdentityClaims) {
                if (customClaim.getScopeType() == null || customClaim.getScopeType().equals(scopeType)) {
                    result.put(customClaim.getName(), resolveCustomClaims(customClaim, member));
                }
            }
        }
        return result;
    }

    protected Object resolveCustomClaims(CustomClaim customClaim, Member member) {
        return resolveCustomClaims(customClaim, member.getTeams());
    }

    protected Object resolveCustomClaims(CustomClaim customClaim, List<Team> teams) {
        if (CollectionUtils.isEmpty(teams)) {
            return Collections.emptyList();
        }
        if (CustomClaim.TYPE_APPLICATION.equals(customClaim.getType())) {
            if (customClaim.getApplication() != null) {
                return teams.stream().filter(t -> t.getApplications() != null)
                        .flatMap(t -> t.getApplications().stream()
                                .filter(app -> matchesApplicationFilter(app, customClaim.getApplication())))
                        .map(Application::getName).collect(Collectors.toList());
            } else if (StringUtils.isNotBlank(customClaim.getAttribute())) {
                return getApplicationAttributeMap(customClaim, teams);
            }
        } else if (CustomClaim.TYPE_TEAM.equals(customClaim.getType())) {
            return teams.stream()
                    .filter(t -> Team.SHARED.equals(t.getType()) && !Team.VIEWER.equals(t.getRole()))
                    .map(Team::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * An application matches when it satisfies every criterion the claim sets: all of the configured compliance
     * types, and the configured information classification. A claim that sets neither matches nothing.
     */
    private static boolean matchesApplicationFilter(Application app, Application filter) {
        boolean filtersOnTags = !CollectionUtils.isEmpty(filter.getComplianceTypes());
        boolean filtersOnClassification = StringUtils.isNotBlank(filter.getInformationClassification());
        if (!filtersOnTags && !filtersOnClassification) {
            return false;
        }
        if (filtersOnTags && (CollectionUtils.isEmpty(app.getComplianceTypes())
                || !app.getComplianceTypes().containsAll(filter.getComplianceTypes()))) {
            return false;
        }
        // ignoring case so a claim configured as "restricted" still matches the registry's RESTRICTED
        return !filtersOnClassification || filter.getInformationClassification().equalsIgnoreCase(app.getInformationClassification());
    }

    protected Object resolveCustomClaims(CustomClaim customClaim, AuthClient authClient) {
        if (CustomClaim.ATTR_CLIENT_PRIVILEGE.equals(customClaim.getAttribute())) {
            return getAuthClientPrivilege(authClient);
        }
        return getAuthClientAttributeMap(customClaim, authClient);
    }

    Map<String, Object> getApplicationAttributeMap(CustomClaim customClaim, List<Team> teams) {
        Field field = getFieldForCustomAttribute(customClaim, Application.class);
        if (field == null) {
            log.warn("custom application claim attribute does not exist '{}.{}'", customClaim.getName(), customClaim.getAttribute());
            return null;
        }

        Map<String, Object> map = new HashMap<>();
        teams.stream().filter(t -> t.getApplications() != null)
                .flatMap(t -> t.getApplications().stream()).forEach(app -> {
            Object value = ReflectionUtils.getField(field, app);
            map.put(app.getName(), value);
        });
        return map;
    }

    Object getAuthClientAttributeMap(CustomClaim customClaim, AuthClient authClient) {
        Field field = getFieldForCustomAttribute(customClaim, AuthClient.class);
        if (field == null) {
            log.warn("custom auth client claim attribute does not exist '{}.{}'", customClaim.getName(), customClaim.getAttribute());
            return null;
        }
        Object value = ReflectionUtils.getField(field, authClient);
        if ("teams".equals(customClaim.getAttribute())) {
            List<Team> teams = (List<Team>) value;
            return teams == null ? Collections.emptyList() : teams.stream().map(Team::getName).collect(Collectors.toList());
        }
        return value;
    }

    private ClientPrivilegeType getAuthClientPrivilege(AuthClient authClient) {
        if (clientPrivilegeConfig == null || authClient == null) {
            return null;
        }
        
        // Priority 1: Client-specific privilege
        if (!CollectionUtils.isEmpty(clientPrivilegeConfig.getAuthorizedClients())) {
            ClientPrivilegeType privilegeType = clientPrivilegeConfig.getAuthorizedClients()
                    .get(authClient.getClientId());
            if (privilegeType != null) {
                log.debug("Client '{}' matched authorized client privilege: {}", 
                        authClient.getClientId(), privilegeType);
                return privilegeType;
            }
        }
        
        // Priority 2: Team-based privilege (highest among all matching teams)
        if (!CollectionUtils.isEmpty(clientPrivilegeConfig.getAuthorizedTeams()) 
                && !CollectionUtils.isEmpty(authClient.getTeams())) {
            ClientPrivilegeType highestPrivilege = null;
            for (Team team : authClient.getTeams()) {
                ClientPrivilegeType privilegeType = clientPrivilegeConfig.getAuthorizedTeams()
                        .get(team.getName());
                if (privilegeType != null) {
                    log.debug("Client '{}' matched team '{}' privilege: {}", 
                            authClient.getClientId(), team.getName(), privilegeType);
                    highestPrivilege = highestPrivilege == null ? privilegeType : highestPrivilege.max(privilegeType);
                }
            }
            if (highestPrivilege != null) {
                log.debug("Client '{}' resolved to highest team privilege: {}", 
                        authClient.getClientId(), highestPrivilege);
                return highestPrivilege;
            }
        }
        
        // Priority 3: Default privilege
        return clientPrivilegeConfig.getDefaultPrivilege();
    }

    private Field getFieldForCustomAttribute(CustomClaim customClaim, Class<?> clazz) {
        Field field = ReflectionUtils.findField(clazz, customClaim.getAttribute());
        if (field != null) {
            field.setAccessible(true);
        }
        return field;
    }
}
