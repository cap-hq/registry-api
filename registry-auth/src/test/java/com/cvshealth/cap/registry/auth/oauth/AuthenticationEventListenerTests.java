// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import com.cvshealth.cap.registry.auth.model.ClientAuthenticatedEvent;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationEventListenerTests {

    @InjectMocks
    private AuthenticationEventListener eventListener;

    @Mock
    private CustomJdbcRegisteredClientRepository registeredClientRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private JwtDecoder jwtDecode;

    @Mock
    private CustomJdbcOauth2AuthorizationService authorizationService;

    @Mock
    private AuthenticationSuccessEvent successEvent;

    @Mock
    private AuthorizationCodeRequestAuthenticationFailureEvent authCodeRequestFailureEvent;

    @Mock
    private ClientSecretAuthenticationFailureEvent clientAuthFailureEvent;

    @Mock
    private OAuth2AuthorizationCodeRequestAuthenticationToken requestToken;

    @Mock
    private OAuth2ClientAuthenticationToken clientAuthToken;

    @Mock
    private OAuth2RefreshTokenAuthenticationToken refreshAuthToken;

    @BeforeEach
    void setUp() {
        eventListener.logAuthenticationSuccess = true;
        eventListener.eventClaimsToLog = List.of("scope", "iss", "sid");
    }

    @Test
    public void onSuccess_withClientAuthToken_withoutLogging() {
        // given
        String clientId = "clientId";
        eventListener.logAuthenticationSuccess = false;
        when(successEvent.getSource()).thenReturn(clientAuthToken);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(clientAuthToken.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn(clientId);

        // when
        eventListener.onSuccess(successEvent);

        // then
        verify(registeredClientRepository, times(1)).updateLastAccessTokenIssuedAt(clientId);
        verifyNoMoreInteractions(registeredClientRepository);
        verifyNoMoreInteractions(successEvent);
        verifyNoMoreInteractions(clientAuthToken);
    }

    @Test
    public void onSuccess_withClientAuthToken_withLogging() {
        // given
        String clientId = "clientId";
        when(successEvent.getSource()).thenReturn(clientAuthToken);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(clientAuthToken.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn(clientId);
        when(clientAuthToken.getCredentials()).thenReturn(null);

        // when
        eventListener.onSuccess(successEvent);

        // then
        verify(registeredClientRepository, times(1)).updateLastAccessTokenIssuedAt(clientId);
        verifyNoMoreInteractions(registeredClientRepository);
        verifyNoMoreInteractions(successEvent);
        verifyNoMoreInteractions(clientAuthToken);
    }

    @Test
    public void onSuccess_withLoginToken_withLogging() {
        // given
        OAuth2LoginAuthenticationToken token = mock(OAuth2LoginAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(token);
        ClientRegistration clientRegistration = mock(ClientRegistration.class);
        when(token.getClientRegistration()).thenReturn(clientRegistration);
        when(clientRegistration.getClientId()).thenReturn("clientId");
        DefaultOidcUser user = mock(DefaultOidcUser.class);
        when(token.getPrincipal()).thenReturn(user);
        when(user.getName()).thenReturn("name");

        // when
        eventListener.onSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
        verifyNoInteractions(registeredClientRepository);
    }

    @Test
    public void onSuccess_withAnonymousToken() {
        // given
        when(successEvent.getSource()).thenReturn(mock(AnonymousAuthenticationToken.class));

        // when
        eventListener.onSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
    }

    @Test
    public void logAuthenticationSuccess_withLoginToken() {
        // given
        OAuth2LoginAuthenticationToken token = mock(OAuth2LoginAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(token);
        ClientRegistration clientRegistration = mock(ClientRegistration.class);
        when(token.getClientRegistration()).thenReturn(clientRegistration);
        when(clientRegistration.getClientId()).thenReturn("clientId");
        DefaultOidcUser user = mock(DefaultOidcUser.class);
        when(token.getPrincipal()).thenReturn(user);
        when(user.getName()).thenReturn("name");

        // when
        eventListener.logAuthenticationSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
    }

    @Test
    public void logAuthenticationSuccess_withAuthorizationCodeRequestToken_AndAnonymousToken() {
        // given
        when(successEvent.getSource()).thenReturn(requestToken);
        AnonymousAuthenticationToken anonymousToken = mock(AnonymousAuthenticationToken.class);
        when(requestToken.getPrincipal()).thenReturn(anonymousToken);

        // when
        eventListener.logAuthenticationSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
        verifyNoMoreInteractions(requestToken);
    }

    @Test
    public void logAuthenticationSuccess_withAuthorizationCodeRequestToken_AndAuthToken() {
        // given
        when(successEvent.getSource()).thenReturn(requestToken);
        OAuth2AuthenticationToken authToken = mock(OAuth2AuthenticationToken.class);
        when(requestToken.getPrincipal()).thenReturn(authToken);
        when(authToken.getName()).thenReturn("name");
        when(requestToken.getClientId()).thenReturn("clientId");
        when(requestToken.getRedirectUri()).thenReturn("http://localhost:3000");

        // when
        eventListener.logAuthenticationSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
        verifyNoMoreInteractions(requestToken);
    }

    @Test
    public void logAuthenticationSuccess_withClientAuthenticationToken() {
        // given
        when(successEvent.getSource()).thenReturn(clientAuthToken);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(clientAuthToken.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(clientAuthToken.getCredentials()).thenReturn(null);

        // when
        eventListener.logAuthenticationSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
        verifyNoMoreInteractions(clientAuthToken);
    }

    @Test
    public void logAuthenticationSuccess_withAccessAuthenticationToken() {
        // given
        OAuth2AccessTokenAuthenticationToken accessToken = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessToken);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessToken.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        OAuth2AccessToken oAuth2AccessToken = mock(OAuth2AccessToken.class);
        when(accessToken.getAccessToken()).thenReturn(oAuth2AccessToken);
        when(oAuth2AccessToken.getTokenValue()).thenReturn("token-value");

        // when
        eventListener.logAuthenticationSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
    }

    @Test
    public void logAuthenticationSuccess_withRefreshToken() {
        // given
        PublicClientRefreshTokenAuthenticationToken refreshToken = mock(PublicClientRefreshTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(refreshToken);
        when(refreshToken.getRegisteredClient()).thenReturn(null);

        // when
        eventListener.logAuthenticationSuccess(successEvent);

        // then
        verifyNoMoreInteractions(successEvent);
    }

    @Test
    public void onFailure_withCodeRequestFailureEvent() {
        // given
        OAuth2AuthorizationCodeRequestAuthenticationException exception = mock(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        when(authCodeRequestFailureEvent.getException()).thenReturn(exception);
        OAuth2Error error = mock(OAuth2Error.class);
        when(exception.getError()).thenReturn(error);
        when(error.getDescription()).thenReturn("error description");
        when(error.getErrorCode()).thenReturn("error code");
        when(authCodeRequestFailureEvent.getSource()).thenReturn(requestToken);
        when(requestToken.getClientId()).thenReturn("clientId");
        when(requestToken.getPrincipal()).thenReturn(null);
        when(requestToken.getRedirectUri()).thenReturn("http://localhost:3000");

        // when
        eventListener.onFailure(authCodeRequestFailureEvent);

        // then
        verifyNoMoreInteractions(authCodeRequestFailureEvent);
        verifyNoMoreInteractions(requestToken);
    }

    @Test
    public void onFailure_withClientAuthFailureEvent() {
        // given
        OAuth2AuthenticationException exception = mock(OAuth2AuthenticationException.class);
        when(clientAuthFailureEvent.getException()).thenReturn(exception);
        OAuth2Error error = mock(OAuth2Error.class);
        when(exception.getError()).thenReturn(error);
        when(error.getDescription()).thenReturn("error description");
        when(error.getErrorCode()).thenReturn("error code");
        when(clientAuthFailureEvent.getSource()).thenReturn(clientAuthToken);
        when(clientAuthToken.getRegisteredClient()).thenReturn(null);
        when(clientAuthToken.getPrincipal()).thenReturn("clientId");
        when(clientAuthToken.getCredentials()).thenReturn(null);
        when(clientAuthToken.getAdditionalParameters()).thenReturn(Map.of("grant_type", "authorization_code"));

        // when
        eventListener.onFailure(clientAuthFailureEvent);

        // then
        verifyNoMoreInteractions(clientAuthFailureEvent);
        verifyNoMoreInteractions(clientAuthToken);
    }

    @Test
    public void onFailure_withRefreshAuthTokenFailureEvent() {
        // given
        OAuth2AuthenticationException exception = mock(OAuth2AuthenticationException.class);
        when(clientAuthFailureEvent.getException()).thenReturn(exception);
        OAuth2Error error = mock(OAuth2Error.class);
        when(exception.getError()).thenReturn(error);
        when(error.getDescription()).thenReturn("");
        when(error.getErrorCode()).thenReturn(OAuth2ErrorCodes.INVALID_GRANT);
        when(clientAuthFailureEvent.getSource()).thenReturn(refreshAuthToken);
        when(refreshAuthToken.getRefreshToken()).thenReturn("1234-7890");
        when(refreshAuthToken.getPrincipal()).thenReturn(clientAuthToken);
        when(refreshAuthToken.getGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(clientAuthToken.getRegisteredClient()).thenReturn(null);
        when(clientAuthToken.getPrincipal()).thenReturn("clientId");
        when(clientAuthToken.getCredentials()).thenReturn(null);

        // when
        eventListener.onFailure(clientAuthFailureEvent);

        // then
        verifyNoMoreInteractions(clientAuthFailureEvent);
        verifyNoMoreInteractions(clientAuthToken);
        verifyNoMoreInteractions(refreshAuthToken);
    }

    @Test
    public void onFailure_withClientAuthFailureEvent_withJwt() {
        // given
        OAuth2AuthenticationException exception = mock(OAuth2AuthenticationException.class);
        when(clientAuthFailureEvent.getException()).thenReturn(exception);
        OAuth2Error error = mock(OAuth2Error.class);
        when(exception.getError()).thenReturn(error);
        when(error.getDescription()).thenReturn("error description");
        when(error.getErrorCode()).thenReturn("error code");
        when(clientAuthFailureEvent.getSource()).thenReturn(clientAuthToken);
        when(clientAuthToken.getRegisteredClient()).thenReturn(null);
        when(clientAuthToken.getPrincipal()).thenReturn("clientId");
        when(clientAuthToken.getAdditionalParameters()).thenReturn(Map.of("grant_type", "authorization_code"));
        Jwt jwt = mock(Jwt.class);
        when(clientAuthToken.getCredentials()).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("header.payload.signature");

        // when
        eventListener.onFailure(clientAuthFailureEvent);

        // then
        verifyNoMoreInteractions(clientAuthFailureEvent);
        verifyNoMoreInteractions(clientAuthToken);
    }

    @Test
    public void buildLogMessage_withJwt() {
        // given
        com.cvshealth.cap.registry.auth.model.AuthenticationEvent event = com.cvshealth.cap.registry.auth.model.AuthenticationEvent.builder()
                .result("failure")
                .sourceClass("TestSource")
                .clientId("clientId")
                .jwt("header.payload.signature")
                .build();

        // when
        String message = eventListener.buildLogMessage(event);

        // then
        assertThat(message).contains("jwt[header.payload.signature]");
    }

    @Test
    public void buildLogMessage_withoutJwt() {
        // given
        com.cvshealth.cap.registry.auth.model.AuthenticationEvent event = com.cvshealth.cap.registry.auth.model.AuthenticationEvent.builder()
                .result("failure")
                .sourceClass("TestSource")
                .clientId("clientId")
                .build();

        // when
        String message = eventListener.buildLogMessage(event);

        // then
        assertThat(message).doesNotContain("jwt[");
    }

    @Test
    public void onSuccess_withAccessToken_withNoNewRefreshToken() {
        // given — auth_code client, no new refresh token issued (e.g. offline_access not in scope)
        OAuth2AccessTokenAuthenticationToken accessTokenAuth = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessTokenAuth);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessTokenAuth.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(registeredClient.getClientName()).thenReturn("clientName");
        when(registeredClient.getAuthorizationGrantTypes()).thenReturn(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE));
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn("token-value");
        Jwt jwt = mock(Jwt.class);
        when(jwtDecode.decode("token-value")).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("sub", "user1"));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findByToken("token-value", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        when(authorization.getId()).thenReturn("auth-id");
        OAuth2AuthorizationRequest authorizationRequest = mock(OAuth2AuthorizationRequest.class);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getRedirectUri()).thenReturn("http://localhost:3000");

        // when
        eventListener.onSuccess(successEvent);

        // then
        ArgumentCaptor<ClientAuthenticatedEvent> captor = ArgumentCaptor.forClass(ClientAuthenticatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClientAuthenticatedEvent event = captor.getValue();
        assertThat(event.getGrantType()).isEqualTo("authorization_code");
        assertThat(event.getEventAttributes()).contains("\"redirect_uri\":\"http://localhost:3000\"");
    }

    @Test
    public void onSuccess_withAccessToken_withNewRefreshToken() {
        // given — auth_code client, new refresh token issued (either first exchange or rotation)
        OAuth2AccessTokenAuthenticationToken accessTokenAuth = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessTokenAuth);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessTokenAuth.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(registeredClient.getClientName()).thenReturn("clientName");
        when(registeredClient.getAuthorizationGrantTypes()).thenReturn(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE));
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn("token-value");
        Jwt jwt = mock(Jwt.class);
        when(jwtDecode.decode("token-value")).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("sub", "user1"));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findByToken("token-value", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        when(authorization.getId()).thenReturn("auth-id");
        OAuth2AuthorizationRequest authorizationRequest = mock(OAuth2AuthorizationRequest.class);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getRedirectUri()).thenReturn("http://localhost:3000");

        // when
        eventListener.onSuccess(successEvent);

        // then
        ArgumentCaptor<ClientAuthenticatedEvent> captor = ArgumentCaptor.forClass(ClientAuthenticatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClientAuthenticatedEvent event = captor.getValue();
        assertThat(event.getGrantType()).isEqualTo("authorization_code");
        assertThat(event.getEventAttributes()).contains("\"redirect_uri\":\"http://localhost:3000\"");
    }

    @Test
    public void onSuccess_withAccessToken_initialIssuance_setsRefreshedTokenFalse_includesRedirectUri() {
        // given — first code exchange: access_token_issued_at == first_access_token_issued_at
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OAuth2AccessTokenAuthenticationToken accessTokenAuth = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessTokenAuth);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessTokenAuth.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(registeredClient.getClientName()).thenReturn("clientName");
        when(registeredClient.getAuthorizationGrantTypes()).thenReturn(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE));
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn("token-value");
        when(accessToken.getIssuedAt()).thenReturn(issuedAt);
        Jwt jwt = mock(Jwt.class);
        when(jwtDecode.decode("token-value")).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("sub", "user1"));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findByToken("token-value", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        when(authorization.getId()).thenReturn("auth-id");
        when(authorizationService.findFirstAccessTokenIssuedAt("auth-id")).thenReturn(issuedAt);
        OAuth2AuthorizationRequest authorizationRequest = mock(OAuth2AuthorizationRequest.class);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getRedirectUri()).thenReturn("http://localhost:3000");

        // when
        eventListener.onSuccess(successEvent);

        // then
        ArgumentCaptor<ClientAuthenticatedEvent> captor = ArgumentCaptor.forClass(ClientAuthenticatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo("authorization_code");
        assertThat(captor.getValue().getEventAttributes()).contains("\"redirect_uri\":\"http://localhost:3000\"");
    }

    @Test
    public void onSuccess_withAccessToken_initialIssuance_withSubMicrosecondPrecision_treatedAsFirstToken() {
        // given — first token: in-memory Instant has nanoseconds, DB-read value is truncated to microseconds
        Instant currentIssuedAt = Instant.parse("2026-08-24T12:00:00.123456789Z"); // simulates JVM nanosecond precision
        Instant firstIssuedAtFromDb = Instant.parse("2026-08-24T12:00:00.123457Z"); // simulates PostgreSQL TIMESTAMP precision
        OAuth2AccessTokenAuthenticationToken accessTokenAuth = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessTokenAuth);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessTokenAuth.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(registeredClient.getClientName()).thenReturn("clientName");
        when(registeredClient.getAuthorizationGrantTypes()).thenReturn(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE));
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn("token-value");
        when(accessToken.getIssuedAt()).thenReturn(currentIssuedAt);
        Jwt jwt = mock(Jwt.class);
        when(jwtDecode.decode("token-value")).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("sub", "user1"));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findByToken("token-value", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        when(authorization.getId()).thenReturn("auth-id");
        when(authorizationService.findFirstAccessTokenIssuedAt("auth-id")).thenReturn(firstIssuedAtFromDb);
        OAuth2AuthorizationRequest authorizationRequest = mock(OAuth2AuthorizationRequest.class);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getRedirectUri()).thenReturn("http://localhost:3000");

        // when
        eventListener.onSuccess(successEvent);

        // then — must be treated as first issuance, not a refresh
        ArgumentCaptor<ClientAuthenticatedEvent> captor = ArgumentCaptor.forClass(ClientAuthenticatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo("authorization_code");
        assertThat(captor.getValue().getEventAttributes()).contains("\"redirect_uri\":\"http://localhost:3000\"");
    }

    @Test
    public void onSuccess_withAccessToken_refreshGrantIssuance_setsRefreshedTokenTrue_excludesRedirectUri() {
        // given — refresh grant: current access_token_issued_at is after first_access_token_issued_at
        Instant firstIssuedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant currentIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OAuth2AccessTokenAuthenticationToken accessTokenAuth = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessTokenAuth);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessTokenAuth.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(registeredClient.getClientName()).thenReturn("clientName");
        when(registeredClient.getAuthorizationGrantTypes()).thenReturn(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE));
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn("token-value");
        when(accessToken.getIssuedAt()).thenReturn(currentIssuedAt);
        Jwt jwt = mock(Jwt.class);
        when(jwtDecode.decode("token-value")).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("sub", "user1"));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findByToken("token-value", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        when(authorization.getId()).thenReturn("auth-id");
        when(authorizationService.findFirstAccessTokenIssuedAt("auth-id")).thenReturn(firstIssuedAt);

        // when
        eventListener.onSuccess(successEvent);

        // then
        ArgumentCaptor<ClientAuthenticatedEvent> captor = ArgumentCaptor.forClass(ClientAuthenticatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo("refresh_token");
        assertThat(captor.getValue().getEventAttributes()).contains("\"first_access_token_issued_at\"");
        assertThat(captor.getValue().getEventAttributes()).doesNotContain("redirect_uri");
    }

    @Test
    public void onSuccess_withAccessToken_clientCredentialsGrant() {
        // given
        OAuth2AccessTokenAuthenticationToken accessTokenAuth = mock(OAuth2AccessTokenAuthenticationToken.class);
        when(successEvent.getSource()).thenReturn(accessTokenAuth);
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(accessTokenAuth.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("clientId");
        when(registeredClient.getClientName()).thenReturn("clientName");
        when(registeredClient.getAuthorizationGrantTypes()).thenReturn(Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS));
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn("token-value");
        Jwt jwt = mock(Jwt.class);
        when(jwtDecode.decode("token-value")).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("sub", "service-account"));

        // when
        eventListener.onSuccess(successEvent);

        // then
        ArgumentCaptor<ClientAuthenticatedEvent> captor = ArgumentCaptor.forClass(ClientAuthenticatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClientAuthenticatedEvent event = captor.getValue();
        assertThat(event.getGrantType()).isEqualTo("client_credentials");
        assertThat(event.getEventAttributes()).isNull();
    }
}

