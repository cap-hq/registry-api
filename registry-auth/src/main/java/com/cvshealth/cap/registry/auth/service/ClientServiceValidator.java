// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.model.ClientRequest;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.model.exception.ApiException;
import com.cvshealth.cap.registry.auth.model.exception.BadRequestException;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
public class ClientServiceValidator {
    private static final List<ClientType> CLIENT_SECRET_CLIENT_TYPES = List.of(ClientType.USER, ClientType.NON_USER);

    private final RegisteredClientConfig registeredClientConfig;

    public ClientRequest validate(ClientRequest request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (request.getRedirectUrls() != null && !request.getRedirectUrls().isEmpty()) {
            if (ClientType.NON_USER.equals(request.getType())) {
                throw new BadRequestException(ExceptionReason.CLIENT_REDIRECT_NOT_ALLOWED);
            }
            if (request.getRedirectUrls().stream().anyMatch(StringUtils::isBlank)) {
                throw new BadRequestException(ExceptionReason.CLIENT_REDIRECT_NOT_VALID);
            }
        }
        if (request.getRefreshTokenExpireMinutes() != null && request.getRefreshTokenExpireMinutes() > 0
                && ClientType.NON_USER.equals(request.getType())) {
            throw new BadRequestException(ExceptionReason.CLIENT_REFRESH_NOT_ALLOWED);
        }
        if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
            if (request.getClientSecretExpiresAt() != null) {
                if (!isClientSecretRequired(request.getType())) {
                    throw new BadRequestException(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_NOT_ALLOWED);
                }
                if (request.getClientSecretExpiresAt().isBefore(Instant.now())) {
                    throw new BadRequestException(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_INVALID);
                }
                long maxExpiresDays = registeredClientConfig.getMaxExpireDays();
                if (maxExpiresDays > 0) {
                    Instant maxExpiresAt = Instant.now().plus(maxExpiresDays, ChronoUnit.DAYS);
                    if (request.getClientSecretExpiresAt().isAfter(maxExpiresAt)) {
                        throw new BadRequestException(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX,
                                String.format(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX.label, maxExpiresDays));
                    }
                }
            }
        }
        return request;
    }

    public ClientRequest validateUpdate(RegisteredClient registeredClient, ClientRequest request) {
        if (registeredClientConfig.isAllowMultipleSecretsPerClient() && registeredClientConfig.getMaxExpireDays() > 0) {
            if (isClientSecretRequired(request.getType()) && request.getClientSecretExpiresAt() != null) {
                long maxExpiresDays = registeredClientConfig.getMaxExpireDays();
                Instant maxExpiresAt = Optional.ofNullable(registeredClient.getClientIdIssuedAt()).orElse(Instant.now()).plus(maxExpiresDays, ChronoUnit.DAYS);

                if (registeredClient.getClientSecretExpiresAt() == null) {
                    // handle legacy registered clients with no expiration at datetime
                    Instant extensionMaxExpiresAt = Instant.now().plus(registeredClientConfig.getMissingExpireExtensionDays(), ChronoUnit.DAYS);
                    if (extensionMaxExpiresAt.compareTo(maxExpiresAt) > 0) {
                        // allow for expiration up to configured extension
                        maxExpiresDays = registeredClientConfig.getMissingExpireExtensionDays();
                        maxExpiresAt = extensionMaxExpiresAt;
                    }
                }
                if (request.getClientSecretExpiresAt().isAfter(maxExpiresAt)) {
                    throw new BadRequestException(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX,
                            String.format(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX.label, maxExpiresDays));
                }
            }
        }
        return request;
    }

    public boolean isClientSecretRequired(ClientType clientType) {
        return CLIENT_SECRET_CLIENT_TYPES.contains(clientType);
    }
}
