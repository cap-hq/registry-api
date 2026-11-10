// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.cvshealth.cap.registry.mapper.AuthClientMapper.buildAuthClientId;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.APPLICATION_TRANSFER_NOT_FOUND;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.EXTERNAL_USER_NOT_FOUND;
import static com.cvshealth.cap.registry.persistent.base.PersistenceConstants.AUTH_CLIENT_FIRST_VERSION;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistryService {

    private final ApplicationService applicationService;
    private final MemberService memberService;
    private final NewMemberService newMemberService;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final AzureAdUserService azureAdUserService;
    private final ApplicationTransferService applicationTransferService;
    private final AuthClientService authClientService;
    private final TeamAuthClientService teamAuthClientService;
    private final ExternalResourceConstraintService externalResourceConstraintService;
    private final ApiResourceConstraintService apiResourceConstraintService;
    private final NotificationService notificationService;

    @Value("${app.notification.non-user-auth-client-created.message-template}")
    private String nonUserAuthClientCreatedMessageTemplate;

    @Value("${authorized-teams:}")
    private List<String> authorizedTeams;

    @Value("${app.admin.authorized-teams}")
    private List<String> adminAuthorizedTeams;

    private final List<LabeledRestClient> downstreamApplicationRestClients;

    private final RetryTemplate getRetryTemplate =
            RetryTemplate.builder().exponentialBackoff(1000, 2, 10000).maxAttempts(5).build();

    /**
     * Fetches a member by external id. If the member record doesn't exist, it is created.
     * Additionally, a personal team is created and the member is added as the team owner.
     */
    public Member getMemberByExternalId(String externalId) {
        if (!StringUtils.isEmpty(externalId)) {
            Member member;
            try {
                member = memberService.getMemberByExternalId(externalId);
            } catch (RecordNotFoundException e) {
                User user = azureAdUserService.findByIdWithManager(externalId).orElseThrow(() -> new RecordNotFoundException(EXTERNAL_USER_NOT_FOUND));
                checkEmployeeId(user);
                member = newMemberService.createNewMember(user);
            }
            return member;
        }
        throw new BadRequestException(ExceptionReason.MEMBER_EXTERNAL_ID_REQUIRED);
    }

    /**
     * Fetches a member by external alias. If the member record doesn't exist, it is created.
     * Additionally, a personal team is created and the member is added as the team owner.
     */
    public Member getMemberByExternalAlias(String externalAlias) {
        if (!StringUtils.isEmpty(externalAlias)) {
            Member member;
            try {
                member = memberService.getMemberByExternalAlias(externalAlias);
            } catch (RecordNotFoundException e) {
                User user = azureAdUserService.findByAlias(externalAlias).orElseThrow(() -> new RecordNotFoundException(EXTERNAL_USER_NOT_FOUND));
                checkEmployeeId(user);
                member = newMemberService.createNewMember(user);
            }
            return member;
        }
        throw new BadRequestException(ExceptionReason.MEMBER_EXTERNAL_ID_REQUIRED);
    }

    private void checkEmployeeId(User user) {
        if (user.getEmployeeId() == null) {
            throw new RecordNotFoundException(EXTERNAL_USER_NOT_FOUND);
        }
    }

    public Team createTeam(TeamRequest request) {
        return createTeam(Team.builder()
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .emailList(request.getEmailList())
                .slackChannel(request.getSlackChannel())
                .build());
    }

    public Team createTeam(Team team) {
        return createTeam(team, memberService.getMemberByExternalId(SecurityHelper.getCurrentUserExternalId()));
    }

    private Team createTeam(Team team, Member member) {
        teamMemberService.checkTeamMembershipLimit(member.getId());
        team = teamService.createTeam(team);
        teamMemberService.addTeamMember(team, member, TeamMemberRole.OWNER);
        return team;
    }

    public Team updateTeam(TeamRequest request) {
        Team team = teamService.getTeamByName(request.getName());
        // overlay updatable team fields from request
        team.setType(request.getType());
        team.setDescription(request.getDescription());
        team.setEmailList(request.getEmailList());
        team.setSlackChannel(request.getSlackChannel());
        return teamService.updateTeam(team);
    }

    @Transactional
    public void deleteTeam(Long id) throws ApiException {
        Team team = teamService.getTeam(id);
        if (team != null) {
            deleteTeam(team.getName());
        }
    }

    @Transactional
    public void deleteTeam(String name) throws ApiException {
        TeamView teamView = teamService.getTeamView(TeamSearchFilter.builder().name(name).build(), null);
        validateTeamCanBeDeleted(teamView);

        validateNoDownstreamApplicationsExist(name);

        // remove last member (user of the delete action) from the team
        teamView.getMembers().forEach(teamMember -> {
            teamMemberService.removeMemberFromTeam(teamView, teamMember, true);
        });
        // remove all app transfers related to the team
        applicationTransferService.deleteTransfersForTeam(teamView.getId());
        teamService.deleteTeam(teamView.getId());
    }

    @Transactional
    public void deleteApplication(String name) {
        deleteApplication(applicationService.getApplicationByName(name));
    }

    @Transactional
    public void deleteApplication(Long id) {
        deleteApplication(applicationService.getApplication(id));
    }

    public TeamMember addTeamMember(Long teamId, Long memberId, TeamMemberRole role) {
        Member member = memberService.getMember(memberId);
        teamMemberService.checkTeamMembershipLimit(member.getId());
        Team team = teamService.getTeam(teamId);
        return teamMemberService.addTeamMember(team, member, role);
    }

    public TeamMember updateTeamMember(Long teamId, Long memberId, TeamMemberRole role) {
        Team team = teamService.getTeam(teamId);
        Member member = memberService.getMember(memberId);
        return teamMemberService.updateTeamMember(team, member, role);
    }

    public void removeMemberFromTeam(Long teamId, Long memberId) {
        Team team = teamService.getTeam(teamId);
        Member member = memberService.getMember(memberId);
        teamMemberService.removeMemberFromTeam(team, member, false);
    }

    @Transactional
    public void deleteApplication(Application app) {
        externalResourceConstraintService.validateNoExternalResourcesExist(app.getName());
        apiResourceConstraintService.validateNoApiResourcesExist(app.getName());
        applicationTransferService.deleteTransfersForApplication(app.getId());
        applicationService.deleteApplication(app.getId());
    }

    /**
     * Returns the auth client with the given client id (latest version when version is null).
     */
    public AuthClientView getAuthClient(String clientId) {
        if (StringUtils.isEmpty(clientId)) {
            return null;
        }
        try {
            return authClientService.getAuthClient(clientId, null);
        } catch (RecordNotFoundException e) {
            return null;
        }
    }

    public boolean verifyAuthClientMemberRoles(String clientId, Long memberId, TeamMemberRole role) {
        if (clientId != null) {
            // fetch auth client with teams (but without call to RegistryAuth service to fetch additional client values)
            AuthClientView authClient = authClientService.getAuthClient(clientId, null, null);
            // NOTE: only allows 1 auth client team association
            return verifyTeamMemberRoles(authClient.getTeams().get(0).getId(), memberId, List.of(role));
        }
        throw new BadRequestException(ExceptionReason.AUTH_CLIENT_CLIENT_ID_REQUIRED);
    }

    public boolean verifyTeamMemberRoles(String name, Long memberId, List<TeamMemberRole> roles) {
        if (name != null) {
            Team team = teamService.getTeamByName(name); // verify valid team
            return verifyTeamMemberRoles(team.getId(), memberId, roles);
        }
        throw new BadRequestException(ExceptionReason.TEAM_NAME_REQUIRED);
    }

    public boolean verifyTeamMemberRoles(Long teamId, Long memberId, List<TeamMemberRole> roles) {
        if (teamId != null) {
            teamService.getTeam(teamId); // verify valid team
            TeamMemberRole role = teamMemberService.getTeamMemberRole(teamId, memberId);
            return roles.stream().anyMatch(r -> r.equals(role));
        }
        throw new BadRequestException(ExceptionReason.TEAM_ID_REQUIRED);
    }

    public boolean verifyTeamAuthClientRoles(Long teamId, String clientId, List<TeamMemberRole> roles) {
        if (teamId != null) {
            TeamView team = teamService.getTeamView(teamId, null);
            return team.getAuthClients().stream()
                    .filter(t -> t.getClientId().equals(clientId) ||
                            buildAuthClientId(t.getClientId(), t.getClientVersion() != null ? t.getClientVersion() : AUTH_CLIENT_FIRST_VERSION).equals(clientId))
                    .findFirst()
                    .map(t -> roles.stream().anyMatch(r -> r.equals(t.getRole())))
                    .orElse(false);
        }
        throw new BadRequestException(ExceptionReason.TEAM_ID_REQUIRED);
    }

    public boolean verifyTeamAuthClientRoles(String name, String clientId, List<TeamMemberRole> roles) {
        if (name != null) {
            Team team = teamService.getTeamByName(name); // verify valid team
            return verifyTeamAuthClientRoles(team.getId(), clientId, roles);
        }
        throw new BadRequestException(ExceptionReason.TEAM_NAME_REQUIRED);
    }

    public boolean verifyApplicationRoles(String name, Long memberId, List<TeamMemberRole> roles) {
        if (name != null) {
            Application application = applicationService.getApplicationByName(name);
            return verifyApplicationRoles(application, memberId, roles);
        }
        throw new BadRequestException(ExceptionReason.APPLICATION_NAME_REQUIRED);
    }

    public boolean verifyApplicationRoles(Long applicationId, Long memberId, List<TeamMemberRole> roles) {
        if (applicationId != null) {
            Application application = applicationService.getApplication(applicationId);
            return verifyApplicationRoles(application, memberId, roles);
        }
        throw new BadRequestException(ExceptionReason.APPLICATION_ID_REQUIRED);
    }

    public boolean verifyApplicationRoles(Application application, Long memberId, List<TeamMemberRole> roles) {
        if (application != null) {
            TeamMemberRole role = teamMemberService.getTeamMemberRole(application.getTeamId(), memberId);
            return roles.stream().anyMatch(r -> r.equals(role));
        }
        return false;
    }

    public boolean verifyApplicationRoles(Application application, String clientId, List<TeamMemberRole> roles) {
        if (application != null) {
            ApplicationView applicationView = applicationService.getApplicationView(application.getId(), null);
            return applicationView.getTeam().getAuthClients().stream()
                    .filter(ac -> ac.getClientId().equals(clientId) ||
                            buildAuthClientId(ac.getClientId(), ac.getClientVersion() != null ? ac.getClientVersion() : AUTH_CLIENT_FIRST_VERSION).equals(clientId))
                    .findFirst()
                    .map(ac -> roles.stream().anyMatch(r -> r.equals(ac.getRole())))
                    .orElse(false);
        }
        return false;
    }

    public boolean verifyApplicationRoles(Long applicationId, String clientId, List<TeamMemberRole> roles) {
        if (applicationId != null) {
            Application application = applicationService.getApplication(applicationId);
            return verifyApplicationRoles(application, clientId, roles);
        }
        throw new BadRequestException(ExceptionReason.APPLICATION_ID_REQUIRED);
    }

    public boolean verifyApplicationRoles(String name, String clientId, List<TeamMemberRole> roles) {
        if (name != null) {
            Application application = applicationService.getApplicationByName(name);
            return verifyApplicationRoles(application, clientId, roles);
        }
        throw new BadRequestException(ExceptionReason.APPLICATION_NAME_REQUIRED);
    }

    public boolean verifyMemberOwner(String externalIdOrExternalAlias, Long memberId) {
        Member member = null;
        try {
            member = memberService.getMemberByExternalId(externalIdOrExternalAlias);
        } catch (Exception e) {
            // no-op
        }
        if (member == null) {
            member = memberService.getMemberByExternalAlias(externalIdOrExternalAlias);
        }
        return memberId.equals(member.getId());
    }

    public TeamMember addUserToTeam(Long teamId, String externalId, TeamMemberRole role) {
        Member member = getMemberByExternalId(externalId);
        teamMemberService.checkTeamMembershipLimit(member.getId());
        Team team = teamService.getTeam(teamId);
        return teamMemberService.addTeamMember(team, member, role);
    }

    public Application createApplication(ApplicationRequest request) throws ApiException {
        return applicationService.createApplication(Application.builder()
                .name(request.getName())
                .description(request.getDescription())
                .complianceTypes(request.getComplianceTypes())
                .priority(request.getPriority())
                .externalId(request.getExternalId())
                .cloud(request.getCloud())
                .tenant(request.getTenant())
                .aiEnabled(Optional.ofNullable(request.getAiEnabled()).orElse(Boolean.FALSE))
                .dataPerimeter(Optional.ofNullable(request.getDataPerimeter()).orElse(DataPerimeter.UNRESTRICTED))
                .informationClassification(request.getInformationClassification())
                .build(), request.getTeamName());
    }

    public Application updateApplication(ApplicationRequest request) {
        Team team = teamService.getTeamByName(request.getTeamName());
        Application app = applicationService.getApplicationByName(request.getName());
        // overlay updatable team fields from request
        app.setTeamId(team.getId());
        app.setDescription(request.getDescription());
        app.setComplianceTypes(request.getComplianceTypes());
        app.setPriority(request.getPriority());
        app.setExternalId(request.getExternalId());
        app.setAiEnabled(Optional.ofNullable(request.getAiEnabled()).orElse(app.isAiEnabled()));
        app.setCloud(Optional.ofNullable(request.getCloud()).orElse(app.getCloud()));
        app.setTenant(Optional.ofNullable(request.getTenant()).orElse(app.getTenant()));
        app.setDataPerimeter(Optional.ofNullable(request.getDataPerimeter()).orElse(app.getDataPerimeter()));
        // what the caller sent; ApplicationService decides whether it changes the stored classification
        app.setInformationClassification(request.getInformationClassification());

        return applicationService.updateApplication(app, request.getTeamName());
    }

    /**
     * Update the application external ID.
     * NOTE: admin only usage to selectively allow certain applications to modify the external ID
     */
    public Application updateApplicationExternalId(ApplicationExternalIdPatchRequest patchRequest) {
        if (StringUtils.isBlank(patchRequest.getTeamName())) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_REQUIRED);
        }
        if (StringUtils.isBlank(patchRequest.getApplicationName())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_NAME_REQUIRED);
        }
        if (Strings.CI.equals(patchRequest.getNewExternalId(), patchRequest.getOldExternalId())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_EXTERNAL_ID_INVALID_FOR_PATCH);
        }

        Team team = teamService.getTeamByName(patchRequest.getTeamName());
        Application app = applicationService.getApplicationByName(patchRequest.getApplicationName());
        if (!Strings.CI.equals(app.getExternalId(), patchRequest.getOldExternalId())) {
            // double-check vs existing external id to ensure change is intentional
            throw new BadRequestException(ExceptionReason.APPLICATION_EXTERNAL_ID_INVALID_FOR_PATCH);
        }
        app.setExternalId(patchRequest.getNewExternalId());
        return applicationService.forceUpdateExternalId(app, team);
    }

    public void updateApplicationExternalId(ApplicationView applicationView) {
        ApplicationViewTeam teamView = applicationView.getTeam();
        Team team = Team.builder().id(teamView.getId()).name(teamView.getName()).type(teamView.getType()).build();
        Application application = Application.builder().id(applicationView.getId()).name(applicationView.getName())
                .externalId(applicationView.getExternalId()).teamId(team.getId()).build();
        applicationService.forceUpdateExternalId(application, team);
    }

    public TeamMember addTeamMemberByExternalAlias(String teamName, String externalAlias, TeamMemberRole role) {
        Member member = getMemberByExternalAlias(externalAlias);
        teamMemberService.checkTeamMembershipLimit(member.getId());
        Team team = teamService.getTeamByName(teamName);
        return teamMemberService.addTeamMember(team, member, role);
    }

    public TeamMember addTeamMemberByExternalId(String teamName, String externalId, TeamMemberRole role) {
        Member member = getMemberByExternalId(externalId);
        teamMemberService.checkTeamMembershipLimit(member.getId());
        Team team = teamService.getTeamByName(teamName);
        return teamMemberService.addTeamMember(team, member, role);
    }

    public TeamMember updateTeamMemberByExternalAlias(String teamName, String externalAlias, TeamMemberRole role) {
        Team team = teamService.getTeamByName(teamName);
        Member member = memberService.getMemberByExternalAlias(externalAlias);
        return teamMemberService.updateTeamMember(team, member, role);
    }

    public TeamMember updateTeamMemberByExternalId(String teamName, String externalId, TeamMemberRole role) {
        Team team = teamService.getTeamByName(teamName);
        Member member = memberService.getMemberByExternalId(externalId);
        return teamMemberService.updateTeamMember(team, member, role);
    }

    public void deleteTeamMemberByExternalAlias(String teamName, String externalAlias) {
        Team team = teamService.getTeamByName(teamName);
        Member member = memberService.getMemberByExternalAlias(externalAlias);
        teamMemberService.removeMemberFromTeam(team, member);
    }

    public void deleteTeamMemberByExternalId(String teamName, String externalId) {
        Team team = teamService.getTeamByName(teamName);
        Member member = memberService.getMemberByExternalId(externalId);
        teamMemberService.removeMemberFromTeam(team, member);
    }

    public Member updateMemberByExternalAlias(MemberRequest request) {
        Member member = memberService.getMemberByExternalAlias(request.getExternalAlias());
        return updateMember(request, member);
    }

    public Member updateMemberByExternalId(MemberRequest request) {
        Member member = memberService.getMemberByExternalId(request.getExternalId());
        return updateMember(request, member);
    }

    public Member updateMember(MemberRequest request, Member member) {
        // overlay updatable team fields from request
        member.setName(request.getName());
        member.setEmail(request.getEmail());
        return memberService.updateMember(member);
    }

    public ApplicationTransfer createApplicationTransfer(ApplicationTransferRequest request) {
        Application app = applicationService.getApplicationByName(request.getApplicationName());
        Team sourceTeam = teamService.getTeamByName(request.getSourceTeamName());
        Team recieveTeam = teamService.getTeamByName(request.getReceiveTeamName());
        return applicationTransferService.createApplicationTransfer(ApplicationTransfer.builder()
                .applicationId(app.getId())
                .sourceTeamId(sourceTeam.getId())
                .receiveTeamId(recieveTeam.getId())
                .comments(request.getComments())
                .build());
    }

    public ApplicationTransfer updateApplicationTransfer(ApplicationTransferRequest request, ApplicationTransferStatus status) {
        Application app = applicationService.getApplicationByName(request.getApplicationName());
        ApplicationTransfer applicationTransfer = applicationTransferService.getApplicationTransfersForApp(app.getId())
                .stream().max(Comparator.comparing(ApplicationTransfer::getCreateTimestamp)).orElse(null); // get latest
        if (applicationTransfer != null) {
            if (!StringUtils.isEmpty(request.getSourceTeamName())) {
                applicationTransfer.setSourceTeamId(teamService.getTeamByName(request.getSourceTeamName()).getId());
            }
            if (!StringUtils.isEmpty(request.getReceiveTeamName())) {
                applicationTransfer.setReceiveTeamId(teamService.getTeamByName(request.getReceiveTeamName()).getId());
            }
            applicationTransfer.setStatus(status);
            applicationTransfer.setComments(request.getComments());
            return applicationTransferService.updateApplicationTransfer(applicationTransfer);
        } else {
            throw new RecordNotFoundException(APPLICATION_TRANSFER_NOT_FOUND);
        }
    }

    public List<TeamView> getAuthorizedTeams() {
        return getAuthorizedTeamsByNames(authorizedTeams);
    }

    public List<TeamView> getAdminAuthorizedTeams() {
        return getAuthorizedTeamsByNames(adminAuthorizedTeams);
    }

    private List<TeamView> getAuthorizedTeamsByNames(List<String> teamNameList) {
        List<TeamView> teams = new ArrayList<>();
        List<String> teamNames = teamNameList != null ?
                teamNameList.stream().filter(n -> !StringUtils.isEmpty(n)).toList() : null;
        if (teamNames != null && !teamNames.isEmpty()) {
            PaginatedTeamResponse response = teamService.getTeams(PaginationInput.builder()
                            .pageSize(teamNames.size()).pageNumber(1).build(),
                    TeamSearchFilter.builder().names(teamNames).build(), null);
            teams = response.getTeams();
        }
        return teams;
    }

    @Transactional
    public AuthClient createAuthClient(AuthClientRequest request) {
        AuthClient authClient = authClientService.createAuthClient(request);
        Team team = teamService.getTeamByName(request.getTeamName());
        teamAuthClientService.addTeamAuthClient(team.getId(), authClient.getId());
        notifyNonUserAuthClientCreated(authClient, team);
        return authClient;
    }

    private void notifyNonUserAuthClientCreated(AuthClient authClient, Team team) {
        if (!SecurityHelper.isNonUserJwt()) {
            return;
        }
        try {
            String callerClientId = SecurityHelper.getCallingClientId();
            notificationService.sendTeamNotification(NotificationRequest.builder()
                    .teamName(team.getName())
                    .subject("New auth client version created (by NON_USER auth client)")
                    .template(nonUserAuthClientCreatedMessageTemplate)
                    .model(buildNonUserAuthClientCreatedModel(authClient, team, callerClientId))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send non-user auth client created notification for clientId={} [v{}]",
                    authClient.getClientId(), authClient.getClientVersion(), e);
        }
    }

    private Map<String, Object> buildNonUserAuthClientCreatedModel(AuthClient authClient, Team team, String callerClientId) {
        Map<String, Object> model = new HashMap<>();
        model.put("teamName", team.getName());
        model.put("newClientId", authClient.getClientId());
        model.put("newClientVersion", authClient.getClientVersion());
        model.put("callerClientId", callerClientId);
        model.put("inRegistryOrCapUI", notificationService.registryOrCapUI());
        return model;
    }

    @Transactional
    public void deleteAuthClient(String clientId, Integer clientVersion) {
        log.info("deleteAuthClient clientId: {} [version:{}]", clientId, clientVersion);
        List<AuthClientView> authClients = clientVersion == null ? authClientService.getAuthClients(clientId) :
                List.of(authClientService.getAuthClient(clientId, clientVersion, null));
        for (AuthClientView authClient : authClients) {
            AuthClientViewTeam team = authClient.getTeams() != null && !authClient.getTeams().isEmpty()
                    ? authClient.getTeams().get(0) : null;
            if (team != null) {
                teamAuthClientService.removeAuthClientFromTeam(team.getId(), authClient.getId());
            } else {
                log.warn("deleteAuthClient: no team association found for auth client {} [v{}]", clientId, authClient.getClientVersion());
            }
            authClientService.deleteAuthClient(clientId, authClient.getClientVersion(), team);
        }
    }

    protected static void validateTeamCanBeDeleted(TeamView teamView) {
        if (TeamType.PERSONAL == teamView.getType()) {
            throw new BadRequestException(ExceptionReason.TEAM_DELETE_PERSONAL);
        }
        // cannot delete a team if it has any associated applications
        if (!teamView.getApplications().isEmpty()) {
            throw new BadRequestException(ExceptionReason.TEAM_DELETE_WITH_APPLICATION);
        }
        if (!CollectionUtils.isEmpty(teamView.getMembers()) && !currentUserIsOnlyTeamMember(teamView.getMembers())) {
            throw new BadRequestException(ExceptionReason.TEAM_DELETE_WITH_MEMBERS);
        }
    }

    // Security check should only allow team owner to delete team, but ensure no other members are associated to the team
    protected static boolean currentUserIsOnlyTeamMember(List<TeamViewMember> teamMembers) {
        return !CollectionUtils.isEmpty(teamMembers) && teamMembers.size() == 1 &&
                SecurityHelper.getCurrentUserExternalId().equals(teamMembers.get(0).getExternalId());
    }

    private void validateNoDownstreamApplicationsExist(String teamName) {
        downstreamApplicationRestClients.forEach(client -> {
            try {
                boolean teamExists = getRetryTemplate.execute(context -> fetch(client, teamName));
                if (teamExists) {
                    throw new BadRequestException(ExceptionReason.TEAM_DELETE_WITH_DOWNSTREAM_APPLICATION,
                            String.format(ExceptionReason.TEAM_DELETE_WITH_DOWNSTREAM_APPLICATION.label,
                                    client.getLabel()));
                }
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "delete-team-downstream-application-unreachable",
                        ExceptionReason.TEAM_DELETE_DOWNSTREAM_APPLICATION_UNREACHABLE,
                        String.format(ExceptionReason.TEAM_DELETE_DOWNSTREAM_APPLICATION_UNREACHABLE.label, client.getLabel()));
            }
        });
    }

    private boolean fetch(LabeledRestClient client, String teamName) {
        DownstreamAppResponse resp = client.getRestClient().get()
                .uri("/" + teamName)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (resp == null) {
            return false;
        }

        return resp.getTeamExists();
    }
}
