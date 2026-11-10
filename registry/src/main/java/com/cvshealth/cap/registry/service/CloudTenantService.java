// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.model.rollup.UserView;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudTenantService {

    private final List<CloudTenant> cloudTenants;
    private final RegistryRollupService registryRollupService;
    private final TeamMemberService teamMemberService;

    @Getter
    @Value("${app.application.cloud.default}")
    String defaultCloud;

    @Getter
    @Value("${app.application.tenant.default}")
    String defaultTenant;

    @PostConstruct
    public void init() {
        log.info("CloudTenantService initialized with:");
        log.info("Default Cloud: {}", defaultCloud);
        log.info("Default Tenant: {}", defaultTenant);
        log.info("Configured Clouds: {}", cloudTenants);
        validateConfig();
    }

    private void validateConfig() {
        if (!StringUtils.isEmpty(defaultCloud)) {
            Optional<CloudTenant> cloud = cloudTenants.stream().filter(cloudTenant -> cloudTenant.getName().equals(defaultCloud)).findFirst();
            if (cloud.isEmpty()) {
                throw new IllegalStateException(String.format("Cloud / Tenant configuration error: Default cloud '%s' must be configured", defaultCloud));
            }
        }
        for (CloudTenant cloudTenant : cloudTenants) {
            if (!StringUtils.isEmpty(defaultTenant) && !cloudTenant.getTenants().contains(defaultTenant)) {
                throw new IllegalStateException(String.format("Cloud / Tenant configuration error: cloud '%s' tenant list must include the defaultTenant: '%s'",
                        cloudTenant.getName(), defaultTenant));
            }
            if (CollectionUtils.isEmpty(cloudTenant.getRegions())) {
                throw new IllegalStateException(String.format("Cloud / Tenant configuration error: cloud '%s' must have at least one region configured", cloudTenant.getName()));
            }
            if (cloudTenant.getRegions().stream().anyMatch(StringUtils::isBlank)) {
                throw new IllegalStateException(String.format("Cloud / Tenant configuration error: cloud '%s' regions list must not contain blank entries", cloudTenant.getName()));
            }
            if (cloudTenant.getDefaultRegion() == null || !cloudTenant.getRegions().contains(cloudTenant.getDefaultRegion())) {
                throw new IllegalStateException(String.format("Cloud / Tenant configuration error: cloud '%s' defaultRegion '%s' must be set and present in its regions list",
                        cloudTenant.getName(), cloudTenant.getDefaultRegion()));
            }
        }
    }

    public List<CloudTenant> getCloudTenants() {
        log.debug("Getting cloud tenants: {}", cloudTenants);
        return CollectionUtils.isEmpty(cloudTenants) ? Collections.emptyList() : cloudTenants;
    }

    public CloudTenant getCloudTenant(String cloudName) {
        log.debug("Getting cloud tenant for: {}", cloudName);
        if (CollectionUtils.isEmpty(cloudTenants)) {
            log.warn("No cloud tenants configured!");
            return null;
        }
        CloudTenant result = cloudTenants.stream()
                .filter(ct -> ct.getName().equals(cloudName))
                .findFirst()
                .orElse(null);
        log.debug("Found cloud tenant: {}", result);
        return result;
    }

    @Cacheable(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#teamView.name")
    public List<CloudTenant> getCloudTenantsForTeamName(TeamAvailableCloudsAwareTeamView teamView) {
        List<? extends TeamMemberIdentity> teamMembers;
        if (teamView instanceof TeamMemberViewAware teamMemberViewAware) {
            teamMembers = teamMemberViewAware.getMembers();
        } else {
            teamMembers = null;
        }
        return cloudTenants.stream().filter(cloudTenant -> isAuthorizedToCloud(cloudTenant, teamView.getName(), teamView.getId(), teamMembers)).toList();
    }

    @Cacheable(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#team.name")
    public List<CloudTenant> getCloudTenantsForTeamName(Team team) {
        return cloudTenants.stream().filter(cloudTenant -> isAuthorizedToCloud(cloudTenant, team.getName(), team.getId(), null)).toList();
    }

    boolean isAuthorizedToCloud(CloudTenant cloudTenant, String teamName, Long teamId, List<? extends TeamMemberIdentity> teamMembers) {
        return isCloudAvailableToAllTeams(cloudTenant)
                || isTeamAuthorized(teamName, cloudTenant)
                || isTeamRollupAuthorized(teamName, cloudTenant, teamMembers, teamId);
    }

    boolean isCloudAvailableToAllTeams(CloudTenant cloudTenant) {
        return (cloudTenant.getName() != null && cloudTenant.getName().equals(defaultCloud)) ||
                (CollectionUtils.isEmpty(cloudTenant.getAuthorizedTeams()) && CollectionUtils.isEmpty(cloudTenant.getAuthorizedManagerExternalIds()));
    }

    boolean isTeamAuthorized(String teamName, CloudTenant cloudTenant) {
        if (StringUtils.isBlank(teamName) || cloudTenant == null || CollectionUtils.isEmpty(cloudTenant.getAuthorizedTeams())) {
            return false;
        }
        return cloudTenant.getAuthorizedTeams().contains(teamName);
    }

    boolean isTeamRollupAuthorized(String teamName, CloudTenant cloudTenant, List<? extends TeamMemberIdentity> teamMembers, Long teamId) {
        if (StringUtils.isBlank(teamName) || cloudTenant == null || CollectionUtils.isEmpty(cloudTenant.getAuthorizedManagerExternalIds())) {
            return false;
        }
        TeamRollupView teamRollup = registryRollupService.getTeamRollup(teamName);
        if (teamRollup == null) {
            return false;
        }
        if (teamName.equals(teamRollup.getName())) {
            if (!CollectionUtils.isEmpty(teamRollup.getManagers())) {
                for (UserView manager : teamRollup.getManagers()) {
                    String externalId = manager.getExternalId();
                    if (externalId != null && cloudTenant.getAuthorizedManagerExternalIds().contains(externalId)) {
                        return true;
                    }
                }
            }
            // check if team contains an authorized manager external id
            if (teamMembers == null) {
                teamMembers = teamMemberService.getMembersForTeam(teamId != null ? teamId : teamRollup.getId());
            }
            if (!CollectionUtils.isEmpty(teamMembers)) {
                for (TeamMemberIdentity member : teamMembers) {
                    if (member.getRole() != TeamMemberRole.OWNER && member.getRole() != TeamMemberRole.MEMBER) {
                        continue;
                    }
                    String externalId = member.getExternalId();
                    if (externalId != null && cloudTenant.getAuthorizedManagerExternalIds().contains(externalId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public List<String> getTenantsForCloud(String cloudName) {
        log.debug("Getting tenants for cloud: {}", cloudName);
        CloudTenant cloudTenant = getCloudTenant(cloudName);
        List<String> tenants = cloudTenant != null ? cloudTenant.getTenants() : Collections.emptyList();
        log.debug("Found tenants: {}", tenants);
        return tenants;
    }
}