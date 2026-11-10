// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.config.AccessTokenConfig;
import com.cvshealth.cap.registry.auth.config.RefreshTokenConfig;
import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.model.*;
import com.cvshealth.cap.registry.auth.model.exception.BadRequestException;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.auth.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcRegisteredClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClientService {
    private final RegisteredClientRepository registeredClientRepository;
    private final ClientServiceValidator validator;
    private final PasswordEncoder encoder;
    private final RandomPasswordGenerator passwordGenerator;
    private final RegisteredClientConfig registeredClientConfig;
    private final List<CustomScope> customAuthorizationScopes;
    private final List<CustomScope> customUserConsentScopes;
    private final AccessTokenConfig accessTokenConfig;
    private final RefreshTokenConfig refreshTokenConfig;

    public ClientService(RegisteredClientRepository registeredClientRepository, ClientServiceValidator validator,
                         RegisteredClientConfig registeredClientConfig, List<CustomScope> customAuthorizationScopes,
                         List<CustomScope> customUserConsentScopes, AccessTokenConfig accessTokenConfig,
                         RefreshTokenConfig refreshTokenConfig) {
        this.registeredClientRepository = registeredClientRepository;
        this.validator = validator;
        this.encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        this.passwordGenerator = new RandomPasswordGenerator(registeredClientConfig.getClientSecretLength());
        this.registeredClientConfig = registeredClientConfig;
        this.customAuthorizationScopes = customAuthorizationScopes;
        this.customUserConsentScopes = customUserConsentScopes;
        this.accessTokenConfig = accessTokenConfig;
        this.refreshTokenConfig = refreshTokenConfig;
    }

    public int getClientVersion(Client client) {
        if (registeredClientConfig.isAllowMultipleSecretsPerClient() &&
                client.getClientId().startsWith(client.getClientName())) {
            String suffix = client.getClientId().substring(client.getClientName().length());
            if (suffix.matches("-v\\d+")) {
                int version = Integer.parseInt(suffix.substring(2));
                if (version > 1) {
                    return version;
                }
            }
        }
        return 1;
    }

    public String getBaseClientId(Client client) {
        if (registeredClientConfig.isAllowMultipleSecretsPerClient() && client.getClientId().startsWith(client.getClientName())) {
            return client.getClientName();
        }
        return client.getClientId();
    }

    public List<Client> getClients() {
        List<CustomRegisteredClient> registeredClients = getJdbcRegisteredClientRepository().findAllCustomRegisteredClients();
        return registeredClients.stream()
                .map(this::toClient)
                .collect(Collectors.toList());
    }

    public List<Client> getClientsByType(ClientType type) {
        log.debug("Fetching clients by type: {}", type);
        return getJdbcRegisteredClientRepository().findCustomRegisteredClientsByType(type).stream()
                .map(this::toClient)
                .collect(Collectors.toList());
    }

    public List<Client> getClientsByIds(List<String> clientIds) {
        log.debug("Fetching clients by IDs: {}", clientIds);
        if (CollectionUtils.isEmpty(clientIds)) {
            return List.of();
        }
        int maxClientIds = registeredClientConfig.getMaxSearchClientIds();
        if (clientIds.size() > maxClientIds) {
            throw new BadRequestException(ExceptionReason.CLIENT_IDS_OVER_MAX,
                    String.format(ExceptionReason.CLIENT_IDS_OVER_MAX.label, maxClientIds));
        }
        return getJdbcRegisteredClientRepository().findCustomRegisteredClientsByClientIds(clientIds).stream()
                .map(this::toClient)
                .collect(Collectors.toList());
    }

    public Client getClient(String clientId) {
        CustomRegisteredClient registeredClient = getJdbcRegisteredClientRepository().findCustomRegisteredClient(clientId);
        if (registeredClient != null) {
            return toClient(registeredClient);
        }
        throw new RecordNotFoundException(ExceptionReason.CLIENT_NOT_FOUND);
    }

    public List<ClientSecretExpiration> findAllClientSecretExpirations() {
        return getJdbcRegisteredClientRepository().findAllClientSecretExpirations();
    }

    public List<Client> getClientsByIdOrName(String clientIdOrName) {
        return getJdbcRegisteredClientRepository().findAllByClientIdOrName(clientIdOrName).stream()
                .map(rc -> toClient(CustomRegisteredClient.Builder.from(rc).build()))
                .collect(Collectors.toList());
    }

    public Client createClient(ClientRequest request) {
        validator.validate(request);
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(request.getClientId());
        if (registeredClient != null) {
            throw new BadRequestException(ExceptionReason.CLIENT_DUPLICATE_FOUND);
        }
        // registered client defaults
        RegisteredClient.Builder registeredClientBuilder = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId(request.getClientId())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(ClientType.USER_PKCE.equals(request.getType()))
                        .build());
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
        // redirect urls
        if (request.getRedirectUrls() != null && !request.getRedirectUrls().isEmpty()) {
            registeredClientBuilder.redirectUris(r -> r.addAll(request.getRedirectUrls()));
        }
        // set registered client auth/grant/secret
        String clientSecret = null;
        if (ClientType.USER_PKCE.equals(request.getType())) {
            registeredClientBuilder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
            registeredClientBuilder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            registeredClientBuilder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        } else if (ClientType.USER.equals(request.getType())) {
            clientSecret = passwordGenerator.generatePassword();
            registeredClientBuilder.clientSecret(encoder.encode(clientSecret));
            registeredClientBuilder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            registeredClientBuilder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            registeredClientBuilder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
            if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
                multipleSecretsPerClient(request, registeredClientBuilder);
            }
        } else if (ClientType.NON_USER.equals(request.getType())) {
            clientSecret = passwordGenerator.generatePassword();
            registeredClientBuilder.clientSecret(encoder.encode(clientSecret));
            registeredClientBuilder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            registeredClientBuilder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
            if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
                multipleSecretsPerClient(request, registeredClientBuilder);
            }
        }
        // token settings
        registeredClientBuilder.tokenSettings(configTokenSettings(TokenSettings.builder(), request));
        // save registered client
        registeredClientRepository.save(registeredClientBuilder.build());
        Client client = getClient(request.getClientId());
        // include client secret on response
        client.setClientSecret(clientSecret);
        return client;
    }

    public Client updateClient(ClientRequest request) {
        validator.validate(request);
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(request.getClientId());
        if (registeredClient != null) {
            validator.validateUpdate(registeredClient, request);
            registeredClientRepository.save(updateRegisteredClient(registeredClient, request));
            return getClient(request.getClientId());
        }
        throw new RecordNotFoundException(ExceptionReason.CLIENT_NOT_FOUND);
    }

    @Transactional
    public Client resetClientSecret(String clientId) {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient != null) {
            String clientSecret = passwordGenerator.generatePassword();
            registeredClientRepository.save(RegisteredClient.from(registeredClient)
                    .clientSecret(encoder.encode(clientSecret)).build());
            if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
                // Reset client issued/expires (not supported by default repository.save)
                Instant expiresAt = registeredClientConfig.getDefaultExpireDays() > 0 ? defaultClientSecretExpiration(): null;
                getJdbcRegisteredClientRepository().resetClientIssuedAt(clientId, expiresAt);
            }
            Client client = getClient(clientId);
            client.setClientSecret(clientSecret);
            return client;
        }
        throw new RecordNotFoundException(ExceptionReason.CLIENT_NOT_FOUND);
    }

    public void deleteClient(String clientId) {
        getJdbcRegisteredClientRepository().deleteByClientClientId(clientId);
    }

    private TokenSettings configTokenSettings(TokenSettings.Builder tokenSettingsBuilder,
                                              ClientRequest request) {
        long accessTokenExpireMinutes = (request.getAccessTokenExpireMinutes() != null)
                ? request.getAccessTokenExpireMinutes() : accessTokenConfig.getDefaultExpireMinutes();
        long refreshTokenExpireMinutes = (request.getRefreshTokenExpireMinutes() != null)
                ? request.getRefreshTokenExpireMinutes() : refreshTokenConfig.getDefaultExpireMinutes();

        tokenSettingsBuilder.accessTokenTimeToLive(Duration.ofMinutes(accessTokenExpireMinutes));
        if (request.getType().equals(ClientType.USER) || request.getType().equals(ClientType.USER_PKCE)) {
            tokenSettingsBuilder.refreshTokenTimeToLive(Duration.ofMinutes(refreshTokenExpireMinutes));
            tokenSettingsBuilder.reuseRefreshTokens(false);
        }
        return tokenSettingsBuilder.build();
    }

    private RegisteredClient updateRegisteredClient(RegisteredClient registeredClient, ClientRequest request) {
        // update client registration fields
        RegisteredClient.Builder registeredClientBuilder = RegisteredClient.from(registeredClient);
        // token settings
        TokenSettings.Builder tokenSettingsBuilder =
                TokenSettings.withSettings(registeredClient.getTokenSettings().getSettings());
        registeredClientBuilder.tokenSettings(configTokenSettings(tokenSettingsBuilder, request));
        // redirect urls
        if (request.getRedirectUrls() != null && !request.getRedirectUrls().isEmpty()) {
            registeredClientBuilder.redirectUris(Set::clear);
            registeredClientBuilder.redirectUris(r -> r.addAll(request.getRedirectUrls()));
        }
        if (registeredClientConfig.isAllowMultipleSecretsPerClient() && validator.isClientSecretRequired(request.getType())) {
            registeredClientBuilder.clientName(request.getClientName());
            if (request.getClientSecretExpiresAt() != null) {
                registeredClientBuilder.clientSecretExpiresAt(request.getClientSecretExpiresAt());
            } else if (registeredClient.getClientSecretExpiresAt() == null && registeredClientConfig.getDefaultExpireDays() > 0) {
                Instant expiresAt = defaultClientSecretExpiration(registeredClient.getClientIdIssuedAt());
                Instant extensionMaxExpiresAt = Instant.now().plus(registeredClientConfig.getMissingExpireExtensionDays(), ChronoUnit.DAYS);
                if (extensionMaxExpiresAt.compareTo(expiresAt) > 0) {
                    expiresAt = extensionMaxExpiresAt;
                }
                registeredClientBuilder.clientSecretExpiresAt(expiresAt);
            }
        }
        return registeredClientBuilder.build();
    }

    private Client toClient(CustomRegisteredClient registeredClient) {
        Client client = Client.builder()
                .id(registeredClient.getId())
                .clientId(registeredClient.getClientId())
                .type(getType(registeredClient))
                .clientSecret(StringUtils.isEmpty(registeredClient.getClientSecret()) ? null : "******")
                .redirectUrls(getType(registeredClient) == ClientType.NON_USER
                        ? null : registeredClient.getRedirectUris().stream().toList())
                .accessTokenExpireMinutes(registeredClient.getTokenSettings().getAccessTokenTimeToLive() != null
                        ? registeredClient.getTokenSettings().getAccessTokenTimeToLive().toMinutes() : null)
                .refreshTokenExpireMinutes(getType(registeredClient) != ClientType.NON_USER
                        && registeredClient.getTokenSettings().getRefreshTokenTimeToLive() != null
                        ? registeredClient.getTokenSettings().getRefreshTokenTimeToLive().toMinutes() : null).build();
        if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
            client.setClientName(registeredClient.getClientName());
            client.setClientSecretExpiresAt(registeredClient.getClientSecretExpiresAt());
        }
        client.setLastAccessTokenIssuedAt(registeredClient.getLastAccessTokenIssuedAt());
        return client;
    }

    private ClientType getType(CustomRegisteredClient registeredClient) {
        if (registeredClient == null) {
            return null;
        }
        return ClientType.from(registeredClient.getAuthorizationGrantTypes(), registeredClient.getClientSettings());
    }

    private CustomJdbcRegisteredClientRepository getJdbcRegisteredClientRepository() {
        return ((CustomJdbcRegisteredClientRepository)registeredClientRepository);
    }

    private void multipleSecretsPerClient(ClientRequest request, RegisteredClient.Builder registeredClientBuilder) {
        registeredClientBuilder.clientName(request.getClientName());
        Instant secretExpiration = request.getClientSecretExpiresAt();
        if (secretExpiration == null && registeredClientConfig.getDefaultExpireDays() > 0) {
            secretExpiration = defaultClientSecretExpiration();
        }
        registeredClientBuilder.clientSecretExpiresAt(secretExpiration);
    }

    private Instant defaultClientSecretExpiration() {
        return defaultClientSecretExpiration(Instant.now());
    }

    private Instant defaultClientSecretExpiration(Instant issuedAt) {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
        return issuedAt.plus(registeredClientConfig.getDefaultExpireDays(), ChronoUnit.DAYS);
    }
}
