// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.config.RefreshTokenConfig;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService.SessionWindow;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.util.Assert;

import java.time.Instant;

/**
 * Wraps the built-in {@code OAuth2RefreshTokenAuthenticationProvider} to enforce a maximum
 * session window for {@code authorization_code} grants.
 *
 * <p>Checks that the original authorization session has not exceeded its session window
 * ({@code defaultSessionExpireMinutes}, or {@code insecureRedirectSessionExpireMinutes} for insecure {@code http://} redirects)
 * before delegating to the standard refresh-token grant. Throws {@code invalid_grant} when the session
 * has expired, or when no authorization holds the refresh token (e.g. the session was deleted).
 *
 * <p>This class WRAPS (not prepends to) the delegate so that when the session is expired the
 * exception propagates through {@code ProviderManager} without being swallowed by a subsequent
 * provider.
 *
 * <p>This check is permanent defence-in-depth, not a temporary measure. {@code save()} already caps
 * {@code refresh_token_expires_at} at {@code session_expires_at}, so the built-in provider would normally
 * reject an expired session on its own; this wrapper still enforces the session window when that cap is
 * missing or bypassed (e.g. rows written before the cap existed, or a manual DB edit).
 */
public final class SessionExpiryOAuth2RefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final CustomJdbcOauth2AuthorizationService authorizationService;
    private final RefreshTokenConfig refreshTokenConfig;
    private final AuthenticationProvider delegate;

    public SessionExpiryOAuth2RefreshTokenAuthenticationProvider(
            CustomJdbcOauth2AuthorizationService authorizationService,
            RefreshTokenConfig refreshTokenConfig,
            AuthenticationProvider delegate) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(refreshTokenConfig, "refreshTokenConfig cannot be null");
        Assert.notNull(delegate, "delegate cannot be null");
        this.authorizationService = authorizationService;
        this.refreshTokenConfig = refreshTokenConfig;
        this.delegate = delegate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refreshTokenAuthentication =
                (OAuth2RefreshTokenAuthenticationToken) authentication;

        SessionWindow sessionWindow = authorizationService
                .findSessionWindowByRefreshToken(refreshTokenAuthentication.getRefreshToken())
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT)));

        Instant sessionExpiresAt = sessionWindow.getSessionExpiresAt();
        if (sessionExpiresAt == null) {
            // Pre-migration fallback: session_expires_at not yet set for this row.
            // session_expires_at is only ever set for authorization_code rows, so other grants are not session-limited.
            Instant firstIssuedAt = sessionWindow.getFirstAccessTokenIssuedAt();
            if (firstIssuedAt != null
                    && AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(sessionWindow.getAuthorizationGrantType())) {
                // Only this fallback needs the redirect URI, so the full row is loaded here rather than on every refresh.
                OAuth2Authorization authorization = authorizationService.findByToken(
                        refreshTokenAuthentication.getRefreshToken(), OAuth2TokenType.REFRESH_TOKEN);
                long sessionExpireMinutes = authorization != null
                        ? authorizationService.resolveSessionExpireMinutes(authorization)
                        : refreshTokenConfig.getDefaultSessionExpireMinutes();
                sessionExpiresAt = firstIssuedAt.plusSeconds(sessionExpireMinutes * 60L);
            }
        }

        if (sessionExpiresAt != null && Instant.now().isAfter(sessionExpiresAt)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "The refresh token session has expired", null));
        }

        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
