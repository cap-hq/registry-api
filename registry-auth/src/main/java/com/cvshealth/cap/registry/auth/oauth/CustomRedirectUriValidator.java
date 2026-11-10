// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.function.Consumer;

@Slf4j
public class CustomRedirectUriValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final String WILDCARD = "*";
    private static final String REGEX_BEGIN_LITERAL = "\\Q";
    private static final String REGEX_END_LITERAL = "\\E";
    private static final String REGEX_WILDCARD = REGEX_END_LITERAL + ".*" + REGEX_BEGIN_LITERAL;
    private static final String REGEX_EMPTY_LITERAL = REGEX_END_LITERAL + REGEX_BEGIN_LITERAL;

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext authenticationContext) {
        OAuth2AuthorizationCodeRequestAuthenticationToken authorizationCodeRequestAuthentication =
                authenticationContext.getAuthentication();
        RegisteredClient registeredClient = authenticationContext.getRegisteredClient();
        String requestedRedirectUri = authorizationCodeRequestAuthentication.getRedirectUri();

        // Use either exact string matching or wildcard matching when comparing client redirect URIs against pre-registered URIs
        boolean redirectMatch = registeredClient.getRedirectUris().stream().anyMatch(registeredClientRedirectUri -> {
            if (registeredClientRedirectUri.contains(WILDCARD)) {
                // wildcard match
                return wildcardMatch(requestedRedirectUri, registeredClientRedirectUri);
            } else {
                // exact match
                return registeredClientRedirectUri.equalsIgnoreCase(requestedRedirectUri);
            }
        });

        if (!redirectMatch) {
            log.warn(String.format("Invalid redirect url for %s - %s", registeredClient.getClientId(), requestedRedirectUri));
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "invalid redirect url", requestedRedirectUri);
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null);
        }
    }

    private boolean wildcardMatch(String requestedUrl, String wildcardUrl) {
        if (!StringUtils.isEmpty(requestedUrl) && !StringUtils.isEmpty(wildcardUrl)) {
            // replace wildcard(*) url with regex expression
            String regexUrl = (REGEX_BEGIN_LITERAL + wildcardUrl.replace(WILDCARD, REGEX_WILDCARD) + REGEX_END_LITERAL)
                    .replace(REGEX_EMPTY_LITERAL, "");
            return requestedUrl.matches(regexUrl);
        }
        return false;
    }
}