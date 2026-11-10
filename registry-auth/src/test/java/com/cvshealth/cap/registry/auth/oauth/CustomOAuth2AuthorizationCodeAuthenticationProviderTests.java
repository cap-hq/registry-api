// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.auth.oauth.CustomOAuth2AuthorizationCodeAuthenticationProvider.AUTHORIZATION_CODE_TOKEN_TYPE;
import static com.cvshealth.cap.registry.auth.oauth.CustomOAuth2AuthorizationCodeRequestAuthenticationProvider.REGISTERED_CLIENT_IDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomOAuth2AuthorizationCodeAuthenticationProviderTests {

    @InjectMocks
    private CustomOAuth2AuthorizationCodeAuthenticationProvider provider;

    @Mock
    private OAuth2AuthorizationService oAuth2AuthorizationService;

    @Mock
    private OAuth2AuthorizationCodeAuthenticationToken authentication;

    @Mock
    private OAuth2ClientAuthenticationToken authToken;

    @Mock
    private RegisteredClient registeredClient;

    @Mock
    private OAuth2Authorization authorization;

    @Mock
    private OAuth2AuthorizationRequest authorizationRequest;

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_ID_V2 = "client_id-v2";

    @Test
    public void authenticate_requestClientId_matches_authRegisteredClientId() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authentication.getCode()).thenReturn("code");
        when(authToken.isAuthenticated()).thenReturn(true);
        when(authToken.getRegisteredClient()).thenReturn(registeredClient);
        when(oAuth2AuthorizationService.findByToken("code", AUTHORIZATION_CODE_TOKEN_TYPE)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(registeredClient.getClientId()).thenReturn(CLIENT_ID);
        when(authorizationRequest.getClientId()).thenReturn(CLIENT_ID);

        // when the authorization request client id matches the authentication registered client id
        Authentication result = provider.authenticate(authentication);

        // then no update is needed on the request client id
        assertThat(result).isNull();
    }

    @Test
    public void authenticate_requestClientId_versionMismatch_withAuthRegisteredClientId() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authentication.getCode()).thenReturn("code");
        when(authToken.isAuthenticated()).thenReturn(true);
        when(authToken.getRegisteredClient()).thenReturn(registeredClient);
        when(oAuth2AuthorizationService.findByToken("code", AUTHORIZATION_CODE_TOKEN_TYPE)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(registeredClient.getClientId()).thenReturn(CLIENT_ID_V2);
        when(authorizationRequest.getClientId()).thenReturn(CLIENT_ID);
        // Spring Security 7 made OAuth2AuthorizationRequest.Builder's authorizationGrantType a
        // final field fixed to AUTHORIZATION_CODE, so from() no longer reads getGrantType(); the
        // rebuilt request still carries AUTHORIZATION_CODE, which is what this stub asserted.
        when(authorizationRequest.getAuthorizationUri()).thenReturn("authorize");
        when(authorizationRequest.getRedirectUri()).thenReturn("http://localhost:3000");
        when(authorizationRequest.getScopes()).thenReturn(Collections.emptySet());
        when(authorizationRequest.getState()).thenReturn("state");
        when(authorizationRequest.getAdditionalParameters()).thenReturn(Map.of(
                REGISTERED_CLIENT_IDS, List.of(CLIENT_ID_V2)
        ));
        when(authorizationRequest.getAttributes()).thenReturn(Collections.emptyMap());
        when(authorization.getId()).thenReturn("123");
        when(authorization.getPrincipalName()).thenReturn(OAuth2ClientAuthenticationToken.class.getName());
        when(authorization.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(authorization.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(authorization.getAttributes()).thenReturn(Collections.emptyMap());
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCodeToken = mock(OAuth2Authorization.Token.class);
        when(authorization.getToken(OAuth2AuthorizationCode.class)).thenReturn(authorizationCodeToken);
        when(authorizationCodeToken.getToken()).thenReturn(mock(OAuth2AuthorizationCode.class));

        // when the authorization request client id does not match the authentication registered client id
        Authentication result = provider.authenticate(authentication);

        // then no update is needed on the request client id
        assertThat(result).isNull();
        verify(oAuth2AuthorizationService).save(any(OAuth2Authorization.class));
        verifyNoMoreInteractions(oAuth2AuthorizationService);
    }

    @Test
    public void authenticate_noClientPrincipal() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authToken.isAuthenticated()).thenReturn(false);

        // when the authorization request client id matches the authentication registered client id
        Authentication result = provider.authenticate(authentication);

        // then no update is needed on the request client id
        assertThat(result).isNull();
        verifyNoMoreInteractions(authentication);
        verifyNoMoreInteractions(authToken);
        verifyNoInteractions(oAuth2AuthorizationService);
    }

    @Test
    public void authenticate_noRegisteredClient() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authToken.isAuthenticated()).thenReturn(true);
        when(authToken.getRegisteredClient()).thenReturn(null);

        // when the authorization request client id matches the authentication registered client id
        Authentication result = provider.authenticate(authentication);

        // then no update is needed on the request client id
        assertThat(result).isNull();
        verifyNoMoreInteractions(authentication);
        verifyNoMoreInteractions(authToken);
        verifyNoInteractions(oAuth2AuthorizationService);
    }

    @Test
    public void authenticate_noAuthorizationToken() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authentication.getCode()).thenReturn("code");
        when(authToken.isAuthenticated()).thenReturn(true);
        when(authToken.getRegisteredClient()).thenReturn(registeredClient);
        when(oAuth2AuthorizationService.findByToken("code", AUTHORIZATION_CODE_TOKEN_TYPE)).thenReturn(null);

        // when the authorization request client id matches the authentication registered client id
        Authentication result = provider.authenticate(authentication);

        // then no update is needed on the request client id
        assertThat(result).isNull();
        verifyNoMoreInteractions(authentication);
        verifyNoMoreInteractions(authToken);
        verifyNoMoreInteractions(oAuth2AuthorizationService);
    }

    @Test
    public void authenticate_noAuthorizationRequest() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authentication.getCode()).thenReturn("code");
        when(authToken.isAuthenticated()).thenReturn(true);
        when(authToken.getRegisteredClient()).thenReturn(registeredClient);
        when(oAuth2AuthorizationService.findByToken("code", AUTHORIZATION_CODE_TOKEN_TYPE)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(null);

        // when the authorization request client id matches the authentication registered client id
        Authentication result = provider.authenticate(authentication);

        // then no update is needed on the request client id
        assertThat(result).isNull();
        verifyNoMoreInteractions(authentication);
        verifyNoMoreInteractions(authToken);
        verifyNoMoreInteractions(oAuth2AuthorizationService);
    }

    @Test
    public void supports() {
        assertThat(provider.supports(OAuth2AuthorizationCodeAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(OAuth2AuthorizationCodeRequestAuthenticationToken.class)).isFalse();
    }

    @Test
    public void getOAuth2ClientAuthenticationToken() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authToken.isAuthenticated()).thenReturn(true);

        // when
        OAuth2ClientAuthenticationToken result = provider.getOAuth2ClientAuthenticationToken(authentication);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isSameAs(authToken);
    }

    @Test
    public void getOAuth2ClientAuthenticationToken_notAuthenticated() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);
        when(authToken.isAuthenticated()).thenReturn(false);

        // when
        OAuth2ClientAuthenticationToken result = provider.getOAuth2ClientAuthenticationToken(authentication);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getOAuth2ClientAuthenticationToken_unexpectedTokenClass() {
        // given
        when(authentication.getPrincipal()).thenReturn(authToken);

        // when
        OAuth2ClientAuthenticationToken result = provider.getOAuth2ClientAuthenticationToken(authentication);

        // then
        assertThat(result).isNull();
    }
}
