// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService;
import com.cvshealth.cap.registry.auth.oauth.SessionExpiryOAuth2RefreshTokenAuthenticationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthorizationServerConfigTests {

    private final CustomJdbcOauth2AuthorizationService authorizationService = mock(CustomJdbcOauth2AuthorizationService.class);
    private final RefreshTokenConfig refreshTokenConfig = new RefreshTokenConfig();

    @Test
    void wrapRefreshTokenProvider_replacesBuiltInProviderInPlace() {
        AuthenticationProvider first = mock(AuthenticationProvider.class);
        AuthenticationProvider last = mock(AuthenticationProvider.class);
        AuthenticationProvider refreshProvider = new OAuth2RefreshTokenAuthenticationProvider(
                mock(OAuth2AuthorizationService.class), mock(OAuth2TokenGenerator.class));
        List<AuthenticationProvider> providers = new ArrayList<>(List.of(first, refreshProvider, last));

        AuthorizationServerConfig.wrapRefreshTokenProvider(providers, authorizationService, refreshTokenConfig);

        assertThat(providers).hasSize(3);
        assertThat(providers.get(0)).isSameAs(first);
        assertThat(providers.get(1)).isInstanceOf(SessionExpiryOAuth2RefreshTokenAuthenticationProvider.class);
        assertThat(providers.get(2)).isSameAs(last);
    }

    @Test
    void wrapRefreshTokenProvider_throws_whenBuiltInProviderMissing() {
        List<AuthenticationProvider> providers = new ArrayList<>(List.of(mock(AuthenticationProvider.class)));

        assertThatThrownBy(() -> AuthorizationServerConfig.wrapRefreshTokenProvider(
                providers, authorizationService, refreshTokenConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth2RefreshTokenAuthenticationProvider");
    }

    @Test
    void wrapRefreshTokenProvider_throws_whenProviderListEmpty() {
        assertThatThrownBy(() -> AuthorizationServerConfig.wrapRefreshTokenProvider(
                new ArrayList<>(), authorizationService, refreshTokenConfig))
                .isInstanceOf(IllegalStateException.class);
    }
}
