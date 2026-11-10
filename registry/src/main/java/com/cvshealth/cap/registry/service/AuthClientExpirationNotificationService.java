// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.AuthClientMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.model.notification.NotificationType;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.model.exception.ExceptionReason.AUTH_CLIENT_EXPIRATION_THRESHOLD_INVALID;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.AUTH_CLIENT_AGE_THRESHOLD_INVALID;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.AUTH_CLIENT_EXPIRED_RETENTION_DAYS_INVALID;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.AUTH_CLIENT_LEGACY_DELETE_THRESHOLD_INVALID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthClientExpirationNotificationService {
    private static final int AUTH_CLIENT_QUERY_BATCH_SIZE = 500;

    private final RegistryService registryService;
    private final RegistryAuthService registryAuthService;
    private final AuthClientService authClientService;
    private final NotificationService notificationService;

    static final String EXPIRING_AUTH_CLIENTS_EMAIL_SUBJECT = "Expiring Auth client secrets - Action Required";

    public AuthClientExpirationCheckJobStats checkAuthClientsExpirations(CheckAuthClientExpirationsRequest request) {
        log.info("checkAuthClientsExpirations with config options:{}", request);
        validateRequest(request);
        AuthClientExpirationCheckJobStats jobStats = new AuthClientExpirationCheckJobStats(request);

        // Retrieve the expiring/expired OAuth Clients
        Instant recentActivityCutoff = request.getLastAccessTokenIssuedWithinHours() != null
                ? Instant.now().minus(request.getLastAccessTokenIssuedWithinHours(), ChronoUnit.HOURS)
                : null;
        List<OAuthClientExpiration> oAuthClientExpirations = registryAuthService.getClientExpirations().stream()
                .filter(authClient -> {
                    Integer daysToExpiration = authClient.getExpirationInDays();
                    if (daysToExpiration != null) {
                        return daysToExpiration <= request.getExpirationDaysThreshold() &&
                               (request.getMinExpirationDaysThreshold() == null || daysToExpiration >= request.getMinExpirationDaysThreshold());
                    }
                    // Legacy clients: only for notifications where no lower bound is set
                    return request.getMinExpirationDaysThreshold() == null &&
                           authClient.getAgeInDays() >= request.getAgeDaysThreshold();
                })
                .filter(authClient -> recentActivityCutoff == null ||
                        (authClient.getLastAccessTokenIssuedAt() != null &&
                         authClient.getLastAccessTokenIssuedAt().isAfter(recentActivityCutoff)))
                .toList();
        recordAuthClientExpirationStats(jobStats, oAuthClientExpirations);

        // Retrieve the auth clients
        Set<String> clientIds = oAuthClientExpirations.stream()
                .map(oAuthClient -> oAuthClient.getClientSecretExpiresAt() != null ? oAuthClient.getClientName() : oAuthClient.getClientId())
                .collect(Collectors.toSet());
        List<AuthClientView> authClients = getAuthClients(new ArrayList<>(clientIds));

        // Associate the OAuth client expiration info with the auth client
        List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients = getExpiringAuthClientPair(authClients, oAuthClientExpirations);

        sendNotifications(request, expiringAuthClients, jobStats);
        deleteExpiredAuthClients(request, expiringAuthClients, jobStats);

        log.info("checkAuthClientsExpirations complete - jobStats: {}", jobStats);
        return jobStats;
    }

    private static void validateRequest(CheckAuthClientExpirationsRequest request) {
        Validate.isTrue(request.getExpirationDaysThreshold() > 0, AUTH_CLIENT_EXPIRATION_THRESHOLD_INVALID.label);
        Validate.isTrue(request.getAgeDaysThreshold() > 0, AUTH_CLIENT_AGE_THRESHOLD_INVALID.label);
        Validate.isTrue(request.getExpiredClientRetentionDays() >= 0, AUTH_CLIENT_EXPIRED_RETENTION_DAYS_INVALID.label);
        Validate.isTrue(request.getLegacyClientDeleteAgeDaysThreshold() > 0, AUTH_CLIENT_LEGACY_DELETE_THRESHOLD_INVALID.label);
    }

    /**
     * Return the associated matching AuthClientView / OAuthClientExpiration pair, filtering out AuthClientViews that are not expiring.
     */
    List<ModelPair<AuthClientView, OAuthClientExpiration>> getExpiringAuthClientPair(List<AuthClientView> authClients, List<OAuthClientExpiration> oAuthClientExpirations) {
        Map<String, AuthClientView> authClientMap = authClients.stream().collect(Collectors.toMap(AuthClientExpirationNotificationService::getOAuthClientId, Function.identity()));
        return oAuthClientExpirations.stream()
                .map(oAuthClientExpiration -> {
                    AuthClientView authClient = authClientMap.get(oAuthClientExpiration.getClientId());
                    return ModelPair.<AuthClientView, OAuthClientExpiration>builder().first(authClient).second(oAuthClientExpiration).build();
                })
                .filter(result -> result.getFirst() != null)
                .toList();
    }

    List<AuthClientView> getAuthClients(List<String> clientIds) {
        List<AuthClientView> authClients = new ArrayList<>();
        int clientIdCount = clientIds.size();
        int batchSize = AUTH_CLIENT_QUERY_BATCH_SIZE;    // for simplicity, use a batch size half of paginated query page size, to retrieve all auth clients versions in a single page.
        for (int i = 0; i < clientIdCount; i += batchSize) {
            int batchEndIndex = Math.min(i + batchSize, clientIdCount);
            List<String> clientIdBatch = clientIds.subList(i, batchEndIndex);

            Integer totalPages = null;
            PaginationInput paginationInput = new PaginationInput(1, batchSize * 2);
            while (totalPages == null || paginationInput.getPageNumber() <= totalPages) {
                PaginatedAuthClientResponse paginatedResponse = authClientService.getAuthClients(paginationInput,
                        AuthClientSearchFilter.builder().clientIds(clientIdBatch).build(),null);
                authClients.addAll(paginatedResponse.getAuthClients());

                totalPages = paginatedResponse.getTotalPageCount();
                paginationInput.setPageNumber(paginationInput.getPageNumber() + 1);
            }
        }
        return authClients;
    }

    void sendNotifications(CheckAuthClientExpirationsRequest request,
                           List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients,
                           AuthClientExpirationCheckJobStats jobStats) {

        // group expiring auth clients by team for notification
        Map<AuthClientViewTeam, List<ModelPair<AuthClientView, OAuthClientExpiration>>> teamExpiringAuthClientMap = expiringAuthClients.stream()
                .collect(Collectors.groupingBy(authClientPair -> authClientPair.getFirst().getTeams().get(0)));
        teamExpiringAuthClientMap.forEach((authClientTeam, teamExpiringAuthClients) -> {
            if (request.isSendNotifications()) {
                sendTeamNotification(request, authClientTeam, teamExpiringAuthClients);
                jobStats.addTeamNotified();
            } else {
                // just log team client expiration info
                for (ModelPair<AuthClientView, OAuthClientExpiration> expiringAuthClient : expiringAuthClients) {
                    logAuthClientExpiration(authClientTeam, expiringAuthClient);
                }
            }
        });
    }

    void sendTeamNotification(CheckAuthClientExpirationsRequest request, AuthClientViewTeam authClientTeam,
                              List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients) {

        try {
            List<NotificationType> notificationTypes = new ArrayList<>();
            if (request.isSendEmailNotifications()) {
                notificationTypes.add(NotificationType.EMAIL);
            }
            if (request.isSendSlackNotifications()) {
                notificationTypes.add(NotificationType.SLACK);
            }
            Map<String, Object> messageContext = buildTeamNotificationMessageContext(request, authClientTeam, expiringAuthClients);
            String subject = request.getNotificationSubject() != null ? request.getNotificationSubject() : EXPIRING_AUTH_CLIENTS_EMAIL_SUBJECT;

            notificationService.sendTeamNotification(NotificationRequest.builder()
                .notificationTypes(notificationTypes)
                .teamName(authClientTeam.getName())
                .subject(subject)
                .template(request.getMessageTemplate())
                .model(messageContext)
                .build());
        } catch (NotificationServiceException | BadRequestException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "notification-service",
                    ExceptionReason.AUTH_CLIENT_EXPIRATION_NOTIFICATION_ERROR, "team notification error");
        }
    }

    Map<String, Object> buildTeamNotificationMessageContext(CheckAuthClientExpirationsRequest request, AuthClientViewTeam authClientViewTeam, List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients) {
        Map<String, Object> messageContext = new HashMap<>();
        messageContext.put("teamName", authClientViewTeam.getName());
        messageContext.put("expirationDaysThreshold", request.getExpirationDaysThreshold());
        messageContext.put("expirationHoursThreshold", request.getExpirationDaysThreshold() * 24);

        // notification message for legacy auth clients without a set expiration date, but are expiring / expired based on auth client age
        Optional<ModelPair<AuthClientView, OAuthClientExpiration>> expiringLegacyAuthClient = expiringAuthClients.stream()
                .filter(authClient -> authClient.getSecond().getExpirationInDays() == null && authClient.getSecond().getAgeInDays() >= request.getAgeDaysThreshold())
                .findFirst();
        messageContext.put("legacyClientDeleteAgeDaysThreshold", request.getLegacyClientDeleteAgeDaysThreshold());
        messageContext.put("inRegistryOrCapUI",  notificationService.registryOrCapUI());
        messageContext.put("authClientRotationDocsUrl",  request.getDocsUrl());
        messageContext.put("expiredClientRetentionDays", request.getExpiredClientRetentionDays());
        if (request.getLastAccessTokenIssuedWithinHours() != null) {
            messageContext.put("lastAccessTokenIssuedWithinHours", request.getLastAccessTokenIssuedWithinHours());
        }

        // order auth clients by client ID with expiration details
        expiringAuthClients.sort(Comparator.comparing(result -> result.getSecond().getClientId()));

        List<Map<String, Object>> clientViews = new ArrayList<>(expiringAuthClients.size());
        for (ModelPair<AuthClientView, OAuthClientExpiration> expiringAuthClient : expiringAuthClients) {
            logAuthClientExpiration(authClientViewTeam, expiringAuthClient);

            Map<String, Object> attributes = new HashMap<>();
            AuthClientView authClient = expiringAuthClient.getFirst();
            attributes.put("clientId", authClient.getClientId());
            attributes.put("clientVersion", authClient.getClientVersion());

            OAuthClientExpiration clientExpiration = expiringAuthClient.getSecond();
            attributes.put("expirationType", getClientViewExpirationType(request, clientExpiration));
            attributes.put("ageInDays", clientExpiration.getAgeInDays());
            attributes.put("expirationInDays", clientExpiration.getExpirationInDays());
            attributes.put("issuedAt", dateAtTimeFormat(clientExpiration.getClientIdIssuedAt()));
            attributes.put("expiresAt", dateAtTimeFormat(clientExpiration.getClientSecretExpiresAt()));
            String lastAccessTokenIssuedAt = dateAtTimeFormat(clientExpiration.getLastAccessTokenIssuedAt());
            attributes.put("lastAccessTokenIssuedAt", lastAccessTokenIssuedAt == null ? "'UNKNOWN'" : lastAccessTokenIssuedAt);
            clientViews.add(attributes);
        }
        messageContext.put("clients", clientViews);
        return messageContext;
    }

    String getClientViewExpirationType(CheckAuthClientExpirationsRequest request, OAuthClientExpiration clientExpiration) {
        if (clientExpiration.getClientSecretExpiresAt() == null) {
            if (canDeleteLegacyAuthClient(request, clientExpiration.getAgeInDays())) {
                return "deleted-no-expiration";
            }
            return "no-expiration";
        } else if (clientExpiration.getExpirationInDays() < 0) {
            if (canDeleteAuthClient(request, clientExpiration.getExpirationInDays())) {
                return "deleted";
            }
            return "expired";
        } else {
            return "expiring";
        }
    }

    private void logAuthClientExpiration(AuthClientViewTeam authClientViewTeam, ModelPair<AuthClientView, OAuthClientExpiration> expiringAuthClient) {
        AuthClientView authClient = expiringAuthClient.getFirst();
        OAuthClientExpiration oAuthClientExpiration = expiringAuthClient.getSecond();
        log.info("Auth Client Expiration Notification: Team[{}]: ClientId[{} (v{})], daysToExpiration[{}], ageInDays[{}]",
                authClientViewTeam.getName(), authClient.getClientId(), authClient.getClientVersion(),
                oAuthClientExpiration.getExpirationInDays(), oAuthClientExpiration.getAgeInDays());
    }

    void deleteExpiredAuthClients(CheckAuthClientExpirationsRequest request,
                                  List<ModelPair<AuthClientView, OAuthClientExpiration>> expiringAuthClients,
                                  AuthClientExpirationCheckJobStats jobStats) {
        if (!request.isDeleteExpiredClients()) {
            return;
        }
        expiringAuthClients.forEach(expiringAuthClient -> {
            AuthClientView authClient = expiringAuthClient.getFirst();
            OAuthClientExpiration clientExpiration = expiringAuthClient.getSecond();
            if (clientExpiration.getExpirationInDays() == null) {
                if (canDeleteLegacyAuthClient(request, clientExpiration.getAgeInDays())) {
                    registryService.deleteAuthClient(authClient.getClientId(), authClient.getClientVersion());
                    jobStats.addDeletedAuthClient();
                    jobStats.addDeletedLegacyAuthClient();
                }
            } else if (canDeleteAuthClient(request, clientExpiration.getExpirationInDays())) {
                registryService.deleteAuthClient(authClient.getClientId(), authClient.getClientVersion());
                jobStats.addDeletedAuthClient();
            }
        });
    }

    static String getOAuthClientId(AuthClientView authClient) {
        return AuthClientMapper.buildAuthClientId(authClient.getClientId(), authClient.getClientVersion());
    }

    static void recordAuthClientExpirationStats(AuthClientExpirationCheckJobStats jobStats, List<OAuthClientExpiration> oAuthClientExpirations) {
        oAuthClientExpirations.forEach(oAuthClientExpiration -> {
            if (oAuthClientExpiration.getClientSecretExpiresAt() == null) {
                if (oAuthClientExpiration.getAgeInDays() > jobStats.getJobConfig().getLegacyClientDeleteAgeDaysThreshold()) {
                    jobStats.addExpiredAuthClient();
                    jobStats.addExpiredLegacyAuthClient();
                } else {
                    jobStats.addExpiringAuthClient();
                    jobStats.addExpiringLegacyAuthClient();
                }
            } else if (oAuthClientExpiration.getExpirationInDays() > 0) {
                jobStats.addExpiringAuthClient();
            } else {
                jobStats.addExpiredAuthClient();
            }
        });
    }

    // Can delete auth client expired and past delete retention period
    static boolean canDeleteAuthClient(CheckAuthClientExpirationsRequest request, int expirationDays) {
        return request.isDeleteExpiredClients() && (expirationDays + request.getExpiredClientRetentionDays() < 0);
    }

    // Can delete legacy auth client without expiration date if more than age threshold and delete retention period
    static boolean canDeleteLegacyAuthClient(CheckAuthClientExpirationsRequest request, Integer ageInDays) {
        return request.isDeleteExpiredLegacyClients() && ageInDays != null
                && ageInDays > request.getLegacyClientDeleteAgeDaysThreshold() + request.getExpiredClientRetentionDays();
    }

    private String dateAtTimeFormat(Instant dateTime) {
        return notificationService.dateAtTimeFormat(dateTime);
    }
}
