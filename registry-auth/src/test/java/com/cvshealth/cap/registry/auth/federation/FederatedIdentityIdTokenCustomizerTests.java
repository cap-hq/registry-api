// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.federation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cvshealth.cap.registry.auth.config.AuthorizeUrlConfig;
import com.cvshealth.cap.registry.auth.config.RefreshTokenConfig;
import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.model.*;
import com.cvshealth.cap.registry.auth.metric.ClientScopeStats;
import com.cvshealth.cap.registry.auth.service.RegistryService;
import com.cvshealth.cap.registry.auth.service.AuditService;
import com.cvshealth.cap.registry.auth.service.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService.SessionWindow;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.util.*;

import static com.cvshealth.cap.registry.auth.model.CustomScopeType.REGISTRY;
import static com.cvshealth.cap.registry.auth.model.CustomScopeType.OPEN_POLICY_AGENT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FederatedIdentityIdTokenCustomizerTests {

    private static final String AGENT = "agent";

    private FederatedIdentityIdTokenCustomizer federatedIdentityIdTokenCustomizer;

    @Mock
    private RegistryService registryService;
    @Mock
    private ClientService clientService;
    @Mock
    private RegisteredClientConfig registeredClientConfig;
    @Mock
    private ClientScopeStats clientScopeStats;

    @Mock
    private RegisteredClient registeredClient;

    @Mock
    private JwtEncodingContext jwtContext;

    @Mock
    private OAuth2Authorization authorization;

    @Mock
    private OAuth2AuthorizationRequest authorizationRequest;

    @Mock
    private Authentication authentication;

    @Mock
    private AuthorizeUrlConfig authorizeUrlConfig;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private static final CustomScopeType CUSTOM_SCOPE_TYPE = CustomScopeType.REGISTRY;

    @BeforeEach
    void setUp() {
        federatedIdentityIdTokenCustomizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);
    }

    private static RefreshTokenConfig refreshTokenConfig(long defaultSessionExpireMinutes) {
        RefreshTokenConfig config = new RefreshTokenConfig();
        config.setDefaultSessionExpireMinutes(defaultSessionExpireMinutes);
        return config;
    }

    @Test
    public void getAzpClaim() {
        // given
        String clientId = "clientId";
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        Map<String, Object> additionalParameters = Map.of("origin_client_id", clientId);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(additionalParameters);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAzpClaim(jwtContext);

        // then
        assertThat(result).isEqualTo(clientId);
    }

    @Test
    public void getAzpClaim_noAuthorization() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(null);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAzpClaim(jwtContext);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getAzpClaim_noAuthorizationRequest() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(null);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAzpClaim(jwtContext);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getAzpClaim_authorizationRequest_noAdditionalParameters() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(null);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAzpClaim(jwtContext);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getAzpClaim_authorizationRequest_emptyAdditionalParameters() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(Collections.emptyMap());

        // when
        String result = federatedIdentityIdTokenCustomizer.getAzpClaim(jwtContext);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getAudiences_multipleVersions_containsClientIdAndName() {
        // given
        when(registeredClient.getClientId()).thenReturn("my-client-v2");
        when(registeredClient.getClientName()).thenReturn("my-client");

        // when
        List<String> result = FederatedIdentityIdTokenCustomizer.getAudiences(registeredClient);

        // then
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.contains("my-client-v2")).isTrue();
        assertThat(result.contains("my-client")).isTrue();
    }

    @Test
    public void getAudiences_oneVersion_containsClientIdOnly() {
        // given
        when(registeredClient.getClientId()).thenReturn("my-client");
        when(registeredClient.getClientName()).thenReturn("123546");

        // when
        List<String> result = FederatedIdentityIdTokenCustomizer.getAudiences(registeredClient);

        // then
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.contains("my-client")).isTrue();
    }

    @Test
    public void getRegistryClaims() {
        // given
        String clientId = "my-client-id";
        Client client = mock(Client.class);
        when(clientService.getClient(clientId)).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn(clientId);
        when(clientService.getClientVersion(client)).thenReturn(1);
        Map<String, Object> claims = Map.of("clientType", "USER");
        when(registryService.getClaimsForClientId(clientId, 1, CUSTOM_SCOPE_TYPE)).thenReturn(claims);

        // when
        Map<String, Object> result = federatedIdentityIdTokenCustomizer.getRegistryClaims(clientId, jwtContext, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(claims);
    }

    @Test
    public void getRegistryClaims_allowMultipleSecretsPerClient() {
        // given
        when(registeredClientConfig.isAllowMultipleSecretsPerClient()).thenReturn(true);
        String clientId = "my-client-id-v2";
        String clientName = "my-client-id";
        Client client = mock(Client.class);
        when(clientService.getClient(clientId)).thenReturn(client);
        when(client.getClientName()).thenReturn(clientName);
        when(clientService.getBaseClientId(client)).thenReturn(clientName);
        when(clientService.getClientVersion(client)).thenReturn(2);
        when(client.getClientSecretExpiresAt()).thenReturn(Instant.now().plusSeconds(60*60));
        Map<String, Object> claims = new HashMap<>();
        when(registryService.getClaimsForClientId(clientName, 2, CUSTOM_SCOPE_TYPE)).thenReturn(claims);

        // when
        Map<String, Object> result = federatedIdentityIdTokenCustomizer.getRegistryClaims(clientId, jwtContext, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void getRegistryClaims_nullClientId() {
        // when
        Map<String, Object> result = federatedIdentityIdTokenCustomizer.getRegistryClaims((String) null, jwtContext, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result.isEmpty()).isTrue();
        verifyNoInteractions(jwtContext);
    }

    @Test
    public void getRegistryClaims_clientServiceError_throws() {
        // given
        String clientId = "my-client-id";
        when(clientService.getClient(clientId)).thenThrow(new RuntimeException("not found"));

        // when / then
        assertThrows(RuntimeException.class, () ->
                federatedIdentityIdTokenCustomizer.getRegistryClaims(clientId, jwtContext, CUSTOM_SCOPE_TYPE));
    }

    @Test
    public void getRegistryClaims_registryService_error_throws() {
        // given
        String clientId = "my-client-id";
        Client client = mock(Client.class);
        when(clientService.getClient(clientId)).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn(clientId);
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryService.getClaimsForClientId(clientId, 1, CUSTOM_SCOPE_TYPE)).thenThrow(new RuntimeException("service error"));
        when(jwtContext.getClaims()).thenReturn(JwtClaimsSet.builder());

        // when / then
        assertThrows(RuntimeException.class, () ->
                federatedIdentityIdTokenCustomizer.getRegistryClaims(clientId, jwtContext, CUSTOM_SCOPE_TYPE));
        verify(eventPublisher).publishEvent(any(TokenCustomizationFailureEvent.class));
    }

    @Test
    public void getRegistryClaims_forUser_registryService_error_throws() {
        // given
        String externalId = "external-id";
        DefaultOidcUser user = mockUser(externalId);
        when(registryService.getClaims(externalId, CUSTOM_SCOPE_TYPE)).thenThrow(new RuntimeException("service error"));
        when(jwtContext.getClaims()).thenReturn(JwtClaimsSet.builder());

        // when / then
        assertThrows(RuntimeException.class, () ->
                federatedIdentityIdTokenCustomizer.getRegistryClaims(user, jwtContext, CUSTOM_SCOPE_TYPE));
        verify(eventPublisher).publishEvent(any(TokenCustomizationFailureEvent.class));
    }

    @Test
    public void getRegistryIdentityClaims() {
        // given
        String externalId = "external-id";
        DefaultOidcUser user = mockUser(externalId);
        Map<String, Object> claims = new HashMap<>();
        when(registryService.getIdentityClaims(externalId, CUSTOM_SCOPE_TYPE)).thenReturn(claims);

        // when
        Map<String, Object> result = federatedIdentityIdTokenCustomizer.getRegistryIdentityClaims(user, jwtContext, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(claims);
    }

    @Test
    public void populateAccessTokenClaims_forOPA() {
        // given
        CustomScopeType scopeType = OPEN_POLICY_AGENT;
        String clientId = "client-1";
        Client client = Client.builder().clientId(clientId).clientName(clientId).build();
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(clientService.getClient(clientId)).thenReturn(client);

        // mock user
        String externalId = "external-id";
        DefaultOidcUser user = mockUser(externalId);
        when(authentication.getPrincipal()).thenReturn(user);

        when(clientService.getBaseClientId(client)).thenReturn(clientId);
        when(clientService.getClientVersion(client)).thenReturn(1);
        Map<String, Object> clientClaims = new HashMap<>(Map.of("clientName", clientId));
        when(registryService.getClaimsForClientId(clientId, 1, scopeType)).thenReturn(clientClaims);
        Map<String, Object> userClaims = new HashMap<>(Map.of("external-id", externalId));
        when(registryService.getClaims(externalId, scopeType)).thenReturn(userClaims);

        // when
        Map<String, Object> customClaims = federatedIdentityIdTokenCustomizer.populateAccessTokenClaims(jwtContext, scopeType);

        // then
        assertThat(customClaims).isNotNull();
        assertThat(customClaims.size()).isEqualTo(4);
        assertThat(customClaims.get("clientType")).isEqualTo("NON_USER"); // mock RegisteredClient has no grant types
    }

    @Test
    public void populateAccessTokenClaims_forAppReg_withUser() {
        // given
        CustomScopeType scopeType = CustomScopeType.REGISTRY;

        // mock client
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);

        // mock user
        when(jwtContext.getPrincipal()).thenReturn(authentication);
        String externalId = "external-id";
        DefaultOidcUser user = mockUser(externalId);
        when(authentication.getPrincipal()).thenReturn(user);

        Map<String, Object> userClaims = new HashMap<>(Map.of("external-id", externalId));
        when(registryService.getClaims(externalId, scopeType)).thenReturn(userClaims);

        // when
        Map<String, Object> customClaims = federatedIdentityIdTokenCustomizer.populateAccessTokenClaims(jwtContext, scopeType);

        // then
        assertThat(customClaims).isNotNull();
        assertThat(customClaims.size()).isEqualTo(3);
        assertThat(customClaims.get("clientType")).isEqualTo("NON_USER"); // mock RegisteredClient has no grant types
        verifyNoMoreInteractions(registryService);
        verify(clientService, never()).getClient(any());
    }

    @Test
    public void getRegistryClaims_forAppReg_withUser() {
        // given
        CustomScopeType scopeType = CUSTOM_SCOPE_TYPE;
        String externalId = "external-id";
        DefaultOidcUser user = mockUser(externalId);

        Map<String, Object> claims = new HashMap<>(Map.of("claim1", "value"));
        when(registryService.getClaims(externalId, scopeType)).thenReturn(claims);
        // when
        Map<String, Object> customClaims = federatedIdentityIdTokenCustomizer.getRegistryClaims(user, jwtContext, scopeType);

        // then
        assertThat(customClaims).isNotNull();
        assertThat(customClaims.size()).isEqualTo(1);
    }

    @Test
    public void getCustomAuthorizationScope_noConfiguredScopes() {
        // given
        Set<String> authorizedScopes = Set.of(CUSTOM_SCOPE_TYPE.getScope(), OPEN_POLICY_AGENT.getScope());
        List<CustomScope> customAuthorizationScopes = List.of(
                CustomScope.builder().scopeType(OPEN_POLICY_AGENT).defaultScope(true).build()
        );

        // when
        assertThat(federatedIdentityIdTokenCustomizer.getCustomAuthorizationScope(null)).isNull();
        assertThat(federatedIdentityIdTokenCustomizer.getCustomAuthorizationScope(authorizedScopes)).isNull();
    }

    @Test
    public void getCustomAuthorizationScope() {
        // given
        Set<String> authorizedScopes = Set.of(CUSTOM_SCOPE_TYPE.getScope(), OPEN_POLICY_AGENT.getScope());
        List<CustomScope> customAuthorizationScopes = List.of(
                CustomScope.builder().scopeType(OPEN_POLICY_AGENT).defaultScope(false).build()
        );
        List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters = Collections.emptyList();

        FederatedIdentityIdTokenCustomizer tokenCustomizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                customAuthorizationScopes, null, authorizeUrlCustomParameters, clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);

        // when
        CustomScope customScope = tokenCustomizer.getCustomAuthorizationScope(authorizedScopes);

        // then
        assertThat(customScope).isEqualTo(customAuthorizationScopes.get(0));
    }

    @Test
    public void getCustomAuthorizationScope_useDefault() {
        // given
        List<CustomScope> customAuthorizationScopes = List.of(
                CustomScope.builder().scopeType(OPEN_POLICY_AGENT).defaultScope(false).build(),
                CustomScope.builder().scopeType(REGISTRY).defaultScope(true).build()
        );
        List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters = Collections.emptyList();

        FederatedIdentityIdTokenCustomizer tokenCustomizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                customAuthorizationScopes, null, authorizeUrlCustomParameters, clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);

        // when
        CustomScope customScope = tokenCustomizer.getCustomAuthorizationScope(null);

        // then
        assertThat(customScope).isEqualTo(customAuthorizationScopes.get(1));
    }

    @Test
    public void getCustomAuthorizationScope_legacyScopeName() {
        // given - OPA is the default, so a match can't come from the default fallback
        List<CustomScope> customAuthorizationScopes = List.of(
                CustomScope.builder().scopeType(OPEN_POLICY_AGENT).defaultScope(true).build(),
                CustomScope.builder().scopeType(REGISTRY).defaultScope(false).build()
        );

        FederatedIdentityIdTokenCustomizer tokenCustomizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                customAuthorizationScopes, null, Collections.emptyList(), clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);

        // when / then
        assertThat(tokenCustomizer.getCustomAuthorizationScope(Set.of("App.AppReg"))).isEqualTo(customAuthorizationScopes.get(1));
        assertThat(tokenCustomizer.getCustomAuthorizationScope(Set.of("App.Registry"))).isEqualTo(customAuthorizationScopes.get(1));
    }

    @Test
    public void getCustomAuthorizationScope_legacyScopeNameWithOpa_keepsConfigOrder() {
        // given - same order as application-tokens.yml
        List<CustomScope> customAuthorizationScopes = List.of(
                CustomScope.builder().scopeType(REGISTRY).defaultScope(true).build(),
                CustomScope.builder().scopeType(OPEN_POLICY_AGENT).defaultScope(false).build()
        );

        FederatedIdentityIdTokenCustomizer tokenCustomizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                customAuthorizationScopes, null, Collections.emptyList(), clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);

        // when
        CustomScope customScope = tokenCustomizer.getCustomAuthorizationScope(Set.of("App.AppReg", "App.OPA"));

        // then
        assertThat(customScope).isEqualTo(customAuthorizationScopes.get(0));
    }

    @Test
    public void logLegacyScopeUse() {
        // given
        Logger customizerLogger = (Logger) LoggerFactory.getLogger(FederatedIdentityIdTokenCustomizer.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        customizerLogger.addAppender(logAppender);

        try {
            // when - none of these request only the legacy name
            federatedIdentityIdTokenCustomizer.logLegacyScopeUse("clientId", null, Set.of("App.AppReg"));
            federatedIdentityIdTokenCustomizer.logLegacyScopeUse("clientId", REGISTRY, Collections.emptySet());
            federatedIdentityIdTokenCustomizer.logLegacyScopeUse("clientId", REGISTRY, Set.of("App.Registry"));
            federatedIdentityIdTokenCustomizer.logLegacyScopeUse("clientId", REGISTRY, Set.of("App.Registry", "App.AppReg"));
            federatedIdentityIdTokenCustomizer.logLegacyScopeUse("clientId", OPEN_POLICY_AGENT, Set.of("App.OPA"));

            // then
            assertThat(logAppender.list.size()).isEqualTo(0);

            // when
            federatedIdentityIdTokenCustomizer.logLegacyScopeUse("clientId", REGISTRY, Set.of("openid", "App.AppReg"));

            // then
            assertThat(logAppender.list.size()).isEqualTo(1);
            assertThat(logAppender.list.get(0).getFormattedMessage())
                    .isEqualTo("client clientId requested legacy scope App.AppReg, use App.Registry instead");
        } finally {
            customizerLogger.detachAppender(logAppender);
        }
    }

    // --- customize: session_expires_at on authorization_code grant ---

    @Test
    public void customize_authorizationCodeGrant_usesDbSessionExpiresAt_whenAvailable() {
        // given: DB already has session_expires_at (e.g. if save order ever changes)
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, authService);

        Instant dbSessionExp = Instant.now().plusSeconds(1440L * 60).minusSeconds(42);
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionExpiresAt(authorizationId)).thenReturn(dbSessionExp);

        // when
        customizer.customize(jwtContext);

        // then: DB value used, not iat-derived
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        assertThat(resultClaims.get("session_expires_at")).isEqualTo((int) dbSessionExp.getEpochSecond());
    }

    @Test
    public void customize_authorizationCodeGrant_fallsBackToIat_whenDbReturnsNull() {
        // given: DB returns null (normal case — save() hasn't run yet at token customization time)
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        long defaultSessionExpireMinutes = 1440L;
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(defaultSessionExpireMinutes), 30L, authService);

        Instant iat = Instant.now();
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(iat)
                .expiresAt(iat.plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionExpiresAt(authorizationId)).thenReturn(null);
        when(authService.resolveSessionExpireMinutes(authorization)).thenReturn(defaultSessionExpireMinutes);

        // when
        customizer.customize(jwtContext);

        // then: falls back to iat + defaultSessionExpireMinutes
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        Instant expectedSessionExp = iat.plusSeconds(defaultSessionExpireMinutes * 60);
        assertThat(resultClaims.get("session_expires_at")).isEqualTo((int) expectedSessionExp.getEpochSecond());
    }

    // --- customize: session_expires_at on refresh token grant ---

    @Test
    public void customize_refreshTokenGrant_setsSessionExpiresAt() {
        // given
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, authService);

        Instant sessionExp = Instant.now().plusSeconds(1440L * 60);
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(sessionExp, null, "authorization_code")));

        // when
        customizer.customize(jwtContext);

        // then
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        assertThat(resultClaims.get("session_expires_at")).isEqualTo((int) sessionExp.getEpochSecond());
    }

    @Test
    public void customize_refreshTokenGrant_capsExpWhenScopeExceedsSessionBoundary() {
        // given: session expiring in 5 min, but token exp is 3 hours out (e.g. from scope override)
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        long accessTokenExpireMinutes = 30L;
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), accessTokenExpireMinutes, authService);

        Instant sessionExp = Instant.now().plusSeconds(5 * 60);
        Instant scopeExtendedExp = Instant.now().plusSeconds(3 * 60 * 60);
        Instant expectedCap = sessionExp.plusSeconds(accessTokenExpireMinutes * 60);
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(scopeExtendedExp);

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(sessionExp, null, "authorization_code")));

        // when
        customizer.customize(jwtContext);

        // then: exp is capped at sessionExp + 30 min, not left at the 3-hour scope-extended value
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        Instant resultExp = (Instant) resultClaims.get("exp");
        assertThat(resultExp).isEqualTo(expectedCap);
        assertThat(resultExp).isBefore(scopeExtendedExp);
    }

    @Test
    public void customize_refreshTokenGrant_doesNotCapExpWhenWithinSessionBoundary() {
        // given: session expiring in 2 hours, token exp 30 min out — no cap should fire
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        long accessTokenExpireMinutes = 30L;
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), accessTokenExpireMinutes, authService);

        Instant sessionExp = Instant.now().plusSeconds(2 * 60 * 60);
        Instant originalExp = Instant.now().plusSeconds(30 * 60);
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(originalExp);

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(sessionExp, null, "authorization_code")));

        // when
        customizer.customize(jwtContext);

        // then: exp is unchanged
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        assertThat((Instant) resultClaims.get("exp")).isEqualTo(originalExp);
    }

    @Test
    public void customize_refreshTokenGrant_preMigration_fallsBackToFirstIssuedAt() {
        // given: session_expires_at not yet set in DB (pre-migration row), but first_access_token_issued_at is
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        long defaultSessionExpireMinutes = 1440L;
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(defaultSessionExpireMinutes), 30L, authService);

        Instant firstIssuedAt = Instant.now().minusSeconds(60 * 60); // session started 1 hour ago
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(null, firstIssuedAt, "authorization_code")));
        when(authorization.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(authService.resolveSessionExpireMinutes(authorization)).thenReturn(defaultSessionExpireMinutes);

        // when
        customizer.customize(jwtContext);

        // then: session_expires_at derived from firstIssuedAt, not absent
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        Instant expectedSessionExp = firstIssuedAt.plusSeconds(defaultSessionExpireMinutes * 60);
        assertThat(resultClaims.get("session_expires_at")).isEqualTo((int) expectedSessionExp.getEpochSecond());
    }

    // --- customize: session_expires_at for insecure (http://) redirect URIs ---

    @Test
    public void customize_authorizationCodeGrant_insecureRedirect_fallsBackToIatWithInsecureSessionWindow() {
        // given: DB returns null and the authorization resolves to the shorter insecure-redirect window
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        long insecureSessionExpireMinutes = 240L;
        long accessTokenExpireMinutes = 30L;
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), accessTokenExpireMinutes, authService);

        Instant iat = Instant.now();
        String authorizationId = UUID.randomUUID().toString();
        // exp 8 hours out — past the 4-hour insecure session boundary
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(iat)
                .expiresAt(iat.plusSeconds(8 * 60 * 60));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionExpiresAt(authorizationId)).thenReturn(null);
        when(authService.resolveSessionExpireMinutes(authorization)).thenReturn(insecureSessionExpireMinutes);

        // when
        customizer.customize(jwtContext);

        // then: session_expires_at uses the insecure window and exp is capped at that boundary
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        Instant expectedSessionExp = iat.plusSeconds(insecureSessionExpireMinutes * 60);
        assertThat(resultClaims.get("session_expires_at")).isEqualTo((int) expectedSessionExp.getEpochSecond());
        assertThat((Instant) resultClaims.get("exp")).isEqualTo(expectedSessionExp.plusSeconds(accessTokenExpireMinutes * 60));
    }

    @Test
    public void customize_refreshTokenGrant_preMigration_insecureRedirect_usesInsecureSessionWindow() {
        // given: pre-migration row (no session_expires_at) for an authorization with an http:// redirect
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        long insecureSessionExpireMinutes = 240L;
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, authService);

        Instant firstIssuedAt = Instant.now().minusSeconds(60 * 60); // session started 1 hour ago
        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(null, firstIssuedAt, "authorization_code")));
        when(authorization.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(authService.resolveSessionExpireMinutes(authorization)).thenReturn(insecureSessionExpireMinutes);

        // when
        customizer.customize(jwtContext);

        // then: session_expires_at derived from firstIssuedAt + insecure window
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        Instant expectedSessionExp = firstIssuedAt.plusSeconds(insecureSessionExpireMinutes * 60);
        assertThat(resultClaims.get("session_expires_at")).isEqualTo((int) expectedSessionExp.getEpochSecond());
    }

    @Test
    public void customize_refreshTokenGrant_nullSessionExpiresAt_doesNotSetClaim() {
        // given
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, authService);

        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(null, null, "authorization_code")));
        when(authorization.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);

        // when
        customizer.customize(jwtContext);

        // then
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        assertThat(resultClaims.containsKey("session_expires_at")).isFalse();
    }

    @Test
    public void customize_refreshTokenGrant_preMigration_nonAuthorizationCodeGrant_doesNotSetClaim() {
        // given: pre-migration row (no session_expires_at) whose original grant is not authorization_code
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, authService);

        String authorizationId = UUID.randomUUID().toString();
        Instant originalExp = Instant.now().plusSeconds(3600);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(originalExp);

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId))
                .thenReturn(Optional.of(new SessionWindow(null, null, "authorization_code")));
        when(authorization.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.DEVICE_CODE);

        // when
        customizer.customize(jwtContext);

        // then: no session limit applied, and the fallback lookup is skipped
        Map<String, Object> resultClaims = claimsBuilder.build().getClaims();
        assertThat(resultClaims.containsKey("session_expires_at")).isFalse();
        assertThat((Instant) resultClaims.get("exp")).isEqualTo(originalExp);
        verify(authService, never()).findFirstAccessTokenIssuedAt(any());
    }

    @Test
    public void customize_refreshTokenGrant_missingRow_throwsInvalidGrant() {
        // given: the authorization row was deleted mid-request
        CustomJdbcOauth2AuthorizationService authService = mock(CustomJdbcOauth2AuthorizationService.class);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                Collections.emptyList(), null, Collections.emptyList(), clientScopeStats, auditService,
                Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, authService);

        String authorizationId = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("UNKNOWN");
        when(jwtContext.getClaims()).thenReturn(claimsBuilder);
        when(jwtContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(jwtContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(jwtContext.getAuthorizedScopes()).thenReturn(Collections.emptySet());
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn("client-1");
        when(registeredClient.getClientName()).thenReturn("client-1");
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getId()).thenReturn(authorizationId);
        when(authService.findSessionWindowById(authorizationId)).thenReturn(Optional.empty());

        // when / then: fail closed rather than issue an uncapped token
        OAuth2AuthenticationException e = assertThrows(OAuth2AuthenticationException.class,
                () -> customizer.customize(jwtContext));
        assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
    }

    private DefaultOidcUser mockUser(String externalId) {
        DefaultOidcUser user = mock(DefaultOidcUser.class);
        when(user.getAttribute(TokenClaim.OID)).thenReturn(externalId);
        return user;
    }

    // --- getAdditionalParameter(agent) tests ---

    @Test
    public void getUserAgentClaim() {
        // given
        String agentValue = "copilot";
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        Map<String, Object> additionalParameters = Map.of(AGENT, agentValue);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(additionalParameters);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAdditionalParameter(jwtContext, AGENT);

        // then
        assertThat(result).isEqualTo(agentValue);
    }

    @Test
    public void getUserAgentClaim_noAuthorization() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(null);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAdditionalParameter(jwtContext, AGENT);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getUserAgentClaim_noAuthorizationRequest() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(null);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAdditionalParameter(jwtContext, AGENT);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getUserAgentClaim_authorizationRequest_noAdditionalParameters() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(null);

        // when
        String result = federatedIdentityIdTokenCustomizer.getAdditionalParameter(jwtContext, AGENT);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getUserAgentClaim_authorizationRequest_emptyAdditionalParameters() {
        // given
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(Collections.emptyMap());

        // when
        String result = federatedIdentityIdTokenCustomizer.getAdditionalParameter(jwtContext, AGENT);

        // then
        assertThat(result).isNull();
    }

    // --- populateIdTokenClaims with user_agent ---

    @Test
    public void populateIdTokenClaims_withUserAgent() {
        // given
        String clientId = "client-1";
        String parameterName = "agent";
        String agentValue = "copilot";

        CustomClaim agentClaim = CustomClaim.builder()
                .name(AGENT)
                .type(CustomClaim.TYPE_CUSTOM)
                .source(CustomClaim.SOURCE_AUTH_PARAMETER)
                .attribute(parameterName)
                .build();
        List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters = List.of(
                AuthorizeUrlCustomParameter.builder().name("agent").values(List.of(agentValue)).build()
        );
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), List.of(agentClaim), authorizeUrlCustomParameters, clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn(clientId);
        // No principal (skips getRegistryIdentityClaims)
        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(clientId);

        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        Map<String, Object> additionalParameters = Map.of(AGENT, agentValue);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(additionalParameters);

        // when
        Map<String, Object> result = customizer.populateIdTokenClaims(jwtContext, null);

        // then
        assertThat(result.get(AGENT)).isEqualTo(agentValue);
    }

    @Test
    public void populateIdTokenClaims_withoutUserAgent() {
        // given
        String clientId = "client-1";
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn(clientId);
        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(clientId);

        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(Collections.emptyMap());

        // when
        Map<String, Object> result = federatedIdentityIdTokenCustomizer.populateIdTokenClaims(jwtContext, null);

        // then
        assertThat(result.containsKey(AGENT)).isFalse();
    }

    @Test
    public void populateAccessTokenClaims_includesAgentClaim_whenScopeIsNull() {
        // given - agent claim configured as AUTH_PARAMETER identity claim
        String clientId = "client-1";
        String agentValue = "copilot";
        CustomClaim agentClaim = CustomClaim.builder()
                .name(AGENT).type(CustomClaim.TYPE_CUSTOM)
                .source(CustomClaim.SOURCE_AUTH_PARAMETER).attribute(AGENT)
                .build();
        List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters = List.of(
                AuthorizeUrlCustomParameter.builder().name(AGENT).values(List.of(agentValue)).build()
        );
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), List.of(agentClaim), authorizeUrlCustomParameters, clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);

        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn(clientId);
        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(clientId);
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(Map.of(AGENT, agentValue));

        // when - no custom scope (scopeType is null)
        Map<String, Object> result = customizer.populateAccessTokenClaims(jwtContext, null);

        // then - agent claim is present even without a custom scope
        assertThat(result.get(AGENT)).isEqualTo(agentValue);
        assertThat(result.get("clientId")).isEqualTo(clientId);
    }

    @Test
    public void populateAccessTokenClaims_includesAgentClaim_whenScopeIsOPA() {
        // given - agent claim configured as AUTH_PARAMETER identity claim with OPA scope
        CustomScopeType scopeType = OPEN_POLICY_AGENT;
        String clientId = "client-1";
        String agentValue = "copilot";
        CustomClaim agentClaim = CustomClaim.builder()
                .name(AGENT).type(CustomClaim.TYPE_CUSTOM)
                .source(CustomClaim.SOURCE_AUTH_PARAMETER).attribute(AGENT)
                .build();
        List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters = List.of(
                AuthorizeUrlCustomParameter.builder().name(AGENT).values(List.of(agentValue)).build()
        );
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), List.of(agentClaim), authorizeUrlCustomParameters, clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);

        Client client = Client.builder().clientId(clientId).clientName(clientId).build();
        when(jwtContext.getRegisteredClient()).thenReturn(registeredClient);
        when(registeredClient.getClientId()).thenReturn(clientId);
        when(jwtContext.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(clientId);
        when(clientService.getClient(clientId)).thenReturn(client);
        when(clientService.getBaseClientId(client)).thenReturn(clientId);
        when(clientService.getClientVersion(client)).thenReturn(1);
        when(registryService.getClaimsForClientId(clientId, 1, scopeType)).thenReturn(new HashMap<>());
        when(jwtContext.get(OAuth2Authorization.class)).thenReturn(authorization);
        when(authorization.getAttribute(OAuth2AuthorizationRequest.class.getName())).thenReturn(authorizationRequest);
        when(authorizationRequest.getAdditionalParameters()).thenReturn(Map.of(AGENT, agentValue));

        // when
        Map<String, Object> result = customizer.populateAccessTokenClaims(jwtContext, scopeType);

        // then - agent claim is present alongside clientId
        assertThat(result.get(AGENT)).isEqualTo(agentValue);
        assertThat(result.get("clientId")).isEqualTo(clientId);
    }

    // --- applyScopeExpiry tests ---

    @Test
    public void applyScopeExpiry_opaScope_withOverrideTtl_overridesExp() {
        // given
        long expireMinutes = 60L;
        Map<CustomScopeType, Long> scopeExpireMinutes = Map.of(CustomScopeType.OPEN_POLICY_AGENT, expireMinutes);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), null, Collections.emptyList(), clientScopeStats, auditService, scopeExpireMinutes, eventPublisher, refreshTokenConfig(1440L), 30L, null);
        Instant issuedAt = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iat", issuedAt);

        // when
        customizer.applyScopeExpiry(claims, CustomScopeType.OPEN_POLICY_AGENT);

        // then
        assertThat(claims.containsKey("exp")).isTrue();
        Instant exp = (Instant) claims.get("exp");
        assertThat(exp).isEqualTo(issuedAt.plusSeconds(expireMinutes * 60));
    }

    @Test
    public void applyScopeExpiry_registryScope_withOverrideTtl_overridesExp() {
        // given — a different scope (REGISTRY) also gets overridden when configured
        long expireMinutes = 120L;
        Map<CustomScopeType, Long> scopeExpireMinutes = Map.of(CustomScopeType.REGISTRY, expireMinutes);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), null, Collections.emptyList(), clientScopeStats, auditService, scopeExpireMinutes, eventPublisher, refreshTokenConfig(1440L), 30L, null);
        Instant issuedAt = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iat", issuedAt);

        // when
        customizer.applyScopeExpiry(claims, CustomScopeType.REGISTRY);

        // then
        assertThat(claims.containsKey("exp")).isTrue();
        Instant exp = (Instant) claims.get("exp");
        assertThat(exp).isEqualTo(issuedAt.plusSeconds(expireMinutes * 60));
    }

    @Test
    public void applyScopeExpiry_scope_notInMap_doesNotOverrideExp() {
        // given — map has OPA config but request scope is REGISTRY
        Map<CustomScopeType, Long> scopeExpireMinutes = Map.of(CustomScopeType.OPEN_POLICY_AGENT, 60L);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), null, Collections.emptyList(), clientScopeStats, auditService, scopeExpireMinutes, eventPublisher, refreshTokenConfig(1440L), 30L, null);
        Map<String, Object> claims = new HashMap<>();

        // when
        customizer.applyScopeExpiry(claims, CustomScopeType.REGISTRY);

        // then
        assertThat(claims.containsKey("exp")).isFalse();
    }

    @Test
    public void applyScopeExpiry_emptyMap_doesNotOverrideExp() {
        // given — no overrides configured
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), null, Collections.emptyList(), clientScopeStats, auditService, Collections.emptyMap(), eventPublisher, refreshTokenConfig(1440L), 30L, null);
        Map<String, Object> claims = new HashMap<>();

        // when
        customizer.applyScopeExpiry(claims, CustomScopeType.OPEN_POLICY_AGENT);

        // then
        assertThat(claims.containsKey("exp")).isFalse();
    }

    @Test
    public void applyScopeExpiry_nullScope_doesNotOverrideExp() {
        // given — scope is null (no custom scope resolved)
        Map<CustomScopeType, Long> scopeExpireMinutes = Map.of(CustomScopeType.OPEN_POLICY_AGENT, 60L);
        FederatedIdentityIdTokenCustomizer customizer = new FederatedIdentityIdTokenCustomizer(
                registryService, clientService, registeredClientConfig,
                List.of(), null, Collections.emptyList(), clientScopeStats, auditService, scopeExpireMinutes, eventPublisher, refreshTokenConfig(1440L), 30L, null);
        Map<String, Object> claims = new HashMap<>();

        // when
        customizer.applyScopeExpiry(claims, null);

        // then
        assertThat(claims.containsKey("exp")).isFalse();
    }
}
