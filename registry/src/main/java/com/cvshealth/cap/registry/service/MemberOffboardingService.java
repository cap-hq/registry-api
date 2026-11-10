// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.model.notification.NotificationType;
import com.cvshealth.cap.registry.persistent.TeamAuthClientViewEntity;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberOffboardingService {

    private final ApplicationTransferService applicationTransferService;
    private final RegistryService registryService;
    private final AuthClientService authClientService;
    private final AzureAdUserService azureAdUserService;
    private final MemberService memberService;
    private final NotificationService notificationService;
    private final TeamService teamService;
    private final TeamAuthClientService teamAuthClientService;
    private final TeamMemberService teamMemberService;
    private final EntityManager entityManager;

    @Value("${app.job.azure-sync.app-transfer-message-template}")
    protected String appTransferMessageTemplate;

    @Value("${app.job.azure-sync.team-transfer-message-template}")
    protected String teamTransferMessageTemplate;

    @Value("${app.job.azure-sync.support.app-transfer-message-template}")
    protected String appTransferToSupportMessageTemplate;

    @Value("${app.job.azure-sync.support.team-transfer-message-template}")
    protected String teamTransferToSupportMessageTemplate;

    @Value("${app.job.azure-sync.support.team-name}")
    protected String orphanAppTeamName;

    @Transactional(value = REQUIRES_NEW)
    public boolean removeMemberFromRegistry(Long memberId, String fallbackManagerExternalId) {
        return removeMemberFromRegistry(memberService.getMemberView(memberId, null), fallbackManagerExternalId);
    }

    @Transactional(value = REQUIRES_NEW)
    public boolean removeMemberFromRegistry(MemberView member, String fallbackManagerExternalId) {
        log.info("Removing member[id:{} name:{}] from Registry", member.getId(), member.getName());
        AtomicBoolean ableToDelete = new AtomicBoolean(true);

        // Determine the personal team of member taking ownership of any application(s) that was owned by the member being removed.
        // NOTE: possible that member's manager is UNKNOWN due to member no longer being in Azure
        Optional<Member> memberManager = getUserManager(member, fallbackManagerExternalId);

        if (!CollectionUtils.isEmpty(member.getTeams())) {
            member.getTeams().forEach(team -> {
                if (team.getRole() == TeamMemberRole.OWNER) {
                    if (team.getType() == TeamType.SHARED) {
                        if (canMemberBeRemovedFromTeam(team.getId(), member.getId())) {
                            teamMemberService.removeMemberFromTeam(team, member, false);
                        } else {
                            // no other team owner exists, assign to member manager (if known) otherwise to Registry support team
                            if (memberManager.isPresent()) {
                                teamMemberService.addOrUpdateTeamMember(team, memberManager.get(),TeamMemberRole.OWNER);
                                teamMemberService.removeMemberFromTeam(team, member, true);
                                notifyManagerOfTeamOwnership(memberManager.get(), member, team);
                            } else if (assignTeamToRegistrySupport(team, member)) {
                                teamMemberService.removeMemberFromTeam(team, member, true);
                            } else {
                                ableToDelete.set(false);
                                log.error("Failed to remove member[{}] from team[{}]; member does not exist in Azure.  ActionRequired: Assign team a new OWNER",
                                        member.getEmail(), team.getName());
                            }
                        }
                    }
                    if (team.getType() == TeamType.PERSONAL) {
                        // delete any USER* auth clients
                        Optional<TeamViewEntity> teamViewEntity = teamService.getTeamViewEntity(team.getId());
                        List<TeamAuthClientViewEntity> authClients = teamViewEntity
                                .map(TeamViewEntity::getTeamAuthClients).orElse(Collections.emptyList());
                        List<AuthClientView> oAuthClients = authClients.stream().map(authClient -> {
                            String clientId = authClient.getId().getAuthClient().getClientId();
                            Integer clientVersion = authClient.getId().getAuthClient().getClientVersion();
                            return authClientService.getAuthClient(clientId, clientVersion);
                        }).toList();

                        oAuthClients.forEach(oAuthClient -> {
                            if (CollectionUtils.isEmpty(team.getApplications())
                                    || oAuthClient.getType() != AuthClientType.NON_USER) {
                                registryService.deleteAuthClient(oAuthClient.getClientId(),
                                        oAuthClient.getClientVersion());
                            }
                        });
                        // transfer personal applications to the personal team of the member's manager
                        if (!CollectionUtils.isEmpty(team.getApplications())) {
                            final Optional<? extends IdentifiableEntity> toTeam;
                            if (memberManager.isPresent()) {
                                toTeam = Optional
                                        .of(teamMemberService.getPersonalTeamForMember(memberManager.get().getId()));
                            } else if (StringUtils.isNotBlank(orphanAppTeamName)) {
                                toTeam = Optional.of(getRegistrySupportTeam()); // to Registry support
                            } else {
                                toTeam = Optional.empty();
                            }
                            if (toTeam.isPresent()) {
                                // system transfer of the application
                                team.getApplications()
                                        .forEach(application -> applicationTransferService
                                                .forceApplicationTransfer(ApplicationTransfer.builder()
                                                        .applicationId(application.getId())
                                                        .sourceTeamId(team.getId())
                                                        .receiveTeamId(toTeam.get().getId())
                                                        .build()));
                                // transfer NON_USER auth clients
                                oAuthClients.forEach(oAuthClient -> {
                                    if (oAuthClient.getType() == AuthClientType.NON_USER) {
                                        teamAuthClientService.addTeamAuthClient(toTeam.get().getId(),
                                                oAuthClient.getId());
                                        teamAuthClientService.removeAuthClientFromTeam(team.getId(),
                                                oAuthClient.getId());
                                    }
                                });
                            }
                            if (memberManager.isPresent()) {
                                notifyManagerOfApplicationOwnership(memberManager.get(), member,
                                        team.getApplications());
                            } else if (toTeam.isPresent()) {
                                notifySupportOfApplicationOwnership(toTeam.get(), member, team.getApplications());
                            } else {
                                ableToDelete.set(false);
                                log.error(
                                        "Failed to transfer member[{}] personal team[{}]; member does not exist in Azure.  ActionRequired: force application transfer(s) to personal team applications",
                                        member.getEmail(), team.getName());
                            }
                        }
                        if (ableToDelete.get()) {
                            entityManagerFlushAndClear();

                            // remove any personal team application transfers
                            applicationTransferService.deleteTransfersForTeam(team.getId());
                            // remove last member (user of the delete action) from the team
                            teamMemberService.removeMemberFromTeam(team, member, true);
                            teamService.deleteTeam(team.getId());
                        }
                    }
                } else {
                    // remove member from the team
                    teamMemberService.removeMemberFromTeam(team, member, false);
                }
            });
        }

        // delete the member
        if (ableToDelete.get()) {
            memberService.deleteMember(member.getId());
        }
        return ableToDelete.get();
    }

    TeamView getRegistrySupportTeam() {
        if (StringUtils.isBlank(orphanAppTeamName)) {
            log.warn("Team owner cannot be assigned to Registry support; Team name has not been configured");
            return null;
        }
        try {
            return teamService.getTeamView(TeamSearchFilter.builder().name(orphanAppTeamName).build(), null);
        } catch (RecordNotFoundException e) {
            log.error("Team owner cannot be assigned to Registry support; Team '{}' not found", orphanAppTeamName,
                    e);
            return null;
        }
    }

    boolean assignTeamToRegistrySupport(MemberViewTeam team, MemberView member) {
        TeamView supportTeam = getRegistrySupportTeam();
        if (supportTeam == null) {
            log.error("Team owner cannot be assigned to Registry support; Team '{}' not found", orphanAppTeamName);
            return false;
        }
        supportTeam.getMembers().forEach(teamMember -> {
            Member supportTeamMember = Member.builder()
                    .id(teamMember.getId())
                    .externalId(teamMember.getExternalId())
                    .externalAlias(teamMember.getExternalAlias())
                    .build();
            teamMemberService.addOrUpdateTeamMember(team, supportTeamMember, TeamMemberRole.OWNER);
        });
        notifySupportOfTeamOwnership(supportTeam.getMembers(), member, team);
        return true;
    }

    /**
     * Resolves who should receive ownership during offboarding.
     *
     * Business logic priority:
     * 1) Use member.managerExternalId when present and active in Azure AD.
     * 2) If missing/inactive (or not set), lookup the member in Azure AD and use that user's current active manager.
     * 3) If Azure-based resolution fails, use the admin-provided fallback manager external ID.
     */
    protected Optional<Member> getUserManager(MemberView member, String fallbackManagerExternalId) {
        try {
            Optional<Member> managerFromConfiguredManagerExternalId =
                    getEnabledManagerFromConfiguredManagerExternalId(member.getManagerExternalId());
            if (managerFromConfiguredManagerExternalId.isPresent()) {
                return managerFromConfiguredManagerExternalId;
            }

            Optional<Member> managerFromAzureMemberLookup =
                    getEnabledManagerFromAzureMemberLookup(member.getExternalId());
            if (managerFromAzureMemberLookup.isPresent()) {
                return managerFromAzureMemberLookup;
            }
        } catch (Exception e) {
            log.error("Failed to get user manager for member externalId '{}' and manager externalId '{}'",
                    member.getExternalId(), member.getManagerExternalId(), e);
            throw e;
        }

        // use fallback manager override if provided (from Admin API)
        if (StringUtils.isNotBlank(fallbackManagerExternalId)) {
            return Optional.of(registryService.getMemberByExternalId(fallbackManagerExternalId));
        }

        return Optional.empty();
    }

    private Optional<Member> getEnabledManagerFromConfiguredManagerExternalId(String managerExternalId) {
        if (StringUtils.isEmpty(managerExternalId)) {
            return Optional.empty();
        }

        Optional<User> currentManager = azureAdUserService.findByIdWithManager(managerExternalId);
        if (currentManager.isEmpty() || !currentManager.get().isAccountEnabled()) {
            log.info("Manager account with externalId '{}' not found in Azure, skipping manager assignment",
                    managerExternalId);
            return Optional.empty();
        }

        return Optional.ofNullable(registryService.getMemberByExternalId(managerExternalId));
    }

    private Optional<Member> getEnabledManagerFromAzureMemberLookup(String memberExternalId) {
        if (StringUtils.isBlank(memberExternalId)) {
            return Optional.empty();
        }
        return azureAdUserService.findByIdWithManager(memberExternalId)
                .flatMap(user -> Optional.ofNullable(user.getManager()))
                .filter(User::isAccountEnabled)
                .map(user -> registryService.getMemberByExternalId(user.getExternalId()));
    }

    private void notifyManagerOfTeamOwnership(Member manager, MemberView deletedUser, MemberViewTeam teamView) {
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .notificationTypes(Collections.singletonList(NotificationType.EMAIL))
                .recipients(List.of(manager))
                .subject("Team Ownership Updated")
                .template(teamTransferMessageTemplate)
                .model(Map.of(
                        "greeting", manager.getFirstName(),
                        "teamName", teamView.getName(),
                        "previousOwnerName", deletedUser.getName()))
                .build();
        try {
            notificationService.sendMemberNotification(notificationRequest);
        } catch (NotificationServiceException e) {
            log.warn("Failed to send manager team transfer notification", e);
        }
    }

    private void notifySupportOfTeamOwnership(List<? extends IdentifiableUser> supportMembers, MemberView deletedUser,
            MemberViewTeam teamView) {
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .notificationTypes(Collections.singletonList(NotificationType.EMAIL))
                .recipients(supportMembers)
                .subject("Azure Sync Job Failure - Team Ownership Update Not Successful")
                .template(teamTransferToSupportMessageTemplate)
                .model(Map.of(
                        "greeting", "Registry Support Team",
                        "teamName", teamView.getName(),
                        "previousOwnerName", deletedUser.getName(),
                        "orphanAppTeam", orphanAppTeamName))
                .build();
        try {
            notificationService.sendMemberNotification(notificationRequest);
        } catch (NotificationServiceException e) {
            log.warn("Failed to send support team transfer notification", e);
        }
    }

    private void notifyManagerOfApplicationOwnership(Member manager, MemberView deletedUser,
            List<MemberViewApplication> applications) {
        List<String> applicationNames = applications.stream().map(MemberViewApplication::getName).toList();
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .notificationTypes(Collections.singletonList(NotificationType.EMAIL))
                .recipients(List.of(manager))
                .subject("Team Ownership Updated")
                .template(appTransferMessageTemplate)
                .model(Map.of(
                        "greeting", manager.getFirstName(),
                        "previousOwnerName", deletedUser.getName(),
                        "applicationNames", applicationNames.toString()))
                .build();
        try {
            notificationService.sendMemberNotification(notificationRequest);
        } catch (NotificationServiceException e) {
            log.warn("Failed to send manager application transfer notification", e);
        }
    }

    private void notifySupportOfApplicationOwnership(IdentifiableEntity teamIdentifiableEntity, MemberView deletedUser,
            List<MemberViewApplication> applications) {
        if (teamIdentifiableEntity instanceof TeamView teamView) {
            List<String> applicationNames = applications.stream().map(MemberViewApplication::getName).toList();
            NotificationRequest notificationRequest = NotificationRequest.builder()
                    .notificationTypes(Collections.singletonList(NotificationType.EMAIL))
                    .recipients(teamView.getMembers())
                    .subject("Azure Sync Job Failure - Team Ownership Update Not Successful")
                    .template(appTransferToSupportMessageTemplate)
                    .model(Map.of(
                            "greeting", "Registry Support Team",
                            "previousOwnerName", deletedUser.getName(),
                            "orphanAppTeam", orphanAppTeamName,
                            "applicationNames", applicationNames.toString()))
                    .build();
            try {
                notificationService.sendMemberNotification(notificationRequest);
            } catch (NotificationServiceException e) {
                log.warn("Failed to send support application transfer notification", e);
            }
        }
    }

    private void entityManagerFlushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Returns whether the member can be removed from the team (when another team
     * owner exists)
     */
    private boolean canMemberBeRemovedFromTeam(Long teamId, Long memberId) {
        List<MemberRoleView> teamMembers = teamMemberService.getMembersForTeam(teamId);
        return (teamMembers.stream().anyMatch(tm -> tm.getRole().equals(TeamMemberRole.OWNER) &&
                !tm.getId().equals(memberId)));
    }
}
