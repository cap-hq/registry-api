// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.Getter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

@Getter
public class CustomRegisteredClient implements Serializable {
    private String id;
    private String clientId;
    private Instant clientIdIssuedAt;
    private String clientSecret;
    private Instant clientSecretExpiresAt;
    private String clientName;
    private Set<ClientAuthenticationMethod> clientAuthenticationMethods;
    private Set<AuthorizationGrantType> authorizationGrantTypes;
    private Set<String> redirectUris;
    private Set<String> postLogoutRedirectUris;
    private Set<String> scopes;
    private ClientSettings clientSettings;
    private TokenSettings tokenSettings;
    private Instant lastAccessTokenIssuedAt;

    CustomRegisteredClient() {
    }

    public static class Builder {
        private final RegisteredClient registeredClient;
        private Instant lastAccessTokenIssuedAt;

        protected Builder(RegisteredClient registeredClient) {
            this.registeredClient = registeredClient;
        }

        public static Builder from(RegisteredClient registeredClient) {
            return new Builder(registeredClient);
        }

        public Builder withLastAccessTokenIssuedAt(Instant lastAccessTokenIssuedAt) {
            this.lastAccessTokenIssuedAt = lastAccessTokenIssuedAt;
            return this;
        }

        public CustomRegisteredClient build() {
            CustomRegisteredClient client = new CustomRegisteredClient();
            client.id = registeredClient.getId();
            client.clientId = registeredClient.getClientId();
            client.clientIdIssuedAt = registeredClient.getClientIdIssuedAt();
            client.clientSecret = registeredClient.getClientSecret();
            client.clientSecretExpiresAt = registeredClient.getClientSecretExpiresAt();
            client.clientName = registeredClient.getClientName();
            client.clientAuthenticationMethods = registeredClient.getClientAuthenticationMethods();
            client.authorizationGrantTypes = registeredClient.getAuthorizationGrantTypes();
            client.redirectUris = registeredClient.getRedirectUris();
            client.postLogoutRedirectUris = registeredClient.getPostLogoutRedirectUris();
            client.scopes = registeredClient.getScopes();
            client.clientSettings = registeredClient.getClientSettings();
            client.tokenSettings = registeredClient.getTokenSettings();
            // custom attributes
            client.lastAccessTokenIssuedAt = lastAccessTokenIssuedAt;
            return client;
        }
    }
}
