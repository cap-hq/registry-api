// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.AuthClientView;
import com.cvshealth.cap.registry.model.AuthClientViewApplication;
import com.cvshealth.cap.registry.model.AuthClientViewMember;
import com.cvshealth.cap.registry.model.AuthClientViewTeam;
import com.cvshealth.cap.registry.model.job.AuthClientCleanupJobResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthClientCleanupServiceTests {

    @InjectMocks
    private AuthClientCleanupService service;

    @Mock
    private AuthClientService authClientService;
    @Mock
    private RegistryService registryService;

    // --- isCandidate ---

    @Test
    public void isCandidate_nullCreateTimestamp_returnsFalse() {
        AuthClientView client = AuthClientView.builder().build();
        Instant ageCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant inactivityCutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(service.isCandidate(client, ageCutoff, inactivityCutoff)).isFalse();
    }

    @Test
    public void isCandidate_clientTooNew_returnsFalse() {
        AuthClientView client = AuthClientView.builder()
                .createTimestamp(Instant.now().minus(10, ChronoUnit.DAYS)).build();
        Instant ageCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant inactivityCutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(service.isCandidate(client, ageCutoff, inactivityCutoff)).isFalse();
    }

    @Test
    public void isCandidate_oldClientNeverUsed_returnsTrue() {
        AuthClientView client = AuthClientView.builder()
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS))
                .lastAccessTokenIssuedAt(null).build();
        Instant ageCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant inactivityCutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(service.isCandidate(client, ageCutoff, inactivityCutoff)).isTrue();
    }

    @Test
    public void isCandidate_oldClientUsedRecently_returnsFalse() {
        AuthClientView client = AuthClientView.builder()
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS))
                .lastAccessTokenIssuedAt(Instant.now().minus(5, ChronoUnit.DAYS)).build();
        Instant ageCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant inactivityCutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(service.isCandidate(client, ageCutoff, inactivityCutoff)).isFalse();
    }

    @Test
    public void isCandidate_oldClientInactiveForLongEnough_returnsTrue() {
        AuthClientView client = AuthClientView.builder()
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS))
                .lastAccessTokenIssuedAt(Instant.now().minus(31, ChronoUnit.DAYS)).build();
        Instant ageCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant inactivityCutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(service.isCandidate(client, ageCutoff, inactivityCutoff)).isTrue();
    }

    // --- getCandidates ---

    @Test
    public void getCandidates_stripsTeamApplicationsAndMembers() {
        AuthClientViewApplication app = AuthClientViewApplication.builder().id(1L).name("my-app").build();
        AuthClientViewMember member = AuthClientViewMember.builder().id(1L).externalId("ext-1").build();
        AuthClientViewTeam team = AuthClientViewTeam.builder().name("my-team")
                .applications(List.of(app)).members(List.of(member)).build();
        AuthClientView stale = AuthClientView.builder()
                .clientId("pkce-1").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS))
                .teams(List.of(team))
                .build();
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(stale));

        List<AuthClientView> candidates = service.getCandidates(30, 30);

        assertThat(candidates).hasSize(1);
        AuthClientViewTeam resultTeam = candidates.get(0).getTeams().get(0);
        assertThat(resultTeam.getApplications()).isNull();
        assertThat(resultTeam.getMembers()).isNull();
    }

    @Test
    public void getCandidates_filtersToStaleClients() {
        AuthClientView stale = AuthClientView.builder()
                .clientId("pkce-1").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS))
                .lastAccessTokenIssuedAt(null)
                .build();
        AuthClientView recent = AuthClientView.builder()
                .clientId("pkce-2").clientVersion(1)
                .createTimestamp(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(stale, recent));

        List<AuthClientView> candidates = service.getCandidates(30, 30);

        assertThat(candidates).containsExactly(stale);
    }

    // --- runCleanup ---

    @Test
    public void runCleanup_deletesAllCandidatesAndReturnsStats() {
        AuthClientView stale1 = AuthClientView.builder()
                .clientId("pkce-1").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS)).build();
        AuthClientView stale2 = AuthClientView.builder()
                .clientId("pkce-2").clientVersion(1)
                .createTimestamp(Instant.now().minus(45, ChronoUnit.DAYS))
                .lastAccessTokenIssuedAt(Instant.now().minus(31, ChronoUnit.DAYS)).build();
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(stale1, stale2));

        AuthClientCleanupJobResponse result = service.runCleanup(30, 30);

        assertThat(result.isDryRun()).isFalse();
        assertThat(result.getCandidates()).containsExactly(stale1, stale2);
        assertThat(result.getDeleted()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();
        verify(registryService).deleteAuthClient("pkce-1", 1);
        verify(registryService).deleteAuthClient("pkce-2", 1);
    }

    @Test
    public void runCleanup_candidatesCollectedBeforeAnyDeletion() {
        AuthClientView stale1 = AuthClientView.builder()
                .clientId("client-1").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS)).build();
        AuthClientView stale2 = AuthClientView.builder()
                .clientId("client-2").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS)).build();
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(stale1, stale2));

        service.runCleanup(30, 30);

        InOrder inOrder = inOrder(authClientService, registryService);
        inOrder.verify(authClientService).getAuthClients(AuthClientType.USER_PKCE);
        inOrder.verify(registryService).deleteAuthClient("client-1", 1);
        inOrder.verify(registryService).deleteAuthClient("client-2", 1);
        verify(authClientService, times(1)).getAuthClients(any(AuthClientType.class));
    }

    @Test
    public void runCleanup_deletionFails_continuesAndCountsFailure() {
        AuthClientView stale1 = AuthClientView.builder()
                .clientId("client-1").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS)).build();
        AuthClientView stale2 = AuthClientView.builder()
                .clientId("client-2").clientVersion(1)
                .createTimestamp(Instant.now().minus(60, ChronoUnit.DAYS)).build();
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(stale1, stale2));
        doThrow(new RuntimeException("boom")).when(registryService).deleteAuthClient("client-1", 1);

        AuthClientCleanupJobResponse result = service.runCleanup(30, 30);

        assertThat(result.getDeleted()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        verify(registryService).deleteAuthClient("client-1", 1);
        verify(registryService).deleteAuthClient("client-2", 1);
    }

    @Test
    public void runCleanup_noCandidates_returnsEmptyStats() {
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of());

        AuthClientCleanupJobResponse result = service.runCleanup(30, 30);

        assertThat(result.getCandidates()).isEmpty();
        assertThat(result.getDeleted()).isZero();
        assertThat(result.getFailed()).isZero();
        verify(registryService, never()).deleteAuthClient(any(), any());
    }
}
