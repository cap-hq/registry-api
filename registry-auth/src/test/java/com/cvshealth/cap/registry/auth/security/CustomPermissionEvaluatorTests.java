// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import com.cvshealth.cap.registry.auth.model.AuthClient;
import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.Member;
import com.cvshealth.cap.registry.auth.model.Team;
import com.cvshealth.cap.registry.auth.service.RegistryApiService;
import com.cvshealth.cap.registry.auth.service.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomPermissionEvaluatorTests {

    @Mock
    private RegistryApiService registryApiService;

    @Mock
    private ClientService clientService;

    private CustomPermissionEvaluator evaluator;

    private static final String ADMIN_TEAM = "platform-team";
    private static final String OTHER_TEAM = "other-team";

    @BeforeEach
    void setUp() {
        evaluator = new CustomPermissionEvaluator(registryApiService, clientService);
        ReflectionTestUtils.setField(evaluator, "adminAuthorizedTeams", List.of(ADMIN_TEAM));
    }

    // ─── hasPermission(auth, object, permission) ──────────────────────────────

    @Test
    void hasPermission_objectTarget_throwsUnsupportedOperationException() {
        Authentication auth = mock(Authentication.class);
        assertThatThrownBy(() -> evaluator.hasPermission(auth, new Object(), "query"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ─── non-JWT authentication ───────────────────────────────────────────────

    @Test
    void hasPermission_nonJwtAuth_returnsFalse() {
        Authentication nonJwtAuth = mock(Authentication.class);
        assertThat(evaluator.hasPermission(nonJwtAuth, "1", "Admin", "query")).isFalse();
    }

    // ─── API secret authentication ────────────────────────────────────────────

    @Test
    void hasPermission_apiSecretAuth_adminTargetType_returnsTrue() {
        var auth = new ApiSecretAuthenticationFilter.ApiSecretAuthentication();
        assertThat(evaluator.hasPermission(auth, null, "Admin", "query")).isTrue();
        assertThat(evaluator.hasPermission(auth, null, "Admin", "mutation")).isTrue();
        verifyNoInteractions(registryApiService, clientService);
    }

    @Test
    void hasPermission_apiSecretAuth_nonAdminTargetType_returnsFalse() {
        var auth = new ApiSecretAuthenticationFilter.ApiSecretAuthentication();
        assertThat(evaluator.hasPermission(auth, null, "Application", "query")).isFalse();
        verifyNoInteractions(registryApiService, clientService);
    }

    // ─── non-Admin targetType ─────────────────────────────────────────────────

    @Test
    void hasPermission_unknownTargetType_returnsFalse() {
        var auth = springJwtAuth(userJwt("ext-001"));
        assertThat(evaluator.hasPermission(auth, "1", "Application", "query")).isFalse();
        verifyNoInteractions(registryApiService);
    }

    // ─── user (has externalId) — Admin query permission ───────────────────────

    @Test
    void hasPermission_userIsOwnerOfAdminTeam_queryPermission_returnsTrue() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true))
                .thenReturn(memberWithTeam(ADMIN_TEAM, "OWNER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isTrue();
    }

    @Test
    void hasPermission_userIsMemberOfAdminTeam_queryPermission_returnsTrue() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true))
                .thenReturn(memberWithTeam(ADMIN_TEAM, "MEMBER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isTrue();
    }

    @Test
    void hasPermission_userIsViewerOfAdminTeam_queryPermission_returnsFalse() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true))
                .thenReturn(memberWithTeam(ADMIN_TEAM, "VIEWER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isFalse();
    }

    @Test
    void hasPermission_userIsOwnerOfNonAdminTeam_queryPermission_returnsFalse() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true))
                .thenReturn(memberWithTeam(OTHER_TEAM, "OWNER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isFalse();
    }

    @Test
    void hasPermission_memberNotFound_queryPermission_returnsFalse() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true)).thenReturn(null);

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isFalse();
    }

    // ─── user — Admin mutation permission ─────────────────────────────────────

    @Test
    void hasPermission_userIsOwnerOfAdminTeam_mutationPermission_returnsTrue() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true))
                .thenReturn(memberWithTeam(ADMIN_TEAM, "OWNER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "mutation")).isTrue();
    }

    @Test
    void hasPermission_userIsMemberOfAdminTeam_mutationPermission_returnsFalse() {
        var auth = springJwtAuth(userJwt("ext-001"));
        when(registryApiService.getMember("ext-001", true))
                .thenReturn(memberWithTeam(ADMIN_TEAM, "MEMBER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "mutation")).isFalse();
    }

    // ─── non-user (client credential) — Admin query permission ───────────────

    @Test
    void hasPermission_clientIsOwnerOfAdminTeam_queryPermission_returnsTrue() {
        var auth = springJwtAuth(clientJwt("client-001"));
        Client client = clientWithId("client-001");
        when(clientService.getClient("client-001")).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn("client-001");
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryApiService.getAuthClient("client-001", 1, false))
                .thenReturn(authClientWithTeam(ADMIN_TEAM, "OWNER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isTrue();
    }

    @Test
    void hasPermission_clientIsMemberOfAdminTeam_queryPermission_returnsTrue() {
        var auth = springJwtAuth(clientJwt("client-001"));
        Client client = clientWithId("client-001");
        when(clientService.getClient("client-001")).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn("client-001");
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryApiService.getAuthClient("client-001", 1, false))
                .thenReturn(authClientWithTeam(ADMIN_TEAM, "MEMBER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isTrue();
    }

    @Test
    void hasPermission_clientIsOwnerOfAdminTeam_mutationPermission_returnsTrue() {
        var auth = springJwtAuth(clientJwt("client-001"));
        Client client = clientWithId("client-001");
        when(clientService.getClient("client-001")).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn("client-001");
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryApiService.getAuthClient("client-001", 1, false))
                .thenReturn(authClientWithTeam(ADMIN_TEAM, "OWNER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "mutation")).isTrue();
    }

    @Test
    void hasPermission_clientIsMemberOfAdminTeam_mutationPermission_returnsFalse() {
        var auth = springJwtAuth(clientJwt("client-001"));
        Client client = clientWithId("client-001");
        when(clientService.getClient("client-001")).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn("client-001");
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryApiService.getAuthClient("client-001", 1, false))
                .thenReturn(authClientWithTeam(ADMIN_TEAM, "MEMBER"));

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "mutation")).isFalse();
    }

    @Test
    void hasPermission_clientNotFoundInClientService_queryPermission_returnsFalse() {
        var auth = springJwtAuth(clientJwt("client-001"));
        when(clientService.getClient("client-001")).thenReturn(null);

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isFalse();
        verifyNoInteractions(registryApiService);
    }

    @Test
    void hasPermission_authClientNotFound_queryPermission_returnsFalse() {
        var auth = springJwtAuth(clientJwt("client-001"));
        Client client = clientWithId("client-001");
        when(clientService.getClient("client-001")).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn("client-001");
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryApiService.getAuthClient("client-001", 1, false)).thenReturn(null);

        assertThat(evaluator.hasPermission(auth, "1", "Admin", "query")).isFalse();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt userJwt(String externalId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", externalId)
                .claim("externalId", externalId)
                .build();
    }

    private Jwt clientJwt(String clientId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", clientId)
                .claim("clientId", clientId)
                .build();
    }

    private org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken springJwtAuth(Jwt jwt) {
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
    }

    private Member memberWithTeam(String teamName, String role) {
        return Member.builder()
                .externalId("ext-001")
                .teams(List.of(Team.builder().name(teamName).role(role).build()))
                .build();
    }

    private Client clientWithId(String clientId) {
        Client client = new Client();
        client.setClientId(clientId);
        client.setClientName(clientId);
        return client;
    }

    private AuthClient authClientWithTeam(String teamName, String role) {
        return AuthClient.builder()
                .clientId("client-001")
                .teams(List.of(Team.builder().name(teamName).role(role).build()))
                .build();
    }
}
