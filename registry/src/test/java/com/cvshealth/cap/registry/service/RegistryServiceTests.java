// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.security.JwtAuthenticationToken;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RegistryServiceTests {

    @InjectMocks
    private RegistryService service;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private NewMemberService newMemberService;

    @Mock
    private MemberService memberService;

    @Mock
    private TeamService teamService;

    @Mock
    private TeamMemberService teamMemberService;

    @Mock
    private AzureAdUserService azureAdUserService;

    @Mock
    AuthClientService authClientService;

    @Mock
    TeamAuthClientService teamAuthClientService;

    @Mock
    ApplicationService applicationService;

    @Mock
    private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void currentUserIsOnlyTeamMember_WhenNoTeamMembers_thenFalse() {
        // given no members
        List<TeamViewMember> teamMembers = Collections.emptyList();

        // when checking for only the current user
        boolean result = RegistryService.currentUserIsOnlyTeamMember(teamMembers);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void currentUserIsOnlyTeamMember_WhenOneTeamMemberAndIsCurrentUser_thenTrue() {
        // given team has only the current user as the team member
        setMockSecurityContext("external-id");
        List<TeamViewMember> teamMembers = List.of(TeamViewMember.builder().externalId("external-id").build());

        // when checking for only the current user
        boolean result = RegistryService.currentUserIsOnlyTeamMember(teamMembers);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void currentUserIsOnlyTeamMember_WhenOneTeamMemberAndIsNotCurrentUser_thenFalse() {
        // given team has only one team member that is not the current user
        setMockSecurityContext("external-id");
        List<TeamViewMember> teamMembers = List.of(TeamViewMember.builder().externalId("some-other-external-id").build());

        // when checking for only the current user
        boolean result = RegistryService.currentUserIsOnlyTeamMember(teamMembers);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void currentUserIsOnlyTeamMember_WhenMoreThanOneTeamMember_thenFalse() {
        // given team has more than one team member
        List<TeamViewMember> teamMembers = List.of(
                TeamViewMember.builder().externalId("member1-external-id").build(),
                TeamViewMember.builder().externalId("member2-external-id").build());

        // when checking for only the current user
        boolean result = RegistryService.currentUserIsOnlyTeamMember(teamMembers);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void validateTeamCanBeDeleted_throwsExceptionWhenPersonalTeam() {
        // given team has an associated application
        TeamView teamView = TeamView.builder().type(TeamType.PERSONAL).build();

        // when validate throws exception
        assertThrows(BadRequestException.class, () -> RegistryService.validateTeamCanBeDeleted(teamView));
    }

    @Test
    public void validateTeamCanBeDeleted_throwsExceptionWhenApplicationsArePresent() {
        // given team has an associated application
        List<TeamViewApplication> applications = List.of(TeamViewApplication.builder().build());
        TeamView teamView = TeamView.builder().applications(applications).build();

        // when validate throws exception
        assertThrows(BadRequestException.class, () -> RegistryService.validateTeamCanBeDeleted(teamView));
    }

    @Test
    public void validateTeamCanBeDeleted_successWithNoApplicationsAndNoMembers() {
        // given team with no applications or members
        List<TeamViewApplication> applications = Collections.emptyList();
        List<TeamViewMember> members = Collections.emptyList();
        TeamView teamView = TeamView.builder().applications(applications).members(members).build();

        // when validate then success
        RegistryService.validateTeamCanBeDeleted(teamView);
    }

    @Test
    public void validateTeamCanBeDeleted_throwsWhenMultipleTeamMembersExist() {
        // given team with multiple team members
        List<TeamViewApplication> applications = Collections.emptyList();
        List<TeamViewMember> teamMembers = List.of(
                TeamViewMember.builder().externalId("member1-external-id").build(),
                TeamViewMember.builder().externalId("member2-external-id").build());
        TeamView teamView = TeamView.builder().applications(applications).members(teamMembers).build();

        // when validate throws exception
        assertThrows(BadRequestException.class, () -> RegistryService.validateTeamCanBeDeleted(teamView));
    }

    @Test
    public void validateTeamCanBeDeleted() {
        // given team with only current user a team member
        setMockSecurityContext("member1-external-id");
        List<TeamViewApplication> applications = Collections.emptyList();
        List<TeamViewMember> teamMembers = List.of(
                TeamViewMember.builder().externalId("member1-external-id").build());
        TeamView teamView = TeamView.builder().applications(applications).members(teamMembers).build();

        // when validate then success
        RegistryService.validateTeamCanBeDeleted(teamView);
    }

    private void setMockSecurityContext(String externalId) {
        SecurityContextHolder.setContext(securityContext);
        Member member = Member.builder().externalId(externalId).build();
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getDetails()).thenReturn(member);
    }

    private void setNonUserJwt(String clientId) {
        JwtAuthenticationToken token = mock(JwtAuthenticationToken.class);
        when(token.isNonUser()).thenReturn(true);
        when(token.getClientId()).thenReturn(clientId);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void setUserJwt() {
        JwtAuthenticationToken token = mock(JwtAuthenticationToken.class);
        when(token.isNonUser()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    public void verifyAuthClientMemberRoles() {
        // given
        String clientId = "nonuser-client";
        Long memberId = 1L;
        Long teamId = 10L;
        TeamMemberRole role = TeamMemberRole.OWNER;
        Team team = Team.builder().id(teamId).build();
        AuthClientView authClient = AuthClientView.builder()
                .clientId(clientId).clientVersion(1)
                .teams(List.of(AuthClientViewTeam.builder().id(teamId).build()))
                .build();

        when(authClientService.getAuthClient(clientId, null, null)).thenReturn(authClient);
        when(teamService.getTeam(teamId)).thenReturn(team); // verify valid team
        when(teamMemberService.getTeamMemberRole(teamId, memberId)).thenReturn(role);

        // when
        boolean result = service.verifyAuthClientMemberRoles(clientId, memberId, role);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void deleteAuthClient_withVersion() {
        // given
        String clientId = "nonuser-client";
        Integer clientVersion = 1;
        Long teamId = 1L;
        AuthClientView authClient = AuthClientView.builder()
                .clientId(clientId).clientVersion(clientVersion)
                .teams(List.of(AuthClientViewTeam.builder().id(teamId).build()))
                .build();

        when(authClientService.getAuthClients(clientId)).thenReturn(List.of(authClient));

        // when
        service.deleteAuthClient(clientId, null);

        // then
        verify(teamAuthClientService, times(1)).removeAuthClientFromTeam(teamId, authClient.getId());
        verify(authClientService, times(1)).deleteAuthClient(eq(clientId), eq(clientVersion), any());
    }

    @Test
    public void deleteAuthClient_noVersion() {
        // given
        String clientId = "nonuser-client";
        Integer clientVersion = 2;
        Long teamId = 1L;
        AuthClientView authClient = AuthClientView.builder().id(10L)
                .clientId(clientId).clientVersion(clientVersion)
                .teams(List.of(AuthClientViewTeam.builder().id(teamId).build()))
                .build();

        when(authClientService.getAuthClient(clientId, clientVersion, null)).thenReturn(authClient);

        // when
        service.deleteAuthClient(clientId, clientVersion);

        // then
        verify(teamAuthClientService, times(1)).removeAuthClientFromTeam(teamId, authClient.getId());
        verify(authClientService, times(1)).deleteAuthClient(eq(clientId), eq(clientVersion), any());
    }

    @Test
    public void verifyTeamAuthClientRoles_OWNER_required_roleMatches() {
        // given
        Long teamId = 1L;
        String clientId = "authclient";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.OWNER);
        List<TeamViewAuthClient> authClients = List.of(TeamViewAuthClient.builder().id(teamId).clientId(clientId).role(TeamMemberRole.OWNER).build());
        TeamView team = TeamView.builder().id(teamId).authClients(authClients).build();
        when(teamService.getTeamView(teamId, null)).thenReturn(team);

        // when
        boolean result = service.verifyTeamAuthClientRoles(teamId, clientId, roles);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void verifyTeamAuthClientRoles_OWNER_required_roleMismatch() {
        // given
        Long teamId = 1L;
        String clientId = "authclient";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.OWNER);
        List<TeamViewAuthClient> authClients = List.of(TeamViewAuthClient.builder().id(teamId).clientId(clientId).role(TeamMemberRole.MEMBER).build());
        TeamView team = TeamView.builder().id(teamId).authClients(authClients).build();
        when(teamService.getTeamView(teamId, null)).thenReturn(team);

        // when
        boolean result = service.verifyTeamAuthClientRoles(teamId, clientId, roles);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void verifyTeamAuthClientRoles_MEMBER_required() {
        // given
        Long teamId = 1L;
        String clientId = "authclient";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);
        List<TeamViewAuthClient> authClients = List.of(TeamViewAuthClient.builder().id(teamId).clientId(clientId).clientVersion(1).role(TeamMemberRole.MEMBER).build());
        TeamView team = TeamView.builder().id(teamId).authClients(authClients).build();
        when(teamService.getTeamView(teamId, null)).thenReturn(team);

        // when
        boolean result = service.verifyTeamAuthClientRoles(teamId, clientId, roles);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void verifyTeamAuthClientRoles_MEMBER_required_withClientTeam_mismatch() {
        // given
        Long teamId = 1L;
        String clientId = "authclient";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);
        List<TeamViewAuthClient> authClients = List.of(TeamViewAuthClient.builder().id(teamId).clientId("authclient2").clientVersion(1).build());
        TeamView team = TeamView.builder().id(teamId).authClients(authClients).build();
        when(teamService.getTeamView(teamId, null)).thenReturn(team);

        // when
        boolean result = service.verifyTeamAuthClientRoles(teamId, clientId, roles);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void verifyTeamAuthClientRoles_noTeamId_returnsBadRequest() {
        // given
        Long teamId = null;
        String clientId = "authclient";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        // when
        assertThrows(BadRequestException.class, () -> service.verifyTeamAuthClientRoles(teamId, clientId, roles));
    }

    @Test
    public void updateApplication_handsOnTheClassificationTheCallerSentAndLeavesTheDecisionToApplicationService() {
        ApplicationRequest request = ApplicationRequest.builder().name("test-app").teamName("test-team")
                .complianceTypes(Set.of("pci")).informationClassification(InformationClassification.RESTRICTED).build();
        Application app = Application.builder().id(1L).name("test-app").teamId(50L)
                .informationClassification(InformationClassification.PROPRIETARY).build();
        when(teamService.getTeamByName("test-team")).thenReturn(Team.builder().id(100L).name("test-team").build());
        when(applicationService.getApplicationByName("test-app")).thenReturn(app);
        when(applicationService.updateApplication(any(Application.class), eq("test-team"))).thenReturn(app);

        service.updateApplication(request);

        // the value read from storage is not carried along; only what was sent is
        verify(applicationService).updateApplication(argThat(application ->
                application.getInformationClassification() == InformationClassification.RESTRICTED), eq("test-team"));
    }

    @Test
    public void updateApplication_setsPropertiesCorrectly() {
        ApplicationRequest request = ApplicationRequest.builder()
                .name("test-app")
                .teamName("test-team")
                .description("test description")
                .complianceTypes(Set.of("pci"))
                .priority(1)
                .externalId("EXT-123")
                .cloud("aws")
                .tenant("prod")
                .aiEnabled(true)
                .dataPerimeter(DataPerimeter.RESTRICTED)
                .build();

        Team team = Team.builder().id(100L).name("test-team").build();
        Application app = Application.builder()
                .id(1L)
                .name("test-app")
                .teamId(50L)
                .description("old description")
                .complianceTypes(Set.of("pii"))
                .priority(2)
                .externalId("OLD-123")
                .cloud("gcp")
                .tenant("dev")
                .aiEnabled(false)
                .dataPerimeter(DataPerimeter.UNRESTRICTED)
                .build();
        Application updatedApp = Application.builder()
                .id(1L)
                .name("test-app")
                .teamId(100L)
                .description("test description")
                .complianceTypes(Set.of("pci"))
                .priority(1)
                .externalId("EXT-123")
                .cloud("aws")
                .tenant("prod")
                .aiEnabled(true)
                .dataPerimeter(DataPerimeter.RESTRICTED)
                .build();

        when(teamService.getTeamByName("test-team")).thenReturn(team);
        when(applicationService.getApplicationByName("test-app")).thenReturn(app);
        when(applicationService.updateApplication(any(Application.class), eq("test-team"))).thenReturn(updatedApp);

        Application result = service.updateApplication(request);

        assertThat(result).isEqualTo(updatedApp);
        verify(applicationService).updateApplication(argThat(application ->
                application.getTeamId().equals(100L) &&
                        application.getDescription().equals("test description") &&
                        application.getComplianceTypes().equals(Set.of("pci")) &&
                        application.getInformationClassification() == null &&
                        application.getPriority().equals(1) &&
                        application.getExternalId().equals("EXT-123") &&
                        application.getCloud().equals("aws") &&
                        application.getTenant().equals("prod") &&
                        application.isAiEnabled() &&
                        application.getDataPerimeter() == DataPerimeter.RESTRICTED
        ), eq("test-team"));
    }

    @Test
    public void updateApplication_setsPropertiesCorrectly_NullCloudAndTenant() {
        ApplicationRequest request = ApplicationRequest.builder()
                .name("test-app")
                .teamName("test-team")
                .description("test description")
                .complianceTypes(Set.of("pci"))
                .priority(1)
                .externalId("EXT-123")
                .cloud(null)
                .tenant(null)
                .aiEnabled(null)
                .dataPerimeter(null)
                .build();

        Team team = Team.builder().id(100L).name("test-team").build();
        Application app = Application.builder()
                .id(1L)
                .name("test-app")
                .teamId(50L)
                .description("old description")
                .complianceTypes(Set.of("pii"))
                .priority(2)
                .externalId("OLD-123")
                .cloud("gcp")
                .tenant("dev")
                .aiEnabled(true)
                .dataPerimeter(DataPerimeter.RESTRICTED)
                .build();
        Application updatedApp = Application.builder()
                .id(1L)
                .name("test-app")
                .teamId(100L)
                .description("test description")
                .complianceTypes(Set.of("pci"))
                .priority(1)
                .externalId("EXT-123")
                .cloud("gcp")
                .tenant("dev")
                .aiEnabled(true)
                .dataPerimeter(DataPerimeter.RESTRICTED)
                .build();

        when(teamService.getTeamByName("test-team")).thenReturn(team);
        when(applicationService.getApplicationByName("test-app")).thenReturn(app);
        when(applicationService.updateApplication(any(Application.class), eq("test-team"))).thenReturn(updatedApp);

        Application result = service.updateApplication(request);

        assertThat(result).isEqualTo(updatedApp);
        verify(applicationService).updateApplication(argThat(application ->
                application.getTeamId().equals(100L) &&
                        application.getDescription().equals("test description") &&
                        application.getComplianceTypes().equals(Set.of("pci")) &&
                        application.getInformationClassification() == null &&
                        application.getPriority().equals(1) &&
                        application.getExternalId().equals("EXT-123") &&
                        application.getCloud().equals("gcp") &&
                        application.getTenant().equals("dev") &&
                        application.isAiEnabled() &&
                        application.getDataPerimeter() == DataPerimeter.RESTRICTED
        ), eq("test-team"));
    }


    @Test
    public void updateApplicationExternalId() {
        // given
        String teamName = "teamName";
        String applicationName = "applicationName";
        String oldExternalId = "oldExternalId";
        String newExternalId = "newExternalId";
        Team team = Team.builder().id(100L).name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);
        Application application = Application.builder().id(1L).teamId(team.getId()).externalId(oldExternalId).build();
        when(applicationService.getApplicationByName(applicationName)).thenReturn(application);
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(applicationName)
                .oldExternalId(oldExternalId).newExternalId(newExternalId).build();
        when(applicationService.forceUpdateExternalId(application, team)).thenReturn(Application.builder().id(1L).teamId(team.getId()).externalId(newExternalId).build());

        // when
        Application result = service.updateApplicationExternalId(patchRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getExternalId()).isEqualTo(newExternalId);
    }

    @Test
    public void updateApplicationExternalId_NoTeamName() {
        // given
        String applicationName = "applicationName";
        String oldExternalId = "oldExternalId";
        String newExternalId = "oldExternalId";
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(null).applicationName(applicationName)
                .oldExternalId(oldExternalId).newExternalId(newExternalId).build();

        // when
        assertThrows(BadRequestException.class, () -> service.updateApplicationExternalId(patchRequest));

        // then
        verifyNoInteractions(teamService);
        verifyNoInteractions(applicationService);
    }

    @Test
    public void updateApplicationExternalId_NoApplicationName() {
        // given
        String teamName = "teamName";
        String oldExternalId = "oldExternalId";
        String newExternalId = "oldExternalId";
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(null)
                .oldExternalId(oldExternalId).newExternalId(newExternalId).build();

        // when
        assertThrows(BadRequestException.class, () -> service.updateApplicationExternalId(patchRequest));

        // then
        verifyNoInteractions(teamService);
        verifyNoInteractions(applicationService);
    }

    @Test
    public void updateApplicationExternalId_NoChange() {
        // given
        String teamName = "teamName";
        String applicationName = "applicationName";
        String oldExternalId = "oldExternalId";
        String newExternalId = "oldExternalId";
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(applicationName).oldExternalId(oldExternalId).newExternalId(newExternalId).build();

        // when
        assertThrows(BadRequestException.class, () -> service.updateApplicationExternalId(patchRequest));

        // then
        verifyNoInteractions(teamService);
        verifyNoInteractions(applicationService);
    }

    @Test
    public void updateApplicationExternalId_InvalidTeamName() {
        // given
        String teamName = "teamName";
        String applicationName = "applicationName";
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(applicationName).oldExternalId("oldExternalId").newExternalId("newExternalId").build();
        when(teamService.getTeamByName(teamName)).thenThrow(RecordNotFoundException.class);

        // when
        assertThrows(RecordNotFoundException.class, () -> service.updateApplicationExternalId(patchRequest));
    }

    @Test
    public void updateApplicationExternalId_InvalidApplicationName() {
        // given
        String teamName = "teamName";
        String applicationName = "applicationName";
        Team team = Team.builder().id(100L).name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(applicationName).oldExternalId("oldExternalId").newExternalId("newExternalId").build();
        when(applicationService.getApplicationByName(applicationName)).thenThrow(RecordNotFoundException.class);

        // when
        assertThrows(RecordNotFoundException.class, () -> service.updateApplicationExternalId(patchRequest));
    }

    @Test
    public void updateApplicationExternalId_ExternalIdMismatch() {
        // given
        String teamName = "teamName";
        String applicationName = "applicationName";
        String oldExternalId = "oldExternalId";
        String newExternalId = "newExternalId";
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(applicationName).oldExternalId(oldExternalId).newExternalId(newExternalId).build();
        Team team = Team.builder().id(100L).name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);
        Application application = Application.builder().id(1L).teamId(team.getId()).externalId("mismatchedExternalId").build();
        when(applicationService.getApplicationByName(applicationName)).thenReturn(application);

        // when
        assertThrows(BadRequestException.class, () -> service.updateApplicationExternalId(patchRequest));

        // then
        verifyNoMoreInteractions(applicationService);
    }

    @Test
    public void createAuthClient_whenCallerIsNonUserType_sendsTeamNotification() throws NotificationServiceException {
        // given
        String callerClientId = "caller-non-user-client";
        String newClientId = "new-client";
        String teamName = "my-team";
        setNonUserJwt(callerClientId);
        ReflectionTestUtils.setField(service, "nonUserAuthClientCreatedMessageTemplate", "text/non-user-auth-client-created.ftl");

        AuthClientRequest request = AuthClientRequest.builder().clientId(newClientId).teamName(teamName).build();
        AuthClient created = AuthClient.builder().clientId(newClientId).clientVersion(2).build();
        Team team = Team.builder().id(10L).name(teamName).build();

        when(authClientService.createAuthClient(request)).thenReturn(created);
        when(teamService.getTeamByName(teamName)).thenReturn(team);

        // when
        AuthClient result = service.createAuthClient(request);

        // then
        assertThat(result).isEqualTo(created);
        verify(notificationService).sendTeamNotification(any(NotificationRequest.class));
    }

    @Test
    public void createAuthClient_whenCallerIsUser_doesNotSendNotification() {
        // given
        setUserJwt();
        ReflectionTestUtils.setField(service, "nonUserAuthClientCreatedMessageTemplate", "text/non-user-auth-client-created.ftl");

        AuthClientRequest request = AuthClientRequest.builder().clientId("new-client").teamName("my-team").build();
        AuthClient created = AuthClient.builder().clientId("new-client").clientVersion(1).build();
        Team team = Team.builder().id(10L).name("my-team").build();

        when(authClientService.createAuthClient(request)).thenReturn(created);
        when(teamService.getTeamByName("my-team")).thenReturn(team);

        // when
        service.createAuthClient(request);

        // then
        verifyNoInteractions(notificationService);
    }

    // Note: Not a valid scenario currently for a USER auth client to create a new auth client version,
    // but if it does happen, notification should be sent for ANY non-human create action.
    @Test
    public void createAuthClient_whenCallerIsNonUserJwtButNotNonUserType_sendsNotification() throws NotificationServiceException {
        // given
        String callerClientId = "caller-user-type-client";
        setNonUserJwt(callerClientId);
        ReflectionTestUtils.setField(service, "nonUserAuthClientCreatedMessageTemplate", "text/non-user-auth-client-created.ftl");

        AuthClientRequest request = AuthClientRequest.builder().clientId("new-client").teamName("my-team").build();
        AuthClient created = AuthClient.builder().clientId("new-client").clientVersion(1).build();
        Team team = Team.builder().id(10L).name("my-team").build();

        when(authClientService.createAuthClient(request)).thenReturn(created);
        when(teamService.getTeamByName("my-team")).thenReturn(team);

        // when
        service.createAuthClient(request);

        // then
        verify(notificationService).sendTeamNotification(any(NotificationRequest.class));
    }

    @Test
    public void createAuthClient_whenNotificationFails_doesNotThrow() throws NotificationServiceException {
        // given
        String callerClientId = "caller-non-user-client";
        setNonUserJwt(callerClientId);
        ReflectionTestUtils.setField(service, "nonUserAuthClientCreatedMessageTemplate", "text/non-user-auth-client-created.ftl");

        AuthClientRequest request = AuthClientRequest.builder().clientId("new-client").teamName("my-team").build();
        AuthClient created = AuthClient.builder().clientId("new-client").clientVersion(1).build();
        Team team = Team.builder().id(10L).name("my-team").build();

        when(authClientService.createAuthClient(request)).thenReturn(created);
        when(teamService.getTeamByName("my-team")).thenReturn(team);
        doThrow(new NotificationServiceException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "test", "notification error"))
                .when(notificationService).sendTeamNotification(any(NotificationRequest.class));

        // when / then — notification failure must not propagate
        assertDoesNotThrow(() -> service.createAuthClient(request));
    }

    // verifyApplicationRoles(Application, String clientId, List) overload

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_applicationIsNull_returnsFalse() {
        // given
        Application application = null;
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isFalse();
        verify(applicationService, never()).getApplicationView(anyLong(), any());
    }

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_OWNER_required_roleMatches_returnsTrue() {
        // given
        Application application = Application.builder().id(1L).teamId(10L).build();
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.OWNER);

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .role(TeamMemberRole.OWNER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_roleMismatch_returnsFalse() {
        // given
        Application application = Application.builder().id(1L).teamId(10L).build();
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.OWNER);

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .role(TeamMemberRole.MEMBER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_clientIdMatchesDirect_returnsTrue() {
        // given
        Application application = Application.builder().id(1L).teamId(10L).build();
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .role(TeamMemberRole.MEMBER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isTrue();
        verify(applicationService).getApplicationView(1L, null);
    }

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_clientIdMatchesVersionedId_returnsTrue() {
        // given
        Application application = Application.builder().id(1L).teamId(10L).build();
        String clientId = "my-client-v2";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(2)
                .role(TeamMemberRole.MEMBER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_clientIdMatchesNullVersion_returnsTrue() {
        // given — null clientVersion falls back to AUTH_CLIENT_FIRST_VERSION (1), so no "-vN" suffix
        Application application = Application.builder().id(1L).teamId(10L).build();
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(null)
                .role(TeamMemberRole.MEMBER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void verifyApplicationRoles_byApplicationAndClientId_noMatchingAuthClient_returnsFalse() {
        // given
        Application application = Application.builder().id(1L).teamId(10L).build();
        String clientId = "unknown-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(application, clientId, roles);

        // then
        assertThat(result).isFalse();
    }

    // verifyApplicationRoles(Long applicationId, String clientId, List) overload

    @Test
    public void verifyApplicationRoles_byApplicationIdAndClientId_nullApplicationId_throwsBadRequest() {
        // given
        Long applicationId = null;
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        // when / then
        assertThrows(BadRequestException.class,
                () -> service.verifyApplicationRoles(applicationId, clientId, roles));
        verify(applicationService, never()).getApplication(any());
    }

    @Test
    public void verifyApplicationRoles_byApplicationIdAndClientId_clientIdMatches_returnsTrue() {
        // given
        Long applicationId = 1L;
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);
        Application application = Application.builder().id(applicationId).teamId(10L).build();

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .role(TeamMemberRole.MEMBER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(applicationId).team(team).build();

        when(applicationService.getApplication(applicationId)).thenReturn(application);
        when(applicationService.getApplicationView(applicationId, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(applicationId, clientId, roles);

        // then
        assertThat(result).isTrue();
        verify(applicationService).getApplication(applicationId);
        verify(applicationService).getApplicationView(applicationId, null);
    }

    // verifyApplicationRoles(String name, String clientId, List) overload

    @Test
    public void verifyApplicationRoles_byNameAndClientId_nullName_throwsBadRequest() {
        // given
        String name = null;
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);

        // when / then
        assertThrows(BadRequestException.class,
                () -> service.verifyApplicationRoles(name, clientId, roles));
        verify(applicationService, never()).getApplicationByName(any());
    }

    @Test
    public void verifyApplicationRoles_byNameAndClientId_clientIdMatches_returnsTrue() {
        // given
        String name = "my-app";
        String clientId = "my-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);
        Application application = Application.builder().id(1L).teamId(10L).build();

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .role(TeamMemberRole.MEMBER)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationByName(name)).thenReturn(application);
        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(name, clientId, roles);

        // then
        assertThat(result).isTrue();
        verify(applicationService).getApplicationByName(name);
        verify(applicationService).getApplicationView(1L, null);
    }

    @Test
    public void verifyApplicationRoles_byNameAndClientId_clientIdDoesNotMatch_returnsFalse() {
        // given
        String name = "my-app";
        String clientId = "unknown-client";
        List<TeamMemberRole> roles = List.of(TeamMemberRole.MEMBER);
        Application application = Application.builder().id(1L).teamId(10L).build();

        ApplicationViewAuthClient authClient = ApplicationViewAuthClient.builder()
                .clientId("my-client")
                .clientVersion(1)
                .build();
        ApplicationViewTeam team = ApplicationViewTeam.builder()
                .authClients(List.of(authClient))
                .build();
        ApplicationView applicationView = ApplicationView.builder().id(1L).team(team).build();

        when(applicationService.getApplicationByName(name)).thenReturn(application);
        when(applicationService.getApplicationView(1L, null)).thenReturn(applicationView);

        // when
        boolean result = service.verifyApplicationRoles(name, clientId, roles);

        // then
        assertThat(result).isFalse();
    }
}
