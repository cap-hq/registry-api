// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomOAuth2AuthorizationCodeRequestAuthenticationProviderTests {

    private CustomOAuth2AuthorizationCodeRequestAuthenticationProvider provider;

    @Mock
    private CustomJdbcRegisteredClientRepository repository;

    @Mock
    private OAuth2AuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider;

    @Mock
    OAuth2AuthorizationCodeRequestAuthenticationToken authentication;

    private static final String CLIENT_ID = "clientId";

    @BeforeEach
    void setUp() {
        provider = new CustomOAuth2AuthorizationCodeRequestAuthenticationProvider(repository, authorizationCodeRequestAuthenticationProvider);
    }

    @Test
    public void authenticate() {
        // given
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        List<RegisteredClient> registeredClients = List.of(
                RegisteredClient.withId("123").clientId(CLIENT_ID).clientName(CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000")
                        .build(),
                RegisteredClient.withId("123").clientId(CLIENT_ID + "-v2").clientName(CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000")
                        .build()
        );
        when(repository.findAllByClientIdOrName(CLIENT_ID)).thenReturn(registeredClients);
        when(authentication.getAuthorizationCode()).thenReturn(null);
        when(authentication.getAuthorizationUri()).thenReturn("/authorization");
        when(authentication.getPrincipal()).thenReturn(mock(Authentication.class));
        when(authentication.getRedirectUri()).thenReturn("http://localhost:3000");
        when(authentication.getState()).thenReturn("state");
        when(authentication.getScopes()).thenReturn(Collections.emptySet());
        when(authentication.getAdditionalParameters()).thenReturn(Collections.emptyMap());
        when(authorizationCodeRequestAuthenticationProvider.authenticate(any())).thenReturn(authentication);

        // when
        Authentication result = provider.authenticate(authentication);

        // then
        assertThat(result).isNotNull();
        verifyNoMoreInteractions(authorizationCodeRequestAuthenticationProvider);
    }

    @Test
    public void authenticate_noRegisteredClient() {
        // given
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        when(repository.findAllByClientIdOrName(CLIENT_ID)).thenReturn(Collections.emptyList());

        // when
        Authentication result = provider.authenticate(authentication);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(authorizationCodeRequestAuthenticationProvider);
    }

    @Test
    public void supports() {
        assertThat(provider.supports(OAuth2AuthorizationCodeRequestAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(OAuth2ClientAuthenticationToken.class)).isFalse();
    }

    @Test
    public void findRegisteredClient_notfound() {
        // given
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        when(repository.findAllByClientIdOrName(CLIENT_ID)).thenReturn(Collections.emptyList());

        // when
        List<RegisteredClient> result = provider.findRegisteredClients(authentication);

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    public void findRegisteredClient_exactMatch() {
        // given
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        List<RegisteredClient> registeredClients = List.of(
                RegisteredClient.withId("123").clientId(CLIENT_ID).clientName(CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000")
                        .build(),
                RegisteredClient.withId("123").clientId(CLIENT_ID + "-v2").clientName(CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000")
                        .build()
        );
        when(repository.findAllByClientIdOrName(CLIENT_ID)).thenReturn(registeredClients);

        // when
        List<RegisteredClient> result = provider.findRegisteredClients(authentication);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        RegisteredClient registeredClient = result.get(0);
        assertThat(registeredClient.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(registeredClient.getClientName()).isEqualTo(CLIENT_ID);
    }

    @Test
    public void findRegisteredClient_noExactMatch_ReturnFirst() {
        // given
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        List<RegisteredClient> registeredClients = List.of(
                RegisteredClient.withId("123").clientId(CLIENT_ID + "-v2").clientName(CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000")
                        .build(),
                RegisteredClient.withId("123").clientId(CLIENT_ID + "-v3").clientName(CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000")
                        .build()
        );
        when(repository.findAllByClientIdOrName(CLIENT_ID)).thenReturn(registeredClients);

        // when
        List<RegisteredClient> result = provider.findRegisteredClients(authentication);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        RegisteredClient registeredClient = result.get(0);
        assertThat(registeredClient.getClientId()).isEqualTo(CLIENT_ID + "-v2");
        assertThat(registeredClient.getClientName()).isEqualTo(CLIENT_ID);
    }

    @Test
    public void buildAuthenticationToken_noCode() {
        // given
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClient.getClientId()).thenReturn(CLIENT_ID);
        when(authentication.getAuthorizationCode()).thenReturn(null);
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        when(authentication.getAuthorizationUri()).thenReturn("/authorization");
        when(authentication.getPrincipal()).thenReturn(mock(Authentication.class));
        when(authentication.getRedirectUri()).thenReturn("http://localhost:3000");
        when(authentication.getState()).thenReturn("state");
        when(authentication.getScopes()).thenReturn(Collections.emptySet());
        when(authentication.getAdditionalParameters()).thenReturn(Collections.emptyMap());
        when(authentication.getDetails()).thenReturn(null);

        // when
        OAuth2AuthorizationCodeRequestAuthenticationToken result = CustomOAuth2AuthorizationCodeRequestAuthenticationProvider
                .buildAuthenticationToken(authentication, List.of(registeredClient));

        // then
        assertThat(result).isNotNull();
        verifyNoMoreInteractions(authentication);
    }

    @Test
    public void buildAuthenticationToken_withCode() {
        // given
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClient.getClientId()).thenReturn(CLIENT_ID);
        when(authentication.getClientId()).thenReturn(CLIENT_ID);
        when(authentication.getAuthorizationCode()).thenReturn(mock(OAuth2AuthorizationCode.class));
        when(authentication.getAuthorizationUri()).thenReturn("/authorization");
        when(authentication.getPrincipal()).thenReturn(mock(Authentication.class));
        when(authentication.getRedirectUri()).thenReturn("http://localhost:3000");
        when(authentication.getState()).thenReturn("state");
        when(authentication.getScopes()).thenReturn(Collections.emptySet());
        when(authentication.getDetails()).thenReturn(null);

        // when
        OAuth2AuthorizationCodeRequestAuthenticationToken result = CustomOAuth2AuthorizationCodeRequestAuthenticationProvider
                .buildAuthenticationToken(authentication, List.of(registeredClient));

        // then
        assertThat(result).isNotNull();
        verifyNoMoreInteractions(authentication);
    }
}
