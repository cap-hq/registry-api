// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import com.cvshealth.cap.registry.model.AuthClientRequest;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.AuthClientView;
import com.cvshealth.cap.registry.model.AuthClientViewTeam;
import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamView;
import com.cvshealth.cap.registry.model.TeamViewAuthClient;
import com.cvshealth.cap.registry.model.TeamViewMember;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.service.RegistryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CustomPermissionEvaluatorTests {

    private static final List<TeamMemberRole> TEAM_AUTH_CLIENT_ROLES = List.of(TeamMemberRole.OWNER, TeamMemberRole.MEMBER);

    @Mock
    private RegistryService registryService;

    @InjectMocks
    private CustomPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void isClientIdValid_returnFalse_whenBothAreNull() {
        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid(null, null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isClientIdValid_returnFalse_whenRequestedIsBlank() {
        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid("", "authClientId");

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isClientIdValid_returnFalse_whenAuthClientIsBlank() {
        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid("requestedClientId", "");

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isClientIdValid_returnTrue_whenMatch_andNoJwt() {
        // given
        SecurityContextHolder.getContext().setAuthentication(null);

        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid("clientId", "clientId");

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void isClientIdValid_returnFalse_whenMismatch_andNoJwt() {
        // given
        SecurityContextHolder.getContext().setAuthentication(null);

        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid("clientId", "clientId-v2");

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isClientIdValid_returnTrue_whenJwtClientIdMatch() {
        // given
        String clientId = "clientId";
        String clientName = "clientId";
        setJwt(Map.of("clientId", clientId, "clientName", clientName));

        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid(clientId, clientId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void isClientIdValid_returnTrue_whenJwtClientNameMatch() {
        // given
        String clientId = "clientId-v2";
        String clientName = "clientId";
        setJwt(Map.of("clientId", clientId, "clientName", clientName));

        // when
        boolean result = CustomPermissionEvaluator.isClientIdValid(clientName, clientId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void validateAuthorizedUser_asNonUser_handlesNullAuthClients() {
        String clientId = "snowball-client";
        setNonUserJwt(clientId);

        TeamView team = TeamView.builder().build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThrows(ApiException.class, () ->
                evaluator.hasPermission(auth, null, "Application", "create"));
    }

    @Test
    public void validateAuthorizedUser_asUser_handlesNullMembers() {
        String externalId = "user123";
        setJwt(Map.of("sub", externalId));

        TeamView team = TeamView.builder().build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThrows(ApiException.class, () ->
                evaluator.hasPermission(auth, null, "Application", "create"));
    }

    @Test
    public void createAuthClient_authClientTypeNonUser_sameTeam_allowed() {
        String callerClientId = "client-credentials-flow";
        String requestedClientId = "client-credentials-flow";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(requestedClientId)
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isTrue();
    }

    @Test
    public void createAuthClient_authClientTypeUser_sameTeam_denied() {
        String clientId = "auth-code-flow";
        setNonUserJwt(clientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(clientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(clientId)
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(clientId).type(AuthClientType.USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(clientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(clientId)).thenReturn(callerAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isFalse();
    }

    @Test
    public void createAuthClient_authClientTypeUserPkce_sameTeam_denied() {
        String clientId = "auth-code-flow-pkce";
        setNonUserJwt(clientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(clientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(clientId)
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(clientId).type(AuthClientType.USER_PKCE).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(clientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(clientId)).thenReturn(callerAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isFalse();
    }

    @Test
    public void createAuthClient_nonUserType_differentTeam_denied() {
        String clientId = "client-credentials-flow";
        setNonUserJwt(clientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(clientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-2")
                .clientId(clientId)
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isFalse();
    }

    @Test
    public void createAuthClient_nonUserType_sameTeam_noPriorVersion_denied() {
        String callerClientId = "client-credentials-flow";
        String requestedClientId = "brand-new-client-id";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(requestedClientId)
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(requestedClientId)).thenReturn(null);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isFalse();
    }

    @Test
    public void createAuthClient_nonUserWithVersionedClientId_stripsVersionSuffix_andAllowed() {
        // Caller JWT has clientId with a version suffix but no clientName claim.
        // getClientName must strip the suffix to resolve the base client type.
        String versionedClientId = "svc-client-v2";
        String baseClientId = "svc-client";
        setJwt(Map.of("clientId", versionedClientId)); // no clientName → getSecond() is null

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(versionedClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("svc-client-v3")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(baseClientId).type(AuthClientType.NON_USER).build();
        AuthClientView requestedAuthClient = AuthClientView.builder().clientId("svc-client-v3").build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(versionedClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(baseClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient("svc-client-v3")).thenReturn(requestedAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isTrue();
    }

    @Test
    public void createAuthClient_nonUserWithVersionedClientId_multiDigitSuffix_stripsVersionAndAllowed() {
        String versionedClientId = "svc-client-v12";
        String baseClientId = "svc-client";
        setJwt(Map.of("clientId", versionedClientId));

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(versionedClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("svc-client-v13")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(baseClientId).type(AuthClientType.NON_USER).build();
        AuthClientView requestedAuthClient = AuthClientView.builder().clientId("svc-client-v13").build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(versionedClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(baseClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient("svc-client-v13")).thenReturn(requestedAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isTrue();
    }

    @Test
    public void createAuthClient_nonUserWithVersionedClientId_stripsVersionSuffix_andDenied_whenRequestedClientNotFound() {
        String versionedClientId = "svc-client-v2";
        String baseClientId = "svc-client";
        setJwt(Map.of("clientId", versionedClientId));

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(versionedClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("brand-new-client")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(baseClientId).type(AuthClientType.NON_USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(versionedClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(baseClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient("brand-new-client")).thenReturn(null);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isFalse();
    }

    @Test
    public void createAuthClient_nonUserWithVersionedClientId_stripsVersionSuffix_andDenied_whenBaseTypeIsUser() {
        String versionedClientId = "user-client-v1";
        String baseClientId = "user-client";
        setJwt(Map.of("clientId", versionedClientId));

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(versionedClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("user-client-v2")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(baseClientId).type(AuthClientType.USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(versionedClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(baseClientId)).thenReturn(callerAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isFalse();
    }

    @Test
    public void createAuthClient_asUser_teamOwner_stillAllowed() {
        String externalId = "user-owner-123";
        setUserJwt(externalId);

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("brand-new-client-id")
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(42L).build());
        when(registryService.verifyTeamMemberRoles(eq("Team-1"), eq(42L), eq(List.of(TeamMemberRole.OWNER))))
                .thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean result = evaluator.hasPermission(auth, createRequest, "AuthClient", "create");

        assertThat(result).isTrue();
    }

    @Test
    public void createTeam_asNonUser_denied() {
        setNonUserJwt("svc-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, null, "Team", "create")).isFalse();
    }

    @Test
    public void createTeam_asUser_allowed() {
        String externalId = "human-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(900L).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, null, "Team", "create")).isTrue();
    }

    @Test
    public void createMember_asUser_allowed() {
        String externalId = "human-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(901L).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, null, "Member", "create")).isTrue();
    }

    @Test
    public void createMember_asNonUser_allowed() {
        setNonUserJwt("machine-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, null, "Member", "create")).isTrue();
    }

    @Test
    public void createApplication_asUser_whenTeamMember_allowed() {
        Long teamId = 10L;
        String externalId = "dev-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(42L).build());
        when(registryService.verifyTeamMemberRoles(eq(teamId), eq(42L), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, teamId, "Application", "create")).isTrue();
    }

    @Test
    public void createApplication_asUser_whenNotOnTeam_denied() {
        Long teamId = 10L;
        String externalId = "dev-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(42L).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, teamId, "Application", "create")).isFalse();
    }

    @Test
    public void createTeamMember_asUser_whenTeamOwner_allowed() {
        Long teamId = 55L;
        String externalId = "owner-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(3L).build());
        when(registryService.verifyTeamMemberRoles(eq(teamId), eq(3L), eq(List.of(TeamMemberRole.OWNER))))
                .thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, teamId, "TeamMember", "create")).isTrue();
    }

    @Test
    public void createTeamMember_asUser_whenNotTeamOwner_denied() {
        Long teamId = 55L;
        String externalId = "member-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(3L).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, teamId, "TeamMember", "create")).isFalse();
    }

    @Test
    public void createApplicationTransfer_asUser_whenTeamOwner_allowed() {
        Long teamId = 88L;
        String externalId = "owner-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(8L).build());
        when(registryService.verifyTeamMemberRoles(eq(teamId), eq(8L), eq(List.of(TeamMemberRole.OWNER))))
                .thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, teamId, "ApplicationTransfer", "create")).isTrue();
    }

    @Test
    public void createApplicationTransfer_asUser_whenNotTeamOwner_denied() {
        Long teamId = 88L;
        String externalId = "member-user";
        setUserJwt(externalId);
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(8L).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, teamId, "ApplicationTransfer", "create")).isFalse();
    }

    @Test
    public void createAuthClient_asUser_notTeamOwner_denied() {
        String externalId = "plain-member";
        setUserJwt(externalId);
        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("new-client")
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(42L).build());
        when(registryService.verifyTeamMemberRoles(eq("Team-1"), eq(42L), eq(List.of(TeamMemberRole.OWNER))))
                .thenReturn(false);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, createRequest, "AuthClient", "create")).isFalse();
    }

    @Test
    public void createAuthClient_nonUser_getAuthClientTypeThrows_denied() {
        String callerClientId = "client-credentials-flow";
        String requestedClientId = "client-credentials-flow";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        AuthClientRequest createRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId(requestedClientId)
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(null);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, createRequest, "AuthClient", "create")).isFalse();
    }

    @Test
    public void createAuthClient_nonUser_targetNotAuthClientRequest_denied() {
        String callerClientId = "client-credentials-flow";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient authClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().name("Team-1").authClients(List.of(authClient)).members(List.of()).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES)))
                .thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "Team-1", "AuthClient", "create")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenCallerIsAuthClient_typeNonUser() {
        setNonUserJwt("credentials-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "target-client-id", "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenCallerIsAuthClient_typeUser() {
        setNonUserJwt("user-flow-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "target-client-id", "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenCallerIsAuthClient_typeUserPkce() {
        setNonUserJwt("user-pkce-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "target-client-id", "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_allowed_whenNonUserCaller_sameTeam_targetIsUser() {
        String callerClientId = "non-user-client";
        String targetClientId = "user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();
        AuthClientViewTeam sharedTeam = AuthClientViewTeam.builder().name("Team-1").build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).teams(List.of(sharedTeam)).build();
        AuthClientView existingTargetClient = AuthClientView.builder().clientId(targetClientId).type(AuthClientType.USER).teams(List.of(sharedTeam)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(existingTargetClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isTrue();
    }

    @Test
    public void updateAuthClient_allowed_whenNonUserCaller_sameTeam_targetIsUserPkce() {
        String callerClientId = "non-user-client";
        String targetClientId = "user-pkce-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER_PKCE)
                .teamName("Team-1")
                .build();
        AuthClientViewTeam sharedTeam = AuthClientViewTeam.builder().name("Team-1").build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).teams(List.of(sharedTeam)).build();
        AuthClientView existingTargetClient = AuthClientView.builder().clientId(targetClientId).type(AuthClientType.USER_PKCE).teams(List.of(sharedTeam)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(existingTargetClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isTrue();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_differentTeam_targetIsUser() {
        String callerClientId = "non-user-client";
        String targetClientId = "user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Other-Team")
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Other-Team"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(false);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_sameTeam_targetIsNonUser() {
        String callerClientId = "non-user-client";
        String targetClientId = "another-non-user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.NON_USER)
                .teamName("Team-1")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).build();
        AuthClientView existingTargetClient = AuthClientView.builder().clientId(targetClientId).type(AuthClientType.NON_USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(existingTargetClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_sameTeam_existingTargetClientNotFound() {
        // NON_USER caller on same team, but the target auth client does not exist in the registry
        String callerClientId = "non-user-client";
        String targetClientId = "user-client-to-update";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(null);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_sameTeam_callerIsUserType() {
        // Caller JWT is non-user auth but the registered auth client type is USER, not NON_USER
        String callerClientId = "user-type-client";
        String targetClientId = "another-user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_sameTeam_callerAuthClientNotFound() {
        // NON_USER caller on same team, but resolveCallingAuthClientType returns null (caller not in registry)
        String callerClientId = "unknown-non-user-client";
        String targetClientId = "user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(null);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_sameTeam_targetNotAuthClientRequest() {
        // NON_USER caller passes a plain String (reset-secret flow); getRequestedAuthClient returns null → denied
        String callerClientId = "non-user-client";
        String stringTarget = "target-client-id";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq(stringTarget), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, stringTarget, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_callerHasNoTeams_targetIsUser() {
        // NON_USER caller has no teams registered → isAuthClientAssociatedWithTeam returns false
        String callerClientId = "non-user-client";
        String targetClientId = "user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).teams(List.of()).build();
        AuthClientViewTeam targetTeam = AuthClientViewTeam.builder().name("Team-1").build();
        AuthClientView existingTargetClient = AuthClientView.builder().clientId(targetClientId).type(AuthClientType.USER).teams(List.of(targetTeam)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(existingTargetClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_targetHasNoTeams_targetIsUser() {
        // Target auth client has no teams registered → isAuthClientAssociatedWithTeam returns false
        String callerClientId = "non-user-client";
        String targetClientId = "user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();
        AuthClientViewTeam callerTeam = AuthClientViewTeam.builder().name("Team-1").build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).teams(List.of(callerTeam)).build();
        AuthClientView existingTargetClient = AuthClientView.builder().clientId(targetClientId).type(AuthClientType.USER).teams(List.of()).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(existingTargetClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_denied_whenNonUserCaller_callerAndTargetOnDifferentTeams() {
        // Caller and target share no common team → isAuthClientAssociatedWithTeam returns false
        String callerClientId = "non-user-client";
        String targetClientId = "user-client";
        setNonUserJwt(callerClientId);

        TeamViewAuthClient callerViewAuthClient = TeamViewAuthClient.builder().clientId(callerClientId).build();
        TeamView team = TeamView.builder().authClients(List.of(callerViewAuthClient)).members(List.of()).build();

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId(targetClientId)
                .type(AuthClientType.USER)
                .teamName("Team-1")
                .build();
        AuthClientViewTeam callerTeam = AuthClientViewTeam.builder().name("Team-1").build();
        AuthClientViewTeam targetTeam = AuthClientViewTeam.builder().name("Team-2").build();
        AuthClientView callerAuthClient = AuthClientView.builder().clientId(callerClientId).type(AuthClientType.NON_USER).teams(List.of(callerTeam)).build();
        AuthClientView existingTargetClient = AuthClientView.builder().clientId(targetClientId).type(AuthClientType.USER).teams(List.of(targetTeam)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamAuthClientRoles(eq("Team-1"), eq(callerClientId), eq(TEAM_AUTH_CLIENT_ROLES))).thenReturn(true);
        when(registryService.getAuthClient(callerClientId)).thenReturn(callerAuthClient);
        when(registryService.getAuthClient(targetClientId)).thenReturn(existingTargetClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_allowed_whenUserCaller_isTeamOwner_withAuthClientRequest() {
        // Human user who is a team OWNER updates an auth client via full AuthClientRequest
        String externalId = "team-owner-user";
        setUserJwt(externalId);

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("existing-client")
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(10L).build());
        when(registryService.verifyAuthClientMemberRoles(eq("existing-client"), eq(10L), eq(TeamMemberRole.OWNER))).thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isTrue();
    }

    @Test
    public void updateAuthClient_denied_whenUserCaller_isNotTeamOwner_withAuthClientRequest() {
        // Human user who is only a MEMBER (not OWNER) cannot update an auth client via AuthClientRequest
        String externalId = "plain-member-user";
        setUserJwt(externalId);

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .teamName("Team-1")
                .clientId("existing-client")
                .build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(11L).build());
        when(registryService.verifyAuthClientMemberRoles(eq("existing-client"), eq(11L), eq(TeamMemberRole.OWNER))).thenReturn(false);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, updateRequest, "AuthClient", "update")).isFalse();
    }

    @Test
    public void updateAuthClient_allowed_whenUserCaller_isTeamOwner_withStringClientId() {
        // Human user who is a team OWNER resets an auth client secret (String clientId target)
        String externalId = "team-owner-user";
        String clientId = "existing-client";
        setUserJwt(externalId);

        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(20L).build());
        when(registryService.verifyAuthClientMemberRoles(eq(clientId), eq(20L), eq(TeamMemberRole.OWNER))).thenReturn(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, clientId, "AuthClient", "update")).isTrue();
    }

    @Test
    public void updateAuthClient_denied_whenUserCaller_isNotTeamOwner_withStringClientId() {
        // Human user who is not the team OWNER cannot reset an auth client secret
        String externalId = "plain-member-user";
        String clientId = "existing-client";
        setUserJwt(externalId);

        when(registryService.getAuthorizedTeams()).thenReturn(List.of());
        when(registryService.getMemberByExternalId(externalId)).thenReturn(Member.builder().id(21L).build());
        when(registryService.verifyAuthClientMemberRoles(eq(clientId), eq(21L), eq(TeamMemberRole.OWNER))).thenReturn(false);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, clientId, "AuthClient", "update")).isFalse();
    }

    @Test
    public void deleteAuthClient_denied_whenCallerIsAuthClient_typeNonUser() {
        setNonUserJwt("credentials-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "target-client-id", "AuthClient", "delete")).isFalse();
    }

    @Test
    public void deleteAuthClient_denied_whenCallerIsAuthClient_typeUser() {
        setNonUserJwt("user-flow-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "target-client-id", "AuthClient", "delete")).isFalse();
    }

    @Test
    public void deleteAuthClient_denied_whenCallerIsAuthClient_typeUserPkce() {
        setNonUserJwt("user-pkce-client");
        when(registryService.getAuthorizedTeams()).thenReturn(List.of());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertThat(evaluator.hasPermission(auth, "target-client-id", "AuthClient", "delete")).isFalse();
    }

    private void setJwt(Map<String, Object> claims) {
        Map<String, Object> headers = Map.of("header1", "value1");
        Jwt jwt = new Jwt("some-token", Instant.now(), Instant.now().plusSeconds(60), headers, claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void setNonUserJwt(String clientId) {
        setJwt(Map.of("clientId", clientId, "clientName", clientId));
    }

    private void setUserJwt(String externalId) {
        setJwt(Map.of("externalId", externalId));
    }

    @Test
    public void modifyAiEnabled_asApplicationOwner_authorized() {
        // given
        String externalId = "user123";
        Long memberId = 1L;
        String applicationName = "test-app";
        setJwt(Map.of("externalId", externalId));

        TeamViewMember teamMember = TeamViewMember.builder().externalId(externalId).build();
        TeamView team = TeamView.builder().members(List.of(teamMember)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyApplicationRoles(applicationName, memberId, List.of(com.cvshealth.cap.registry.model.TeamMemberRole.OWNER)))
                .thenReturn(true);
        when(registryService.getMemberByExternalId(externalId))
                .thenReturn(com.cvshealth.cap.registry.model.Member.builder().id(memberId).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // when
        boolean result = evaluator.hasPermission(auth, applicationName, "Application", "modify-ai-enabled");

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void modifyAiEnabled_asApplicationMember_unauthorized() {
        // given
        String externalId = "user123";
        Long memberId = 1L;
        String applicationName = "test-app";
        setJwt(Map.of("externalId", externalId));

        TeamViewMember teamMember = TeamViewMember.builder().externalId(externalId).build();
        TeamView team = TeamView.builder().members(List.of(teamMember)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyApplicationRoles(applicationName, memberId, List.of(com.cvshealth.cap.registry.model.TeamMemberRole.OWNER)))
                .thenReturn(false);
        when(registryService.getMemberByExternalId(externalId))
                .thenReturn(com.cvshealth.cap.registry.model.Member.builder().id(memberId).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // when
        boolean result = evaluator.hasPermission(auth, applicationName, "Application", "modify-ai-enabled");

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void modifyAiEnabled_asTeamOwner_authorized() {
        // given
        String externalId = "user123";
        Long memberId = 1L;
        String teamName = "test-team";
        setJwt(Map.of("externalId", externalId));

        TeamViewMember teamMember = TeamViewMember.builder().externalId(externalId).build();
        TeamView team = TeamView.builder().members(List.of(teamMember)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamMemberRoles(teamName, memberId, List.of(com.cvshealth.cap.registry.model.TeamMemberRole.OWNER)))
                .thenReturn(true);
        when(registryService.getMemberByExternalId(externalId))
                .thenReturn(com.cvshealth.cap.registry.model.Member.builder().id(memberId).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // when
        boolean result = evaluator.hasPermission(auth, teamName, "Team", "modify-ai-enabled");

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void modifyAiEnabled_asTeamMember_unauthorized() {
        // given
        String externalId = "user123";
        Long memberId = 1L;
        String teamName = "test-team";
        setJwt(Map.of("externalId", externalId));

        TeamViewMember teamMember = TeamViewMember.builder().externalId(externalId).build();
        TeamView team = TeamView.builder().members(List.of(teamMember)).build();

        when(registryService.getAuthorizedTeams()).thenReturn(List.of(team));
        when(registryService.verifyTeamMemberRoles(teamName, memberId, List.of(com.cvshealth.cap.registry.model.TeamMemberRole.OWNER)))
                .thenReturn(false);
        when(registryService.getMemberByExternalId(externalId))
                .thenReturn(com.cvshealth.cap.registry.model.Member.builder().id(memberId).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // when
        boolean result = evaluator.hasPermission(auth, teamName, "Team", "modify-ai-enabled");

        // then
        assertThat(result).isFalse();
    }
}
