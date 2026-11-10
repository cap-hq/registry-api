// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import com.cvshealth.cap.registry.config.AuthClientCleanupJobConfig;
import com.cvshealth.cap.registry.config.AuthClientExpirationCheckJobConfig;
import com.cvshealth.cap.registry.config.AuthClientExpirationCheckNightlyJobConfig;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.job.AuthClientCleanupJobResponse;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.service.AuthClientCleanupService;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.AuthClientExpirationNotificationService;
import com.cvshealth.cap.registry.service.AuthClientService;
import com.cvshealth.cap.registry.service.AzureApplicationService;
import com.cvshealth.cap.registry.service.DefaultRedirectUriValidator;
import com.cvshealth.cap.registry.service.TeamRollupCacheService;
import com.cvshealth.cap.registry.service.notification.SlackService;
import com.slack.api.model.Conversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTests {

    @InjectMocks
    private AdminController adminController;

    @Mock
    private RegistryService registryService;
    @Mock
    private AzureApplicationService azureApplicationService;
    @Mock
    private AuthClientExpirationNotificationService authClientExpirationNotificationService;
    @Mock
    private AuthClientExpirationCheckJobConfig authClientExpirationCheckJobConfig;
    @Mock
    private AuthClientExpirationCheckNightlyJobConfig authClientExpirationCheckNightlyJobConfig;
    @Mock
    private AuthClientExpirationCheckJobStats authClientExpirationCheckJobStats;
    @Mock
    private SlackService slackService;
    @Mock
    private TeamRollupCacheService teamRollupCacheService;
    @Mock
    private AuthClientCleanupJobConfig authClientCleanupJobConfig;
    @Mock
    private AuthClientCleanupService authClientCleanupService;
    @Mock
    private AuthClientService authClientService;
    @Mock
    private DefaultRedirectUriValidator redirectUriValidator;

    @Test
    public void checkAuthClientExpirationsRequest_withNoRequestBody() {
        // given
        when(authClientExpirationCheckJobConfig.getExpirationDaysThreshold()).thenReturn(30);
        when(authClientExpirationCheckJobConfig.getAgeDaysThreshold()).thenReturn(60);
        when(authClientExpirationCheckJobConfig.isSendEmailNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isSendSlackNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.getExpiredClientRetentionDays()).thenReturn(10);
        when(authClientExpirationCheckJobConfig.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(authClientExpirationCheckJobConfig.getMessageTemplate()).thenReturn("text/auth-client-expiration.ftl");
        when(authClientExpirationCheckJobConfig.getDocsUrl()).thenReturn("https://docs.example.com");

        // when
        CheckAuthClientExpirationsRequest request = adminController.buildCheckAuthClientExpirationsRequest(null);

        // then
        assertThat(request).isNotNull();
        verifyNoMoreInteractions(authClientExpirationCheckJobConfig);
        assertThat(request.getExpirationDaysThreshold()).isEqualTo(30);
        assertThat(request.getAgeDaysThreshold()).isEqualTo(60);
        assertThat(request.isSendEmailNotifications()).isTrue();
        assertThat(request.isSendSlackNotifications()).isTrue();
        assertThat(request.isDeleteExpiredClients()).isTrue();
        assertThat(request.isDeleteExpiredLegacyClients()).isTrue();
        assertThat(request.getExpiredClientRetentionDays()).isEqualTo(10);
        assertThat(request.getLegacyClientDeleteAgeDaysThreshold()).isEqualTo(90);
    }

    @Test
    public void checkAuthClientExpirationsRequest_withRequestBody() {
        // given
        when(authClientExpirationCheckJobConfig.getExpirationDaysThreshold()).thenReturn(30);
        when(authClientExpirationCheckJobConfig.getAgeDaysThreshold()).thenReturn(60);
        when(authClientExpirationCheckJobConfig.isSendEmailNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isSendSlackNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.getExpiredClientRetentionDays()).thenReturn(10);
        when(authClientExpirationCheckJobConfig.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(authClientExpirationCheckJobConfig.getMessageTemplate()).thenReturn("text/auth-client-expiration.ftl");
        when(authClientExpirationCheckJobConfig.getDocsUrl()).thenReturn("https://docs.example.com");
        Map<String, Object> requestBody = Map.of(
                "expirationDaysThreshold", 14,
                "ageDaysThreshold", 80,
                "sendSlackNotifications", false,
                "deleteExpiredLegacyClients", false
        );

        // when
        CheckAuthClientExpirationsRequest request = adminController.buildCheckAuthClientExpirationsRequest(requestBody);

        // then
        assertThat(request).isNotNull();
        verifyNoMoreInteractions(authClientExpirationCheckJobConfig);
        assertThat(request.getExpirationDaysThreshold()).isEqualTo(14);
        assertThat(request.getAgeDaysThreshold()).isEqualTo(80);
        assertThat(request.isSendEmailNotifications()).isTrue();
        assertThat(request.isSendSlackNotifications()).isFalse();
        assertThat(request.isDeleteExpiredClients()).isTrue();
        assertThat(request.isDeleteExpiredLegacyClients()).isFalse();
        assertThat(request.getExpiredClientRetentionDays()).isEqualTo(10);
        assertThat(request.getLegacyClientDeleteAgeDaysThreshold()).isEqualTo(90);
    }

    @Test
    public void authClientExpirationCheck_default() {
        // given
        when(authClientExpirationCheckJobConfig.getExpirationDaysThreshold()).thenReturn(30);
        when(authClientExpirationCheckJobConfig.getAgeDaysThreshold()).thenReturn(60);
        when(authClientExpirationCheckJobConfig.isSendEmailNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isSendSlackNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.getExpiredClientRetentionDays()).thenReturn(10);
        when(authClientExpirationCheckJobConfig.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);

        //when
        ResponseEntity<String> response = adminController.authClientExpirationCheck(null);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    public void authClientExpirationCheck_asyncFalse() {
        // given
        when(authClientExpirationCheckJobConfig.getExpirationDaysThreshold()).thenReturn(30);
        when(authClientExpirationCheckJobConfig.getAgeDaysThreshold()).thenReturn(60);
        when(authClientExpirationCheckJobConfig.isSendEmailNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isSendSlackNotifications()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(authClientExpirationCheckJobConfig.getExpiredClientRetentionDays()).thenReturn(10);
        when(authClientExpirationCheckJobConfig.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);

        when(authClientExpirationNotificationService.checkAuthClientsExpirations(any(CheckAuthClientExpirationsRequest.class)))
                .thenReturn(authClientExpirationCheckJobStats);
        Map<String, Object> requestBody = Map.of("runAsync", false);

        //when
        ResponseEntity<String> response = adminController.authClientExpirationCheck(requestBody);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    public void slackChannelInfo() throws SlackServiceException {
        // given
        String channelId = "Channel-id";
        Conversation conversation = Conversation.builder().id(channelId).name("channel-name").isPrivate(true).numOfMembers(3).build();
        when(slackService.getChannelInfo(channelId)).thenReturn(conversation);
        // when
        AdminSlackChannelInfo channelInfo = adminController.slackChannelInfo(channelId).getBody();

        // then
        assertThat(channelInfo).isNotNull();
        assertThat(channelInfo.getId()).isEqualTo(channelId);
        assertThat(channelInfo.getName()).isEqualTo(conversation.getName());
        assertThat(channelInfo.isPrivate()).isEqualTo(conversation.isPrivate());
        assertThat(channelInfo.getMemberCount()).isEqualTo(conversation.getNumOfMembers());
    }

    @Test
    public void slackChannelInfo_notFound() throws SlackServiceException {
        // given
        String channelId = "Channel-id";
        when(slackService.getChannelInfo(channelId)).thenReturn(null);
        // when
        AdminSlackChannelInfo channelInfo = adminController.slackChannelInfo(channelId).getBody();

        // then
        assertThat(channelInfo).isNull();
    }

    @Test
    public void refreshTeamRollupCache_returnsOkAndSubmitsJob() {
        // when
        ResponseEntity<String> response = adminController.refreshTeamRollupCache(null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isEqualTo("refreshTeamRollupCache job submitted - see logs");
        verify(teamRollupCacheService, timeout(2000)).refreshCache();
    }

    @Test
    public void patchApplicationExternalId() {
        // given
        String teamName = "teamName";
        String applicationName = "applicationName";
        String oldExternalId = "oldExternalId";
        String newExternalId = "newExternalId";
        ApplicationExternalIdPatchRequest patchRequest = ApplicationExternalIdPatchRequest.builder()
                .teamName(teamName).applicationName(applicationName)
                .oldExternalId(oldExternalId).newExternalId(newExternalId).build();
        Application application = Application.builder().id(1L).teamId(100L).externalId(newExternalId).build();
        when(registryService.updateApplicationExternalId(patchRequest)).thenReturn(application);

        // when
        Application result = adminController.patchApplicationExternalId(patchRequest).getBody();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getExternalId()).isEqualTo(newExternalId);
    }

    @Test
    public void authClientRedirectUriCompliance_noViolations_returnsEmptyList() {
        // given
        AuthClientView client = AuthClientView.builder()
                .clientId("client-1").clientVersion(1).type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback"))
                .teams(List.of()).build();
        when(authClientService.getAuthClients(AuthClientType.USER)).thenReturn(List.of(client));
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of());

        // when
        ResponseEntity<List<AuthClientRedirectUriComplianceResult>> response = adminController.authClientRedirectUriCompliance(null);

        // then
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    public void authClientRedirectUriCompliance_withViolations_returnsNonCompliantClients() {
        // given
        AuthClientType authClientType = AuthClientType.USER_PKCE;
        AuthClientViewTeam team = AuthClientViewTeam.builder().name("my-team").build();
        AuthClientView client = AuthClientView.builder()
                .clientId("client-2").clientVersion(1).type(authClientType)
                .redirectUrls(List.of("http://evil.example.com/callback"))
                .teams(List.of(team)).build();
        when(authClientService.getAuthClients(AuthClientType.USER)).thenReturn(List.of());
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(client));
        doThrow(new BadRequestException(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED,
                "Redirect URI domain is not allowed: http://evil.example.com/callback"))
                .when(redirectUriValidator).validate("http://evil.example.com/callback", authClientType);

        // when
        List<AuthClientRedirectUriComplianceResult> results = adminController.authClientRedirectUriCompliance(null).getBody();

        // then
        assertThat(results).hasSize(1);
        AuthClientRedirectUriComplianceResult result = results.get(0);
        assertThat(result.getClientId()).isEqualTo("client-2");
        assertThat(result.getType()).isEqualTo(AuthClientType.USER_PKCE);
        assertThat(result.getTeamNames()).containsExactly("my-team");
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRedirectUri()).isEqualTo("http://evil.example.com/callback");
        assertThat(result.getViolations().get(0).getReason()).isEqualTo(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.name());
    }

    @Test
    public void authClientRedirectUriCompliance_queriesOnlyUserAndUserPkceTypes() {
        // when
        List<AuthClientRedirectUriComplianceResult> results = adminController.authClientRedirectUriCompliance(null).getBody();

        // then
        assertThat(results).isEmpty();
        verify(authClientService).getAuthClients(AuthClientType.USER);
        verify(authClientService).getAuthClients(AuthClientType.USER_PKCE);
        verify(authClientService, never()).getAuthClients(AuthClientType.NON_USER);
        verifyNoInteractions(redirectUriValidator);
    }

    @Test
    public void authClientRedirectUriCompliance_checksClientsOfBothTypes() {
        // given
        AuthClientView userClient = AuthClientView.builder()
                .clientId("client-4").clientVersion(1).type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/cb")).teams(List.of()).build();
        AuthClientView pkceClient = AuthClientView.builder()
                .clientId("client-5").clientVersion(1).type(AuthClientType.USER_PKCE)
                .redirectUrls(List.of("https://app2.example.com/cb")).teams(List.of()).build();
        when(authClientService.getAuthClients(AuthClientType.USER)).thenReturn(List.of(userClient));
        when(authClientService.getAuthClients(AuthClientType.USER_PKCE)).thenReturn(List.of(pkceClient));

        // when
        adminController.authClientRedirectUriCompliance(null);

        // then
        verify(redirectUriValidator).validate("https://app.example.com/cb", AuthClientType.USER);
        verify(redirectUriValidator).validate("https://app2.example.com/cb", AuthClientType.USER_PKCE);
    }

    @Test
    public void authClientRedirectUriCompliance_withClientIdFilter_usesDirectLookup() throws ApiException {
        // given
        AuthClientView v1 = AuthClientView.builder()
                .clientId("my-client").clientVersion(1).type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/cb")).teams(List.of()).build();
        AuthClientView v2 = AuthClientView.builder()
                .clientId("my-client").clientVersion(2).type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/cb")).teams(List.of()).build();
        when(authClientService.getAuthClients("my-client")).thenReturn(List.of(v1, v2));

        // when
        List<AuthClientRedirectUriComplianceResult> results = adminController.authClientRedirectUriCompliance("my-client").getBody();

        // then
        assertThat(results).isEmpty();
        verify(authClientService).getAuthClients("my-client");
        verify(authClientService, never()).getAuthClients(any(AuthClientType.class));
    }

    @Test
    public void authClientRedirectUriCompliance_withClientIdFilter_returnsViolationsForAllVersions() throws ApiException {
        // given
        AuthClientType authClientType = AuthClientType.USER;
        String badUri = "http://evil.example.com/cb";
        AuthClientView v1 = AuthClientView.builder()
                .clientId("my-client").clientVersion(1).type(authClientType)
                .redirectUrls(List.of(badUri)).teams(List.of()).build();
        AuthClientView v2 = AuthClientView.builder()
                .clientId("my-client").clientVersion(2).type(authClientType)
                .redirectUrls(List.of(badUri)).teams(List.of()).build();
        when(authClientService.getAuthClients("my-client")).thenReturn(List.of(v1, v2));
        doThrow(new BadRequestException(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED, "domain not allowed"))
                .when(redirectUriValidator).validate(badUri, authClientType);

        // when
        List<AuthClientRedirectUriComplianceResult> results = adminController.authClientRedirectUriCompliance("my-client").getBody();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getClientVersion()).isEqualTo(1);
        assertThat(results.get(1).getClientVersion()).isEqualTo(2);
    }

    @Test
    public void deleteAuthClient_teamOwnsClient_returns204() throws ApiException {
        // given
        AuthClientViewTeam team = AuthClientViewTeam.builder().name("my-team").build();
        AuthClientView authClient = AuthClientView.builder()
                .clientId("my-client").clientVersion(2)
                .teams(List.of(team)).build();
        when(authClientService.getAuthClient("my-client", 2, null)).thenReturn(authClient);
        AdminDeleteAuthClientRequest request = AdminDeleteAuthClientRequest.builder()
                .teamName("my-team").clientId("my-client").clientVersion(2).build();

        // when
        ResponseEntity<Void> response = adminController.deleteAuthClient(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        verify(registryService).deleteAuthClient("my-client", 2);
    }

    @Test
    public void deleteAuthClient_teamOwnsClient_teamNameCaseInsensitive() throws ApiException {
        // given
        AuthClientViewTeam team = AuthClientViewTeam.builder().name("My-Team").build();
        AuthClientView authClient = AuthClientView.builder()
                .clientId("my-client").clientVersion(1)
                .teams(List.of(team)).build();
        when(authClientService.getAuthClient("my-client", 1, null)).thenReturn(authClient);
        AdminDeleteAuthClientRequest request = AdminDeleteAuthClientRequest.builder()
                .teamName("my-team").clientId("my-client").clientVersion(1).build();

        // when
        ResponseEntity<Void> response = adminController.deleteAuthClient(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        verify(registryService).deleteAuthClient("my-client", 1);
    }

    @Test
    public void deleteAuthClient_teamMismatch_throwsBadRequest() throws ApiException {
        // given
        AuthClientViewTeam team = AuthClientViewTeam.builder().name("other-team").build();
        AuthClientView authClient = AuthClientView.builder()
                .clientId("my-client").clientVersion(1)
                .teams(List.of(team)).build();
        when(authClientService.getAuthClient("my-client", 1, null)).thenReturn(authClient);
        AdminDeleteAuthClientRequest request = AdminDeleteAuthClientRequest.builder()
                .teamName("wrong-team").clientId("my-client").clientVersion(1).build();

        // when / then
        assertThatThrownBy(() -> adminController.deleteAuthClient(request))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getReason())
                        .isEqualTo(ExceptionReason.AUTH_CLIENT_TEAM_MISMATCH));
        verify(registryService, never()).deleteAuthClient(any(), any());
    }

    @Test
    public void deleteAuthClient_clientHasNoTeams_throwsBadRequest() throws ApiException {
        AuthClientView authClient = AuthClientView.builder()
                .clientId("my-client").clientVersion(1)
                .teams(List.of()).build();
        when(authClientService.getAuthClient("my-client", 1, null)).thenReturn(authClient);
        AdminDeleteAuthClientRequest request = AdminDeleteAuthClientRequest.builder()
                .teamName("my-team").clientId("my-client").clientVersion(1).build();

        assertThatThrownBy(() -> adminController.deleteAuthClient(request))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getReason())
                        .isEqualTo(ExceptionReason.AUTH_CLIENT_TEAM_MISMATCH));
        verify(registryService, never()).deleteAuthClient(any(), any());
    }

    @Test
    public void deleteAuthClient_clientHasNullTeams_throwsBadRequest() throws ApiException {
        AuthClientView authClient = AuthClientView.builder()
                .clientId("my-client").clientVersion(1).build();
        when(authClientService.getAuthClient("my-client", 1, null)).thenReturn(authClient);
        AdminDeleteAuthClientRequest request = AdminDeleteAuthClientRequest.builder()
                .teamName("my-team").clientId("my-client").clientVersion(1).build();

        assertThatThrownBy(() -> adminController.deleteAuthClient(request))
                .isInstanceOf(BadRequestException.class);
        verify(registryService, never()).deleteAuthClient(any(), any());
    }

    @Test
    public void deleteAuthClient_clientNotFound_throwsRecordNotFoundException() throws ApiException {
        // given
        when(authClientService.getAuthClient("missing-client", 1, null))
                .thenThrow(new RecordNotFoundException(ExceptionReason.AUTH_CLIENT_NOT_FOUND));
        AdminDeleteAuthClientRequest request = AdminDeleteAuthClientRequest.builder()
                .teamName("my-team").clientId("missing-client").clientVersion(1).build();

        // when / then
        assertThatThrownBy(() -> adminController.deleteAuthClient(request))
                .isInstanceOf(RecordNotFoundException.class);
        verify(registryService, never()).deleteAuthClient(any(), any());
    }

    @Test
    public void authClientCleanup_dryRun_returnsCandidatesWithoutDeleting() {
        // given
        when(authClientCleanupJobConfig.getMinAgeDays()).thenReturn(30);
        when(authClientCleanupJobConfig.getInactivityDays()).thenReturn(30);
        AuthClientView stale = AuthClientView.builder().clientId("pkce-1").clientVersion(1).build();
        when(authClientCleanupService.getCandidates(30, 30)).thenReturn(List.of(stale));

        // when
        ResponseEntity<AuthClientCleanupJobResponse> response = adminController.authClientCleanup(Map.of("dryRun", true));

        // then
        assertThat(response.getStatusCode()).isEqualTo(OK);
        AuthClientCleanupJobResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isDryRun()).isTrue();
        assertThat(body.getCandidates()).containsExactly(stale);
        assertThat(body.getDeleted()).isZero();
        assertThat(body.getFailed()).isZero();
        verify(authClientCleanupService).getCandidates(30, 30);
        verify(authClientCleanupService, never()).runCleanup(anyInt(), anyInt());
    }

    @Test
    public void authClientCleanup_actualRun_delegatesToServiceAndReturnsResult() {
        // given
        when(authClientCleanupJobConfig.getMinAgeDays()).thenReturn(30);
        when(authClientCleanupJobConfig.getInactivityDays()).thenReturn(30);
        AuthClientView stale = AuthClientView.builder().clientId("pkce-1").clientVersion(1).build();
        AuthClientCleanupJobResponse serviceResult = AuthClientCleanupJobResponse.builder()
                .dryRun(false).candidates(List.of(stale)).deleted(1).failed(0).build();
        when(authClientCleanupService.runCleanup(30, 30)).thenReturn(serviceResult);

        // when
        ResponseEntity<AuthClientCleanupJobResponse> response = adminController.authClientCleanup(null);

        // then
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isEqualTo(serviceResult);
        verify(authClientCleanupService).runCleanup(30, 30);
        verify(authClientCleanupService, never()).getCandidates(anyInt(), anyInt());
    }

    @Test
    public void authClientCleanup_withOverriddenThresholds_passesOverriddenValuesToService() {
        // given
        when(authClientCleanupJobConfig.getMinAgeDays()).thenReturn(30);
        when(authClientCleanupJobConfig.getInactivityDays()).thenReturn(30);
        AuthClientView candidate = AuthClientView.builder().clientId("pkce-1").clientVersion(1).build();
        when(authClientCleanupService.getCandidates(10, 10)).thenReturn(List.of(candidate));

        // when
        ResponseEntity<AuthClientCleanupJobResponse> response = adminController.authClientCleanup(
                Map.of("dryRun", true, "minAgeDays", 10, "inactivityDays", 10));

        // then
        assertThat(response.getBody().getCandidates()).containsExactly(candidate);
        verify(authClientCleanupService).getCandidates(10, 10);
    }
}
