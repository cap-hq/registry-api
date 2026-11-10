// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.config.RefreshTokenConfig;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService.SessionWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionExpiryOAuth2RefreshTokenAuthenticationProviderTests {

    private static final String REFRESH_TOKEN = "some-refresh-token";
    private static final long DEFAULT_SESSION_EXPIRE_MINUTES = 1440L;
    private static final long INSECURE_REDIRECT_SESSION_EXPIRE_MINUTES = 240L;

    @Mock
    private CustomJdbcOauth2AuthorizationService authorizationService;

    @Mock
    private OAuth2RefreshTokenAuthenticationToken refreshTokenAuthentication;

    @Mock
    private AuthenticationProvider delegate;

    @Mock
    private OAuth2Authorization authorization;

    @Mock
    private Authentication delegateResult;

    private final RefreshTokenConfig refreshTokenConfig = new RefreshTokenConfig();

    private SessionExpiryOAuth2RefreshTokenAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        refreshTokenConfig.setDefaultSessionExpireMinutes(DEFAULT_SESSION_EXPIRE_MINUTES);
        provider = new SessionExpiryOAuth2RefreshTokenAuthenticationProvider(
                authorizationService, refreshTokenConfig, delegate);
        lenient().when(refreshTokenAuthentication.getRefreshToken()).thenReturn(REFRESH_TOKEN);
        lenient().when(delegate.authenticate(refreshTokenAuthentication)).thenReturn(delegateResult);
    }

    @AfterEach
    void singleSessionLookup() {
        // The refresh path must read the session with exactly one small query, never the full row.
        // Only the pre-migration fallback may load the full row; those tests verify that lookup themselves.
        if (mockingDetails(authorizationService).getInvocations().isEmpty()) {
            return;
        }
        verify(authorizationService).findSessionWindowByRefreshToken(REFRESH_TOKEN);
        verifyNoMoreInteractions(authorizationService);
    }

    @Test
    void supports_refreshTokenAuthenticationToken() {
        assertThat(provider.supports(OAuth2RefreshTokenAuthenticationToken.class)).isTrue();
    }

    @Test
    void supports_returnsFalse_forOtherTypes() {
        assertThat(provider.supports(Object.class)).isFalse();
    }

    @Test
    void authenticate_authorizationNotFound_throwsInvalidGrant() {
        when(authorizationService.findSessionWindowByRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.empty());

        assertInvalidGrant();
        verifyNoInteractions(delegate);
    }

    @Test
    void authenticate_sessionNotExpired_delegatesAndReturnsResult() {
        Instant futureExpiry = Instant.now().plusSeconds(3600);
        givenSessionWindow(futureExpiry, null);

        assertThat(provider.authenticate(refreshTokenAuthentication)).isEqualTo(delegateResult);
        verify(delegate).authenticate(refreshTokenAuthentication);
    }

    @Test
    void authenticate_sessionExpired_throwsInvalidGrant() {
        Instant pastExpiry = Instant.now().minusSeconds(60);
        givenSessionWindow(pastExpiry, null);

        assertInvalidGrant();
        verifyNoInteractions(delegate);
    }

    @Test
    void authenticate_migrationFallback_sessionNotExpired_delegatesAndReturnsResult() {
        Instant recentFirstIssuedAt = Instant.now().minusSeconds(60);
        givenSessionWindow(null, recentFirstIssuedAt);
        givenFallbackSessionExpireMinutes(DEFAULT_SESSION_EXPIRE_MINUTES);

        assertThat(provider.authenticate(refreshTokenAuthentication)).isEqualTo(delegateResult);
        verify(delegate).authenticate(refreshTokenAuthentication);
        verifyFallbackLookup();
    }

    @Test
    void authenticate_migrationFallback_sessionExpired_throwsInvalidGrant() {
        Instant oldFirstIssuedAt = Instant.now().minusSeconds(DEFAULT_SESSION_EXPIRE_MINUTES * 60 + 1);
        givenSessionWindow(null, oldFirstIssuedAt);
        givenFallbackSessionExpireMinutes(DEFAULT_SESSION_EXPIRE_MINUTES);

        assertInvalidGrant();
        verifyNoInteractions(delegate);
        verifyFallbackLookup();
    }

    @Test
    void authenticate_migrationFallback_insecureRedirect_pastInsecureWindow_throwsInvalidGrant() {
        // 5 hours in: inside the 24-hour default window but past the 4-hour insecure-redirect window
        Instant firstIssuedAt = Instant.now().minusSeconds(5 * 60 * 60);
        givenSessionWindow(null, firstIssuedAt);
        givenFallbackSessionExpireMinutes(INSECURE_REDIRECT_SESSION_EXPIRE_MINUTES);

        assertInvalidGrant();
        verifyNoInteractions(delegate);
        verifyFallbackLookup();
    }

    @Test
    void authenticate_migrationFallback_insecureRedirect_withinInsecureWindow_delegatesAndReturnsResult() {
        Instant firstIssuedAt = Instant.now().minusSeconds(60 * 60);
        givenSessionWindow(null, firstIssuedAt);
        givenFallbackSessionExpireMinutes(INSECURE_REDIRECT_SESSION_EXPIRE_MINUTES);

        assertThat(provider.authenticate(refreshTokenAuthentication)).isEqualTo(delegateResult);
        verify(delegate).authenticate(refreshTokenAuthentication);
        verifyFallbackLookup();
    }

    @Test
    void authenticate_migrationFallback_authorizationNotFound_usesDefaultWindow() {
        // Row vanished between the session-window query and the full-row lookup
        Instant oldFirstIssuedAt = Instant.now().minusSeconds(DEFAULT_SESSION_EXPIRE_MINUTES * 60 + 1);
        givenSessionWindow(null, oldFirstIssuedAt);
        when(authorizationService.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        assertInvalidGrant();
        verifyNoInteractions(delegate);
        verify(authorizationService).findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN);
    }

    @Test
    void authenticate_migrationFallback_noFirstIssuedAt_delegatesAndReturnsResult() {
        givenSessionWindow(null, null);

        assertThat(provider.authenticate(refreshTokenAuthentication)).isEqualTo(delegateResult);
        verify(delegate).authenticate(refreshTokenAuthentication);
    }

    @Test
    void authenticate_migrationFallback_nonAuthorizationCodeGrant_skipsSessionWindowAndDelegates() {
        // Past the default session window, but session windows only apply to authorization_code grants
        Instant oldFirstIssuedAt = Instant.now().minusSeconds(DEFAULT_SESSION_EXPIRE_MINUTES * 60 + 1);
        givenSessionWindow(null, oldFirstIssuedAt, AuthorizationGrantType.DEVICE_CODE);

        assertThat(provider.authenticate(refreshTokenAuthentication)).isEqualTo(delegateResult);
        verify(delegate).authenticate(refreshTokenAuthentication);
    }

    private void givenSessionWindow(Instant sessionExpiresAt, Instant firstAccessTokenIssuedAt) {
        givenSessionWindow(sessionExpiresAt, firstAccessTokenIssuedAt, AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    private void givenSessionWindow(Instant sessionExpiresAt, Instant firstAccessTokenIssuedAt, AuthorizationGrantType grantType) {
        when(authorizationService.findSessionWindowByRefreshToken(REFRESH_TOKEN))
                .thenReturn(Optional.of(new SessionWindow(sessionExpiresAt, firstAccessTokenIssuedAt, grantType.getValue())));
    }

    private void givenFallbackSessionExpireMinutes(long sessionExpireMinutes) {
        when(authorizationService.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(authorization);
        when(authorizationService.resolveSessionExpireMinutes(authorization)).thenReturn(sessionExpireMinutes);
    }

    private void verifyFallbackLookup() {
        verify(authorizationService).findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN);
        verify(authorizationService).resolveSessionExpireMinutes(authorization);
    }

    private void assertInvalidGrant() {
        assertThatThrownBy(() -> provider.authenticate(refreshTokenAuthentication))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
    }
}
