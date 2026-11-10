// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthClientExpirationNotificationServiceTests {
    @InjectMocks
    private AuthClientExpirationNotificationService service;

    @Mock
    private RegistryService registryService;
    @Mock
    private RegistryAuthService registryAuthService;
    @Mock
    private AuthClientService authClientService;
    @Mock
    private NotificationService notificationService;

    @Mock
    private CheckAuthClientExpirationsRequest mockCheckAuthClientExpirationsRequest;;
    @Mock
    private AuthClientViewTeam mockAuthClientViewTeam;
    @Mock
    private AuthClientView mockAuthClientView;
    @Mock
    private OAuthClientExpiration mockOAuthClientExpiration;
    @Mock
    private AuthClientExpirationCheckJobStats mockJobStats;

    @Test
    public void testGetClientExpirations() throws NotificationServiceException {
        // given auth client expirations check request
        CheckAuthClientExpirationsRequest request = CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(30)
                .ageDaysThreshold(60)
                .sendEmailNotifications(true)
                .sendSlackNotifications(true)
                .deleteExpiredClients(true)
                .deleteExpiredLegacyClients(true)
                .legacyClientDeleteAgeDaysThreshold(95)
                .build();

        Instant now = Instant.now();
        List<OAuthClientExpiration> clientExpirations = List.of(
                OAuthClientExpiration.builder().clientId("expiring-client")
                        .clientName("expiring-client")
                        .ageInDays(75).expirationInDays(15)
                        .clientIdIssuedAt(now.minus(75, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(15, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expiring-client-v2")
                        .clientName("expiring-client")
                        .ageInDays(1).expirationInDays(89)
                        .clientIdIssuedAt(now.minus(1, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(89, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expired-client")
                        .clientName("expired-client")
                        .ageInDays(100).expirationInDays(-10)
                        .clientIdIssuedAt(now.minus(100, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.minus(10, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expired-client-versioned-v3")
                        .clientName("expired-client-versioned")
                        .ageInDays(99).expirationInDays(-9)
                        .clientIdIssuedAt(now.minus(99, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(9, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expiring-legacy-client")
                        .clientName("e08f8bf6-a627-1111-2222-33333333333")
                        .ageInDays(80)
                        .clientIdIssuedAt(now.minus(80, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expired-legacy-client")
                        .clientName("e08f8bf6-a627-1111-2222-44444444444")
                        .ageInDays(150)
                        .clientIdIssuedAt(now.minus(150, ChronoUnit.DAYS))
                        .build()
        );
        when(registryAuthService.getClientExpirations()).thenReturn(clientExpirations);
        PaginatedAuthClientResponse authClientResponse = PaginatedAuthClientResponse.builder()
                .totalPageCount(1).totalRecordCount(5L)
                .pageNumber(1).pageSize(5)
                .authClients(List.of(
                        AuthClientView.builder().clientId("expiring-client").clientVersion(1)
                                        .teams(buildAuthClientViewTeam(1L, "team-1", "team1.owner@example.com",
                                        "CH123", "team-1-members@example.com")).build(),
                        AuthClientView.builder().clientId("expiring-client-v2").clientVersion(2)
                                .teams(buildAuthClientViewTeam(1L, "team-1", "team1.owner@example.com",
                                        "CH123", "team-1-members@example.com")).build(),
                        AuthClientView.builder().clientId("expired-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(2L, "team-2", "team2.owner@example.com",
                                        null, "team-2-members@example.com")).build(),
                        AuthClientView.builder().clientId("expired-client-versioned").clientVersion(3)
                                .teams(buildAuthClientViewTeam(2L, "team-2", "team2.owner@example.com",
                                        null, "team-2-members@example.com")).build(),
                        AuthClientView.builder().clientId("expiring-legacy-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(3L, "team-3", "team3.owner@example.com",
                                        null, null)).build(),
                        AuthClientView.builder().clientId("expired-legacy-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(4L, "team-4", "team4.owner@example.com",
                                        null, null)).build()


                ))
                .build();
        when(authClientService.getAuthClients(any(PaginationInput.class), any(AuthClientSearchFilter.class), any())).thenReturn(authClientResponse);

        // when
        AuthClientExpirationCheckJobStats jobStats = service.checkAuthClientsExpirations(request);

        // then
        verify(notificationService, times(4)).sendTeamNotification(any(NotificationRequest.class));
        verify(registryService, times(1)).deleteAuthClient("expired-client", 1);
        verify(registryService, times(1)).deleteAuthClient("expired-client-versioned", 3);
        verify(registryService, times(1)).deleteAuthClient("expired-legacy-client", 1);

        assertThat(jobStats).isNotNull();
        assertThat(jobStats.getExpiringAuthClients()).isEqualTo(2);
        assertThat(jobStats.getExpiringLegacyAuthClients()).isEqualTo(1);
        assertThat(jobStats.getExpiredAuthClients()).isEqualTo(3);
        assertThat(jobStats.getExpiredLegacyAuthClients()).isEqualTo(1);
        assertThat(jobStats.getTeamsNotified()).isEqualTo(4);
        assertThat(jobStats.getDeletedAuthClients()).isEqualTo(3);
        assertThat(jobStats.getDeletedLegacyAuthClients()).isEqualTo(1);
    }

    @Test
    public void testGetClientExpirations_DoNotDeleteLegacyExpiredAuthClients() throws NotificationServiceException {
        // given auth client expirations check request
        CheckAuthClientExpirationsRequest request = CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(30)
                .ageDaysThreshold(60)
                .sendEmailNotifications(true)
                .deleteExpiredClients(true)
                .deleteExpiredLegacyClients(false)      // DO NOT DELETE
                .legacyClientDeleteAgeDaysThreshold(90)
                .build();

        Instant now = Instant.now();
        List<OAuthClientExpiration> clientExpirations = List.of(
                OAuthClientExpiration.builder().clientId("expiring-legacy-client")
                        .clientName("e08f8bf6-a627-1111-2222-33333333333")
                        .ageInDays(80)
                        .clientIdIssuedAt(now.minus(80, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expired-legacy-client")
                        .clientName("e08f8bf6-a627-1111-2222-44444444444")
                        .ageInDays(150)
                        .clientIdIssuedAt(now.minus(150, ChronoUnit.DAYS))
                        .build()
        );
        when(registryAuthService.getClientExpirations()).thenReturn(clientExpirations);

        PaginatedAuthClientResponse authClientResponse = PaginatedAuthClientResponse.builder()
                .totalPageCount(1).totalRecordCount(5L)
                .pageNumber(1).pageSize(5)
                .authClients(List.of(
                        AuthClientView.builder().clientId("expiring-legacy-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(3L, "team-3", "team3.owner@example.com",
                                        null, null)).build(),
                        AuthClientView.builder().clientId("expired-legacy-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(4L, "team-4", "team4.owner@example.com",
                                        null, null)).build()

                ))
                .build();
        when(authClientService.getAuthClients(any(PaginationInput.class), any(AuthClientSearchFilter.class), any())).thenReturn(authClientResponse);

        // when
        AuthClientExpirationCheckJobStats jobStats = service.checkAuthClientsExpirations(request);

        // then
        verify(notificationService, times(2)).sendTeamNotification(any(NotificationRequest.class)); // spy
        verify(registryService, times(0)).deleteAuthClient("expired-legacy-client", 1);

        assertThat(jobStats).isNotNull();
        assertThat(jobStats.getExpiringAuthClients()).isEqualTo(1);
        assertThat(jobStats.getExpiringLegacyAuthClients()).isEqualTo(1);
        assertThat(jobStats.getExpiredAuthClients()).isEqualTo(1);
        assertThat(jobStats.getExpiredLegacyAuthClients()).isEqualTo(1);
        assertThat(jobStats.getTeamsNotified()).isEqualTo(2);
        assertThat(jobStats.getDeletedAuthClients()).isEqualTo(0);
        assertThat(jobStats.getDeletedLegacyAuthClients()).isEqualTo(0);
    }

    @Test
    public void testGetClientExpirations_DoNotDeleteDoNotNotify() {
        // given auth client expirations check request
        CheckAuthClientExpirationsRequest request = CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(30)
                .ageDaysThreshold(60)
                .sendEmailNotifications(false)      // DO NOT NOTIFY
                .deleteExpiredClients(false)
                .legacyClientDeleteAgeDaysThreshold(90)// DO NOT DELETE
                .build();

        Instant now = Instant.now();
        List<OAuthClientExpiration> clientExpirations = List.of(
                OAuthClientExpiration.builder().clientId("expiring-client")
                        .clientName("expiring-client")
                        .ageInDays(75).expirationInDays(15)
                        .clientIdIssuedAt(now.minus(75, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(15, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expiring-client-v2")
                        .clientName("expiring-client")
                        .ageInDays(1).expirationInDays(89)
                        .clientIdIssuedAt(now.minus(1, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(89, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expired-client")
                        .clientName("expired-client")
                        .ageInDays(100).expirationInDays(-10)
                        .clientIdIssuedAt(now.minus(100, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.minus(10, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expiring-legacy-client")
                        .clientName("e08f8bf6-a627-1111-2222-33333333333")
                        .ageInDays(80)
                        .clientIdIssuedAt(now.minus(80, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expired-legacy-client")
                        .clientName("e08f8bf6-a627-1111-2222-44444444444")
                        .ageInDays(150)
                        .clientIdIssuedAt(now.minus(150, ChronoUnit.DAYS))
                        .build()
        );
        when(registryAuthService.getClientExpirations()).thenReturn(clientExpirations);

        PaginatedAuthClientResponse authClientResponse = PaginatedAuthClientResponse.builder()
                .totalPageCount(1).totalRecordCount(5L)
                .pageNumber(1).pageSize(5)
                .authClients(List.of(
                        AuthClientView.builder().clientId("expiring-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(1L, "team-1", "team1.owner@example.com",
                                        "C123", "team-1-members@example.com")).build(),
                        AuthClientView.builder().clientId("expiring-client-v2").clientVersion(2)
                                .teams(buildAuthClientViewTeam(1L, "team-1", "team1.owner@example.com",
                                        "C123", "team-1-members@example.com")).build(),
                        AuthClientView.builder().clientId("expired-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(2L, "team-2", "team2.owner@example.com",
                                        null, "team-2-members@example.com")).build(),
                        AuthClientView.builder().clientId("expiring-legacy-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(3L, "team-3", "team3.owner@example.com",
                                        null, null)).build(),
                        AuthClientView.builder().clientId("expired-legacy-client").clientVersion(1)
                                .teams(buildAuthClientViewTeam(4L, "team-4", "team4.owner@example.com",
                                        null, null)).build()

                ))
                .build();
        when(authClientService.getAuthClients(any(PaginationInput.class), any(AuthClientSearchFilter.class), any())).thenReturn(authClientResponse);

        // when
        AuthClientExpirationCheckJobStats jobStats = service.checkAuthClientsExpirations(request);

        // then
        verifyNoInteractions(notificationService);
        verify(registryService, times(0)).deleteAuthClient(any(String.class), any(Integer.class));

        assertThat(jobStats).isNotNull();
        assertThat(jobStats.getExpiringAuthClients()).isEqualTo(2);
        assertThat(jobStats.getExpiringLegacyAuthClients()).isEqualTo(1);
        assertThat(jobStats.getExpiredAuthClients()).isEqualTo(2);
        assertThat(jobStats.getExpiredLegacyAuthClients()).isEqualTo(1);
        assertThat(jobStats.getTeamsNotified()).isEqualTo(0);
        assertThat(jobStats.getDeletedAuthClients()).isEqualTo(0);
        assertThat(jobStats.getDeletedLegacyAuthClients()).isEqualTo(0);
    }

    @Test
    public void buildTeamNotificationMessageContext() {
        // given
        when(mockCheckAuthClientExpirationsRequest.getExpirationDaysThreshold()).thenReturn(30);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockAuthClientViewTeam.getName()).thenReturn("team-1");
        when(mockAuthClientView.getClientId()).thenReturn("expiring-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(1);
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(14);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(76);
        when(mockOAuthClientExpiration.getClientIdIssuedAt()).thenReturn(Instant.now().minus(76, ChronoUnit.DAYS));
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(Instant.now().plus(14, ChronoUnit.DAYS));
        when(notificationService.dateAtTimeFormat(any(Instant.class))).thenReturn("2025-01-01 at 2:00 AM EST");
        when(notificationService.registryOrCapUI()).thenReturn("in Registry Or Cap UI");

        List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients = new ArrayList<>(List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build()
        ));

        // when
        when(mockCheckAuthClientExpirationsRequest.getDocsUrl()).thenReturn("http://localhost:3000");

        Map<String, Object> messageModel = service.buildTeamNotificationMessageContext(mockCheckAuthClientExpirationsRequest, mockAuthClientViewTeam, expiringAuthClients);

        // then
        assertThat(messageModel).isNotNull();
        assertThat(messageModel.get("teamName")).isEqualTo("team-1");
        assertThat(messageModel.get("expirationDaysThreshold")).isEqualTo(30);
        assertThat(messageModel.get("inRegistryOrCapUI")).isNotNull();
        assertThat(messageModel.get("authClientRotationDocsUrl")).isNotNull();

        List<Map<String, Object>> clients = (List<Map<String, Object>>) messageModel.get("clients");
        assertThat(clients).isNotNull();
        Map<String, Object> client1 = clients.get(0);
        assertThat(client1.get("clientId")).isEqualTo("expiring-client");
        assertThat(client1.get("clientVersion")).isEqualTo(1);
        assertThat(client1.get("expirationType")).isEqualTo("expiring");
        assertThat(client1.get("ageInDays")).isEqualTo(76);
        assertThat(client1.get("expirationInDays")).isEqualTo(14);
        assertThat(client1.get("issuedAt")).isNotNull();
        assertThat(client1.get("expiresAt")).isNotNull();
    }

    @Test
    public void buildTeamNotificationMessageContext_includesLastAccessTokenWindowForNightlyTemplate() {
        // given
        when(mockCheckAuthClientExpirationsRequest.getExpirationDaysThreshold()).thenReturn(1);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(0);
        when(mockCheckAuthClientExpirationsRequest.getLastAccessTokenIssuedWithinHours()).thenReturn(24);
        when(mockAuthClientViewTeam.getName()).thenReturn("team-1");
        when(mockAuthClientView.getClientId()).thenReturn("expiring-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(1);
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(1);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(90);
        when(mockOAuthClientExpiration.getClientIdIssuedAt()).thenReturn(Instant.now().minus(90, ChronoUnit.DAYS));
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));
        when(notificationService.dateAtTimeFormat(any(Instant.class))).thenReturn("2026-09-03 at 8:00 AM UTC");
        when(notificationService.registryOrCapUI()).thenReturn("in Registry Or Cap UI");
        when(mockCheckAuthClientExpirationsRequest.getDocsUrl()).thenReturn("http://localhost:3000");

        List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients = new ArrayList<>(List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView).second(mockOAuthClientExpiration).build()
        ));

        // when
        Map<String, Object> messageModel = service.buildTeamNotificationMessageContext(mockCheckAuthClientExpirationsRequest, mockAuthClientViewTeam, expiringAuthClients);

        // then: nightly template receives lastAccessTokenIssuedWithinHours and expirationHoursThreshold in context
        assertThat(messageModel.get("lastAccessTokenIssuedWithinHours")).isEqualTo(24);
        assertThat(messageModel.get("expirationHoursThreshold")).isEqualTo(24);
    }


    @Test
    public void buildTeamNotificationMessage_multiple() {
        // given
        when(mockCheckAuthClientExpirationsRequest.getExpirationDaysThreshold()).thenReturn(30);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockAuthClientViewTeam.getName()).thenReturn("team-2");
        when(mockAuthClientView.getClientId()).thenReturn("expiring-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(1);
        when(mockOAuthClientExpiration.getClientId()).thenReturn("expiring-client");
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(14);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(76);
        when(mockOAuthClientExpiration.getClientIdIssuedAt()).thenReturn(Instant.now().minus(76, ChronoUnit.DAYS));
        when(mockOAuthClientExpiration.getClientSecretExpiresAt())
                .thenReturn(Instant.now().plus(14, ChronoUnit.DAYS));
        when(notificationService.dateAtTimeFormat(any(Instant.class))).thenReturn("2024-12-31 at 2:00 AM EST");
        when(notificationService.registryOrCapUI()).thenReturn("in Registry Or Cap UI");

        AuthClientView mockAuthClientView2 = mock(AuthClientView.class);
        when(mockAuthClientView2.getClientId()).thenReturn("expired-client");
        when(mockAuthClientView2.getClientVersion()).thenReturn(2);
        OAuthClientExpiration mockOAuthClientExpiration2 = mock(OAuthClientExpiration.class);
        when(mockOAuthClientExpiration2.getClientId()).thenReturn("expired-client-v2");
        when(mockOAuthClientExpiration2.getExpirationInDays()).thenReturn(-1);
        when(mockOAuthClientExpiration2.getAgeInDays()).thenReturn(91);
        when(mockOAuthClientExpiration2.getClientIdIssuedAt()).thenReturn(Instant.now().minus(91, ChronoUnit.DAYS));
        when(mockOAuthClientExpiration2.getClientSecretExpiresAt())
                .thenReturn(Instant.now().minus(-1, ChronoUnit.DAYS));

        List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients = new ArrayList<>(List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build(),
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView2)
                        .second(mockOAuthClientExpiration2)
                        .build()
        ));

        // when
        when(mockCheckAuthClientExpirationsRequest.getDocsUrl()).thenReturn("http://localhost:3000");
        Map<String, Object> messageModel = service.buildTeamNotificationMessageContext(mockCheckAuthClientExpirationsRequest, mockAuthClientViewTeam, expiringAuthClients);

        assertThat(messageModel).isNotNull();
        assertThat(messageModel.get("teamName")).isEqualTo("team-2");
        assertThat(messageModel.get("expirationDaysThreshold")).isEqualTo(30);
        assertThat(messageModel.get("inRegistryOrCapUI")).isNotNull();
        assertThat(messageModel.get("authClientRotationDocsUrl")).isNotNull();

        List<Map<String, Object>> clients = (List<Map<String, Object>>) messageModel.get("clients");
        assertThat(clients.size()).isEqualTo(2);
        Map<String, Object> client = clients.get(0);
        assertThat(client.get("clientId")).isEqualTo("expired-client");
        assertThat(client.get("clientVersion")).isEqualTo(2);
        assertThat(client.get("expirationType")).isEqualTo("expired");
        assertThat(client.get("ageInDays")).isEqualTo(91);
        assertThat(client.get("expirationInDays")).isEqualTo(-1);
        assertThat(client.get("issuedAt")).isNotNull();
        assertThat(client.get("expiresAt")).isNotNull();

        client = clients.get(1);
        assertThat(client.get("clientId")).isEqualTo("expiring-client");
        assertThat(client.get("clientVersion")).isEqualTo(1);
        assertThat(client.get("expirationType")).isEqualTo("expiring");
        assertThat(client.get("ageInDays")).isEqualTo(76);
        assertThat(client.get("expirationInDays")).isEqualTo(14);
        assertThat(client.get("issuedAt")).isNotNull();
        assertThat(client.get("expiresAt")).isNotNull();
    }

    @Test
    public void buildTeamNotificationMessage_legacyExpiringAuthClient() {
        // given
        when(mockCheckAuthClientExpirationsRequest.getExpirationDaysThreshold()).thenReturn(30);
        when(mockCheckAuthClientExpirationsRequest.getAgeDaysThreshold()).thenReturn(60);
        when(mockCheckAuthClientExpirationsRequest.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockAuthClientViewTeam.getName()).thenReturn("team-1");
        when(mockAuthClientView.getClientId()).thenReturn("expiring-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(1);
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(null);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(76);
        when(mockOAuthClientExpiration.getClientIdIssuedAt()).thenReturn(Instant.now().minus(76, ChronoUnit.DAYS));
        when(notificationService.dateAtTimeFormat(any(Instant.class))).thenReturn("2025-01-01 at 2:00 AM EST");
        when(notificationService.registryOrCapUI()).thenReturn("in Registry Or Cap UI");

        List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients = new ArrayList<>(List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build()
        ));

        // when
        when(mockCheckAuthClientExpirationsRequest.getDocsUrl()).thenReturn("http://localhost:3000");
        Map<String, Object> messageModel = service.buildTeamNotificationMessageContext(mockCheckAuthClientExpirationsRequest, mockAuthClientViewTeam, expiringAuthClients);

        // then
        assertThat(messageModel).isNotNull();
        assertThat(messageModel.get("teamName")).isEqualTo("team-1");
        assertThat(messageModel.get("expirationDaysThreshold")).isEqualTo(30);
        assertThat(messageModel.get("inRegistryOrCapUI")).isNotNull();
        assertThat(messageModel.get("authClientRotationDocsUrl")).isNotNull();

        List<Map<String, Object>> clients = (List<Map<String, Object>>) messageModel.get("clients");
        assertThat(clients.size()).isEqualTo(1);
        Map<String, Object> client = clients.get(0);
        assertThat(client.get("clientId")).isEqualTo("expiring-client");
        assertThat(client.get("clientVersion")).isEqualTo(1);
        assertThat(client.get("expirationType")).isEqualTo("no-expiration");
        assertThat(client.get("ageInDays")).isEqualTo(76);
        assertThat(client.get("expirationInDays")).isNull();
        assertThat(client.get("issuedAt")).isNotNull();
        assertThat(client.get("expiresAt")).isNull();
    }

    @Test
    public void buildTeamNotificationMessage_legacyExpiredAuthClient() {
        // given
        when(mockCheckAuthClientExpirationsRequest.getExpirationDaysThreshold()).thenReturn(30);
        when(mockCheckAuthClientExpirationsRequest.getAgeDaysThreshold()).thenReturn(60);
        when(mockCheckAuthClientExpirationsRequest.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockAuthClientViewTeam.getName()).thenReturn("team-1");
        when(mockAuthClientView.getClientId()).thenReturn("expired-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(2);
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(null);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(91);
        when(mockOAuthClientExpiration.getClientIdIssuedAt()).thenReturn(Instant.now().minus(91, ChronoUnit.DAYS));
        when(notificationService.dateAtTimeFormat(any(Instant.class))).thenReturn("2025-01-01 at 2:00 AM EST");
        when(notificationService.registryOrCapUI()).thenReturn("in Registry Or Cap UI");

        List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients = new ArrayList<>(List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build()
        ));

        // when
        when(mockCheckAuthClientExpirationsRequest.getDocsUrl()).thenReturn("http://localhost:3000");
        Map<String, Object> messageModel = service.buildTeamNotificationMessageContext(mockCheckAuthClientExpirationsRequest, mockAuthClientViewTeam, expiringAuthClients);

        // then
        assertThat(messageModel).isNotNull();
        assertThat(messageModel.get("teamName")).isEqualTo("team-1");
        assertThat(messageModel.get("expirationDaysThreshold")).isEqualTo(30);
        assertThat(messageModel.get("inRegistryOrCapUI")).isNotNull();
        assertThat(messageModel.get("authClientRotationDocsUrl")).isNotNull();

        List<Map<String, Object>> clients = (List<Map<String, Object>>) messageModel.get("clients");
        assertThat(clients.size()).isEqualTo(1);
        Map<String, Object> client = clients.get(0);
        assertThat(client.get("clientId")).isEqualTo("expired-client");
        assertThat(client.get("clientVersion")).isEqualTo(2);
        assertThat(client.get("expirationType")).isEqualTo("no-expiration");
        assertThat(client.get("ageInDays")).isEqualTo(91);
        assertThat(client.get("expirationInDays")).isNull();
        assertThat(client.get("issuedAt")).isNotNull();
        assertThat(client.get("expiresAt")).isNull();
    }

    @Test
    public void getExpiringAuthClientPair() {
        // given
        List<AuthClientView> authClients = List.of(
                AuthClientView.builder().clientId("client-1").clientVersion(1).build(),
                AuthClientView.builder().clientId("client-1").clientVersion(2).build(),
                AuthClientView.builder().clientId("client-2").clientVersion(3).build()
        );
        List<OAuthClientExpiration> oAuthClientExpirations = List.of(
                OAuthClientExpiration.builder().clientId("client-1").build(),
                OAuthClientExpiration.builder().clientId("client-2-v3").build()
        );

        // when
        List<ModelPair<AuthClientView, OAuthClientExpiration>> result = service.getExpiringAuthClientPair(authClients, oAuthClientExpirations);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getFirst().getClientId()).isEqualTo("client-1");
        assertThat(result.get(0).getSecond().getClientId()).isEqualTo("client-1");
        assertThat(result.get(1).getFirst().getClientId()).isEqualTo("client-2");
        assertThat(result.get(1).getSecond().getClientId()).isEqualTo("client-2-v3");
    }

    @Test
    public void deleteExpiredAuthClients() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);

        AuthClientView mockAuthClientView2 = mock(AuthClientView.class);
        AuthClientView mockAuthClientView3 = mock(AuthClientView.class);
        OAuthClientExpiration mockOAuthClientExpiration2 = mock(OAuthClientExpiration.class);
        OAuthClientExpiration mockOAuthClientExpiration3 = mock(OAuthClientExpiration.class);
        List<ModelPair<AuthClientView, OAuthClientExpiration>> mockExpiringAuthClients = List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build(),
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView2)
                        .second(mockOAuthClientExpiration2)
                        .build(),
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView3)
                        .second(mockOAuthClientExpiration3)
                        .build()
        );
        // expired-client
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(-10);
        when(mockAuthClientView.getClientId()).thenReturn("expired-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(1);
        // expired-client retain
        when(mockOAuthClientExpiration2.getExpirationInDays()).thenReturn(-5);
        // expiring-client
        when(mockOAuthClientExpiration2.getExpirationInDays()).thenReturn(3);

        // when
        service.deleteExpiredAuthClients(mockCheckAuthClientExpirationsRequest, mockExpiringAuthClients, mockJobStats);

        // then
        verify(registryService).deleteAuthClient("expired-client", 1);
        verify(mockJobStats).addDeletedAuthClient();
    }

    @Test
    public void deleteExpiredAuthClients_deleteLegacyFeatureOff() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredLegacyClients()).thenReturn(false);

        List<ModelPair<AuthClientView, OAuthClientExpiration>> mockExpiringAuthClients = List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build()
        );
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(null);

        // when
        service.deleteExpiredAuthClients(mockCheckAuthClientExpirationsRequest, mockExpiringAuthClients, mockJobStats);

        // then
        verifyNoInteractions(registryService);
        verifyNoInteractions(mockJobStats);
    }

    @Test
    public void deleteLegacyExpiredAuthClients() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);

        AuthClientView mockAuthClientView2 = mock(AuthClientView.class);
        AuthClientView mockAuthClientView3 = mock(AuthClientView.class);
        OAuthClientExpiration mockOAuthClientExpiration2 = mock(OAuthClientExpiration.class);
        OAuthClientExpiration mockOAuthClientExpiration3 = mock(OAuthClientExpiration.class);
        List<ModelPair<AuthClientView, OAuthClientExpiration>> mockExpiringAuthClients = List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build(),
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView2)
                        .second(mockOAuthClientExpiration2)
                        .build(),
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView3)
                        .second(mockOAuthClientExpiration3)
                        .build()
        );
        // expired-client
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(null);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(100);
        when(mockAuthClientView.getClientId()).thenReturn("expired-client");
        when(mockAuthClientView.getClientVersion()).thenReturn(1);
        // expired-client retain
        when(mockOAuthClientExpiration2.getExpirationInDays()).thenReturn(null);
        when(mockOAuthClientExpiration2.getAgeInDays()).thenReturn(95);
        // expiring-client
        when(mockOAuthClientExpiration3.getExpirationInDays()).thenReturn(null);
        when(mockOAuthClientExpiration3.getAgeInDays()).thenReturn(65);

        // when
        service.deleteExpiredAuthClients(mockCheckAuthClientExpirationsRequest, mockExpiringAuthClients, mockJobStats);

        // then
        verify(registryService).deleteAuthClient("expired-client", 1);
        verify(mockJobStats).addDeletedAuthClient();
        verify(mockJobStats).addDeletedLegacyAuthClient();
    }

    @Test
    public void deleteExpiredAuthClients_featureOff() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(false);
        List<ModelPair<AuthClientView, OAuthClientExpiration>> mockExpiringAuthClients = List.of(
                ModelPair.<AuthClientView, OAuthClientExpiration>builder()
                        .first(mockAuthClientView)
                        .second(mockOAuthClientExpiration)
                        .build()
        );

        // when
        service.deleteExpiredAuthClients(mockCheckAuthClientExpirationsRequest, mockExpiringAuthClients, mockJobStats);

        // then
        verifyNoInteractions(mockJobStats);
    }

    @Test
    public void getClientViewExpirationType_noExpiration() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(null);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(75);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("no-expiration");
    }

    @Test
    public void getClientViewExpirationType_noExpiration_retention() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(null);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(92);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("no-expiration");
    }


    @Test
    public void getClientViewExpirationType_deletedNoExpiration() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(null);
        when(mockOAuthClientExpiration.getAgeInDays()).thenReturn(100);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("deleted-no-expiration");
    }

    @Test
    public void getClientViewExpirationType_deletedNoExpiration_noDeletedAllowed() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredLegacyClients()).thenReturn(false);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(null);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("no-expiration");
    }

    @Test
    public void getClientViewExpirationType_expired() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(Instant.now());
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(-1);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("expired");
    }

    @Test
    public void getClientViewExpirationType_expired_noDeletedAllowed() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(false);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(Instant.now());
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(-8);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("expired");
    }

    @Test
    public void getClientViewExpirationType_deleted() {
        // given
        when(mockCheckAuthClientExpirationsRequest.isDeleteExpiredClients()).thenReturn(true);
        when(mockCheckAuthClientExpirationsRequest.getExpiredClientRetentionDays()).thenReturn(7);
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(Instant.now());
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(-8);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("deleted");
    }

    @Test
    public void getClientViewExpirationType_expiring() {
        // given
        when(mockOAuthClientExpiration.getClientSecretExpiresAt()).thenReturn(Instant.now());
        when(mockOAuthClientExpiration.getExpirationInDays()).thenReturn(15);

        // when
        String result = service.getClientViewExpirationType(mockCheckAuthClientExpirationsRequest, mockOAuthClientExpiration);

        // then
        assertThat(result).isEqualTo("expiring");
    }

    @Test
    public void testGetClientExpirations_withLowerBound_onlyIncludesExpiringInWindow() throws NotificationServiceException {
        // given: nightly-style request: expiring within 0–1 days only
        CheckAuthClientExpirationsRequest request = CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(1)
                .minExpirationDaysThreshold(0)
                .ageDaysThreshold(9999)
                .sendEmailNotifications(true)
                .sendSlackNotifications(false)
                .deleteExpiredClients(false)
                .deleteExpiredLegacyClients(false)
                .legacyClientDeleteAgeDaysThreshold(9999)
                .messageTemplate("text/auth-client-expiration-24h.ftl")
                .docsUrl("http://localhost:3000")
                .notificationSubject("URGENT: Auth client secret expiring within 24 hours - Action Required")
                .build();

        Instant now = Instant.now();
        List<OAuthClientExpiration> clientExpirations = List.of(
                OAuthClientExpiration.builder().clientId("expiring-soon-a").clientName("expiring-soon-a")
                        .ageInDays(90).expirationInDays(1)
                        .clientIdIssuedAt(now.minus(90, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(1, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expiring-soon-b").clientName("expiring-soon-b")
                        .ageInDays(89).expirationInDays(1)
                        .clientIdIssuedAt(now.minus(89, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(1, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("already-expired").clientName("already-expired")
                        .ageInDays(100).expirationInDays(-5)
                        .clientIdIssuedAt(now.minus(100, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.minus(5, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("expiring-in-2-days").clientName("expiring-in-2-days")
                        .ageInDays(88).expirationInDays(2)
                        .clientIdIssuedAt(now.minus(88, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(2, ChronoUnit.DAYS))
                        .build(),
                OAuthClientExpiration.builder().clientId("legacy-client").clientName("legacy-uuid")
                        .ageInDays(200)
                        .clientIdIssuedAt(now.minus(200, ChronoUnit.DAYS))
                        .build()
        );
        when(registryAuthService.getClientExpirations()).thenReturn(clientExpirations);

        PaginatedAuthClientResponse authClientResponse = PaginatedAuthClientResponse.builder()
                .totalPageCount(1).totalRecordCount(2L)
                .pageNumber(1).pageSize(2)
                .authClients(List.of(
                        AuthClientView.builder().clientId("expiring-soon-a").clientVersion(1)
                                .teams(buildAuthClientViewTeam(1L, "team-1", "team1.owner@example.com", null, "team-1-members@example.com")).build(),
                        AuthClientView.builder().clientId("expiring-soon-b").clientVersion(1)
                                .teams(buildAuthClientViewTeam(2L, "team-2", "team2.owner@example.com", null, "team-2-members@example.com")).build()
                ))
                .build();
        when(authClientService.getAuthClients(any(PaginationInput.class), any(AuthClientSearchFilter.class), any())).thenReturn(authClientResponse);

        // when
        AuthClientExpirationCheckJobStats jobStats = service.checkAuthClientsExpirations(request);

        // then: only clients with expirationInDays in [1] trigger notifications;
        // already-expired (expDays=-5), expiring-in-2-days (expDays=2), and legacy are excluded
        verify(notificationService, atLeastOnce()).sendTeamNotification(any(NotificationRequest.class));
        verify(registryService, never()).deleteAuthClient(any(), any());

        assertThat(jobStats.getExpiringAuthClients()).isEqualTo(2);
        assertThat(jobStats.getExpiredAuthClients()).isEqualTo(0);
    }

    @Test
    public void testGetClientExpirations_withLowerBound_excludesLegacyClients() {
        // given: nightly-style request with lower bound set — legacy clients must be excluded
        CheckAuthClientExpirationsRequest request = CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(1)
                .minExpirationDaysThreshold(0)
                .ageDaysThreshold(9999)
                .sendEmailNotifications(false)
                .sendSlackNotifications(false)
                .deleteExpiredClients(false)
                .deleteExpiredLegacyClients(false)
                .legacyClientDeleteAgeDaysThreshold(9999)
                .messageTemplate("text/auth-client-expiration-24h.ftl")
                .docsUrl("http://localhost:3000")
                .build();

        Instant now = Instant.now();
        List<OAuthClientExpiration> clientExpirations = List.of(
                OAuthClientExpiration.builder().clientId("legacy-client").clientName("legacy-uuid")
                        .ageInDays(200)
                        .clientIdIssuedAt(now.minus(200, ChronoUnit.DAYS))
                        .build()
        );
        when(registryAuthService.getClientExpirations()).thenReturn(clientExpirations);

        // when
        AuthClientExpirationCheckJobStats jobStats = service.checkAuthClientsExpirations(request);

        // then: legacy client excluded; no notifications, no auth client queries
        verifyNoInteractions(notificationService);
        verifyNoInteractions(authClientService);
        assertThat(jobStats.getTeamsNotified()).isEqualTo(0);
    }

    @Test
    public void testGetClientExpirations_withRecentActivityFilter_excludesInactiveClients() throws NotificationServiceException {
        // given: nightly-style request requiring lastAccessTokenIssuedAt within 24 hours
        CheckAuthClientExpirationsRequest request = CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(1)
                .minExpirationDaysThreshold(0)
                .ageDaysThreshold(9999)
                .sendEmailNotifications(true)
                .sendSlackNotifications(false)
                .deleteExpiredClients(false)
                .deleteExpiredLegacyClients(false)
                .legacyClientDeleteAgeDaysThreshold(9999)
                .messageTemplate("text/auth-client-expiration-24h.ftl")
                .docsUrl("http://localhost:3000")
                .lastAccessTokenIssuedWithinHours(24)
                .build();

        Instant now = Instant.now();
        List<OAuthClientExpiration> clientExpirations = List.of(
                // active: lastAccessToken issued 1 hour ago — should be included
                OAuthClientExpiration.builder().clientId("active-expiring").clientName("active-expiring")
                        .ageInDays(90).expirationInDays(1)
                        .clientIdIssuedAt(now.minus(90, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(1, ChronoUnit.DAYS))
                        .lastAccessTokenIssuedAt(now.minus(1, ChronoUnit.HOURS))
                        .build(),
                // stale: lastAccessToken issued 48 hours ago — should be excluded (rotated but not deleted)
                OAuthClientExpiration.builder().clientId("stale-expiring").clientName("stale-expiring")
                        .ageInDays(90).expirationInDays(1)
                        .clientIdIssuedAt(now.minus(90, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(1, ChronoUnit.DAYS))
                        .lastAccessTokenIssuedAt(now.minus(48, ChronoUnit.HOURS))
                        .build(),
                // no activity: null lastAccessTokenIssuedAt — should be excluded
                OAuthClientExpiration.builder().clientId("no-activity-expiring").clientName("no-activity-expiring")
                        .ageInDays(90).expirationInDays(1)
                        .clientIdIssuedAt(now.minus(90, ChronoUnit.DAYS))
                        .clientSecretExpiresAt(now.plus(1, ChronoUnit.DAYS))
                        .build()
        );
        when(registryAuthService.getClientExpirations()).thenReturn(clientExpirations);

        PaginatedAuthClientResponse authClientResponse = PaginatedAuthClientResponse.builder()
                .totalPageCount(1).totalRecordCount(1L).pageNumber(1).pageSize(1)
                .authClients(List.of(
                        AuthClientView.builder().clientId("active-expiring").clientVersion(1)
                                .teams(buildAuthClientViewTeam(1L, "team-1", "team1.owner@example.com", null, "team-1-members@example.com")).build()
                ))
                .build();
        when(authClientService.getAuthClients(any(PaginationInput.class), any(AuthClientSearchFilter.class), any())).thenReturn(authClientResponse);

        // when
        AuthClientExpirationCheckJobStats jobStats = service.checkAuthClientsExpirations(request);

        // then: only active-expiring triggers a notification; stale and no-activity are excluded
        verify(notificationService, times(1)).sendTeamNotification(any(NotificationRequest.class));
        assertThat(jobStats.getExpiringAuthClients()).isEqualTo(1);
        assertThat(jobStats.getTeamsNotified()).isEqualTo(1);
    }

    private List<AuthClientViewTeam> buildAuthClientViewTeam(long teamId, String teamName, String ownerEmail,
                                                             String slackChannel, String emailDistroList) {
        return List.of(
                AuthClientViewTeam.builder()
                        .id(teamId)
                        .name(teamName)
                        .slackChannel(slackChannel)
                        .emailList(emailDistroList)
                        .members(List.of(
                                AuthClientViewMember.builder().email(ownerEmail).role(TeamMemberRole.OWNER).build()
                        ))
                        .build()
        );
    }
}
