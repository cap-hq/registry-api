// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.AuthClientRequest;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthClientServiceValidator {
    protected static final int CLIENT_ID_MAX_LENGTH = 30;
    private static final String ALPHANUMERIC_REGEX = "^[a-zA-Z][a-zA-Z0-9-]+$";
    private static final List<AuthClientType> AUTH_TYPES_WITH_REDIRECT_URIS = List.of(AuthClientType.USER, AuthClientType.USER_PKCE);

    private final DefaultRedirectUriValidator redirectUriValidator;

    public AuthClientRequest validate(AuthClientRequest request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (request.getType() == null) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_TYPE_REQUIRED);
        }
        if (StringUtils.isEmpty(request.getClientId())) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_CLIENT_ID_REQUIRED);
        }
        if (request.getRedirectUrls() != null && !request.getRedirectUrls().isEmpty()
                && AuthClientType.NON_USER.equals(request.getType())) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_REDIRECT_NOT_ALLOWED);
        }
        if ((request.getRedirectUrls() == null || request.getRedirectUrls().isEmpty())
                && AUTH_TYPES_WITH_REDIRECT_URIS.contains(request.getType())) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_REDIRECT_REQUIRED);
        }

        if (request.getClientId().length() > CLIENT_ID_MAX_LENGTH) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_CLIENT_ID_INVALID_LENGTH,
                    String.format(ExceptionReason.AUTH_CLIENT_CLIENT_ID_INVALID_LENGTH.label, CLIENT_ID_MAX_LENGTH));
        }

        if (!request.getClientId().matches(ALPHANUMERIC_REGEX)) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_CLIENT_ID_INVALID_FORMAT);
        }
        return request;
    }

    public void validateBetweenVersions(AuthClientRequest newVersion, OAuthClient currentVersion) throws ApiException {
        validateTypeBetweenVersions(newVersion.getType(), currentVersion.getType());
        if (AUTH_TYPES_WITH_REDIRECT_URIS.contains(newVersion.getType())) {
            validateRedirectUrlsBetweenVersions(newVersion.getRedirectUrls(), currentVersion.getRedirectUrls());
        }
    }

    public void validateUpdate(AuthClientRequest updateRequest, OAuthClient currentVersion, OAuthClient previousVersion) throws ApiException {
        // If previous version exists, check immutable fields
        if (updateRequest.getRedirectUrls() != null && previousVersion != null) {
            validateBetweenVersions(updateRequest, previousVersion);
        } else {
            validateTypeBetweenVersions(updateRequest.getType(), currentVersion.getType());
            validateRedirectUris(updateRequest.getRedirectUrls(), currentVersion.getRedirectUrls(), updateRequest.getType());
        }
    }

    private void validateTypeBetweenVersions(AuthClientType newType, AuthClientType oldType) throws ApiException {
        if (!newType.equals(oldType)) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_TYPE_IMMUTABLE);
        }
    }

    private void validateRedirectUrlsBetweenVersions(List<String> newUrls, List<String> oldUrls) throws ApiException {
        List<String> a = newUrls == null ? List.of() : newUrls.stream().sorted().toList();
        List<String> b = oldUrls == null ? List.of() : oldUrls.stream().sorted().toList();
        if (!a.equals(b)) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE);
        }
    }

    public void validateRedirectUris(AuthClientRequest request) {
        if (AUTH_TYPES_WITH_REDIRECT_URIS.contains(request.getType())) {
            validateRedirectUris(request.getRedirectUrls(), request.getType());
        }
    }

    private void validateRedirectUris(List<String> newRedirectUris, List<String> oldRedirectUris, AuthClientType type) {
        if (CollectionUtils.isEmpty(newRedirectUris)) {
            return;
        }
        // Get all new or changed redirect uris for validation
        List<String> addedUris = newRedirectUris.stream()
                .filter(uri -> oldRedirectUris == null || oldRedirectUris.stream().noneMatch(uri::equalsIgnoreCase))
                .toList();
        validateRedirectUris(addedUris, type);
    }

    private void validateRedirectUris(List<String> redirectUris, AuthClientType type) {
        if (CollectionUtils.isEmpty(redirectUris)) {
            return;
        }
        for (String redirectUri : redirectUris) {
            redirectUriValidator.validate(redirectUri, type);
        }
    }
}
