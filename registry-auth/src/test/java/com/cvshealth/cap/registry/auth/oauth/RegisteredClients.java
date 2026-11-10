// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.config.ClientSecretsConfig;
import com.cvshealth.cap.registry.auth.model.CustomScope;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**********************************************************************************************************
 * NOTE: Test only. Populates the DB with registered clients via TestRegisteredClientsConfig.
 **********************************************************************************************************/
public class RegisteredClients {

    private final ClientSecretsConfig clientSecretsConfig;
    private final PasswordEncoder encoder;
    private final List<CustomScope> customAuthorizationScopes;
    private final List<CustomScope> customUserConsentScopes;

    public RegisteredClients(ClientSecretsConfig clientSecretsConfig, List<CustomScope> customAuthorizationScopes,
                             List<CustomScope> customUserConsentScopes) {
        this.clientSecretsConfig = clientSecretsConfig;
        this.customAuthorizationScopes = customAuthorizationScopes;
        this.customUserConsentScopes = customUserConsentScopes;
        this.encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    public List<RegisteredClient> getRegisteredClients() {
        return List.of(
                getRegistryClient("e08f8bf6-a627-498d-8ac2-af82f6e01501"),
                getCapCtlClient("e08f8bf6-a627-498d-8ac2-af82f6e01502"),
                getCapUIClient("e08f8bf6-a627-498d-8ac2-af82f6e01503"),
                getGrafanaRegistration("e08f8bf6-a627-498d-8ac2-af82f6e01504"),
                getGrafanaSyncRegistration("e08f8bf6-a627-498d-8ac2-af82f6e01508"),
                getApiPlatformRegistration("e08f8bf6-a627-498d-8ac2-af82f6e01509"),
                getAuthorizationCodeFlowWithPkceRegistration("e08f8bf6-a627-498d-8ac2-af82f6e01505"),
                getAuthorizationCodeFlowRegistration("e08f8bf6-a627-498d-8ac2-af82f6e01506"),
                getClientCredentialFlowRegistration("e08f8bf6-a627-498d-8ac2-af82f6e01507"),
                getTestUIClient("340ccbc1-0bf9-4350-babc-e3f06fe8ab4f", "TestUIClient", "340ccbc1-0bf9-4350-babc-e3f06fe8ab4f", getSecret("CapUIClient")),
                getTestUIClient("41fd1d6e-69d2-422d-b62d-f2bc370e1187", "TestUIClient-v2", "TestUIClient", getSecret("auth-code-flow")),
                getRegistryUserPkceClient("e08f8bf6-a627-498d-8ac2-af82f6e01515", "registry-test-user-pkce", "registry-test-user-pkce")
        );
    }

    private String getSecret(String clientId) {
        return encoder.encode(clientSecretsConfig.getSecret(clientId));
    }

    private RegisteredClient getRegistryClient(String id) {
        // Authorization Code Flow with Proof Key for Code Exchange (PKCE)
        return getRegisteredClientBuilder(id)
                .clientId("RegistryClient")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/ui/oauth-callback")
                .postLogoutRedirectUri("http://localhost:3000/ui/logged-out")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(8L))
                        .refreshTokenTimeToLive(Duration.ofHours(16L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .build())
                .build();
    }

    private RegisteredClient getCapCtlClient(String id) {
        // Authorization Code Flow with Proof Key for Code Exchange (PKCE)
        return getRegisteredClientBuilder(id)
                .clientId("CapCtlClient")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8989/oauth/callback")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(8L))
                        .refreshTokenTimeToLive(Duration.ofHours(16L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .build())
                .build();
    }

    private RegisteredClient getCapUIClient(String id) {
        // Authorization Code Flow
        return getRegisteredClientBuilder(id)
                .clientId("CapUIClient")
                .clientSecret(getSecret("CapUIClient"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/api/auth/callback/registry")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(8L))
                        .refreshTokenTimeToLive(Duration.ofHours(16L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(false)
                        .build())
                .build();
    }

    private RegisteredClient getTestUIClient(String id, String clientId, String clientName, String clientSecret) {
        // Authorization Code Flow
        Instant now = Instant.now();
        return getRegisteredClientBuilder(id)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(clientName)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientIdIssuedAt(now.minus(Duration.ofDays(10)))
                .clientSecretExpiresAt(now.plus(Duration.ofDays(80)))
                .redirectUri("http://localhost:8080/login/oauth2/code/oauth-authorization-code-oidc")
                .redirectUri("http://localhost:8080/oauth-callback")
                .postLogoutRedirectUri("http://localhost:8080/logged-out")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5L))
                        .refreshTokenTimeToLive(Duration.ofMinutes(30L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).requireProofKey(false).build())
                .build();
    }

    private RegisteredClient getGrafanaRegistration(String id) {
        // grafana registration for client credentials and auth code flow
        return getRegisteredClientBuilder(id)
                .clientId("grafana")
                .clientSecret(getSecret("grafana"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/login") // used by grafana proxy for local development
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(8L))
                        .refreshTokenTimeToLive(Duration.ofHours(16L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).requireProofKey(false).build())
                .build();
    }

    private RegisteredClient getGrafanaSyncRegistration(String id) {
        // grafana registration for client credentials flow
        return getRegisteredClientBuilder(id)
                .clientId("grafanaSync")
                .clientSecret(getSecret("grafanaSync"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30L))
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient getApiPlatformRegistration(String id) {
        // API platform registration for client credentials flow
        return getRegisteredClientBuilder(id)
                .clientId("apiplatform")
                .clientSecret(getSecret("apiplatform"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30L))
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient getAuthorizationCodeFlowWithPkceRegistration(String id) {
        // Authorization Code Flow with Proof Key for Code Exchange (PKCE)
        return getRegisteredClientBuilder(id)
                .clientId("auth-code-flow-pkce")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/oauth-callback")
                .redirectUri("http://wild*:8080/oauth-callback")
                .postLogoutRedirectUri("http://localhost:8080/logged-out")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5L))
                        .refreshTokenTimeToLive(Duration.ofMinutes(30L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .build())
                .build();
    }

    private RegisteredClient getRegistryUserPkceClient(String id, String clientId, String clientName) {
        // Authorization Code Flow with Proof Key for Code Exchange (PKCE)
        return getRegisteredClientBuilder(id)
                .clientId(clientId)
                .clientName(clientName)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(List.of(
                        "http://localhost:8080/oauth-callback",
                        "http://127.0.0.1:8989/oauth/callback",
                        "http://wild*:8080/oauth-callback",
                        "http://localhost:3000"
                )))
                .postLogoutRedirectUri("http://localhost:8080/logged-out")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(600L))
                        .refreshTokenTimeToLive(Duration.ofMinutes(600L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .build())
                .build();
    }


    private RegisteredClient getAuthorizationCodeFlowRegistration(String id) {
        // Authorization Code Flow example
        Instant now = Instant.now();
        return getRegisteredClientBuilder(id)
                .clientId("auth-code-flow")
                .clientSecret(getSecret("auth-code-flow"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientIdIssuedAt(now.minus(Duration.ofDays(10)))
                .clientSecretExpiresAt(now.plus(Duration.ofDays(80)))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/login/oauth2/code/oauth-authorization-code-oidc")
                .redirectUri("http://localhost:8080/oauth-callback")
                .postLogoutRedirectUri("http://localhost:8080/logged-out")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5L))
                        .refreshTokenTimeToLive(Duration.ofMinutes(30L))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).requireProofKey(false).build())
                .build();
    }

    private RegisteredClient getClientCredentialFlowRegistration(String id) {
        // Client Credentials Flow example
        return getRegisteredClientBuilder(id)
                .clientId("client-credentials-flow")
                .clientSecret(getSecret("client-credentials-flow"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5L))
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    RegisteredClient.Builder getRegisteredClientBuilder(String id) {
        RegisteredClient.Builder registeredClientBuilder = RegisteredClient.withId(id)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL);
        // add custom user consent scopes
        if (!CollectionUtils.isEmpty(customUserConsentScopes)) {
            for (CustomScope scope : customUserConsentScopes) {
                scope.scopeType.getScopes().forEach(registeredClientBuilder::scope);
            }
        }
        // add custom scopes used for adding custom JWT claims
        if (!CollectionUtils.isEmpty(customAuthorizationScopes)) {
            for (CustomScope scope : customAuthorizationScopes) {
                scope.scopeType.getScopes().forEach(registeredClientBuilder::scope);
            }
        }
        return registeredClientBuilder;
    }

}
