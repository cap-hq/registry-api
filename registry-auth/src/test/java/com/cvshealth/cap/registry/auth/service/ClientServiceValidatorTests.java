// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.model.ClientRequest;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.model.exception.BadRequestException;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientServiceValidatorTests {

    RegisteredClientConfig singleSecretPerClientConfig = registeredClientConfig(false, 0, 0);
    RegisteredClientConfig multipleSecretPerClientConfig = registeredClientConfig(true, 120, 30);
    RegisteredClientConfig multipleSecretPerClientConfigNoDefaults = registeredClientConfig(true, 0, 0);

    @Test
    public void validate_nullRequest() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(singleSecretPerClientConfig);

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(null));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY.label);
    }

    @Test
    public void validate_nonUser_cannot_have_redirect_urls() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(singleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .redirectUrls(List.of("http://localhost:3000"))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_REDIRECT_NOT_ALLOWED.label);
    }

    @Test
    public void validate_redirect_url_blank_not_valid() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(singleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .redirectUrls(List.of("http://localhost:3000", " "))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_REDIRECT_NOT_VALID.label);
    }

    @Test
    public void validate_redirect_url_empty_not_valid() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(singleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .redirectUrls(List.of("http://localhost:3000", ""))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_REDIRECT_NOT_VALID.label);
    }

    @Test
    public void validate_redirect_url_null_not_valid() {
        // given
        List<String> urls = new ArrayList<>(2);
        urls.add("http://localhost:3000");
        urls.add(null);

        ClientServiceValidator validator = new ClientServiceValidator(singleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .redirectUrls(urls)
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_REDIRECT_NOT_VALID.label);
    }

    @Test
    public void validate_nonUser_cannot_have_refresh_tokens() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(singleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .refreshTokenExpireMinutes(30L)
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_REFRESH_NOT_ALLOWED.label);
    }

    @Test
    public void validate_user_pkce_clientSecretExpires_not_allowed() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER_PKCE)
                .clientSecretExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_NOT_ALLOWED.label);
    }

    @Test
    public void validate_user_clientSecretExpiresAt_cannot_before_now() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .clientSecretExpiresAt(Instant.now().plus(-30, ChronoUnit.DAYS))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_INVALID.label);
    }

    @Test
    public void validate_user_clientSecretExpiresAt_cannot_after_maxAllowed() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .clientSecretExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).isEqualTo(String.format(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX.label, multipleSecretPerClientConfig.getMaxExpireDays()));
    }

    @Test
    public void validate_user_clientSecretExpiresAt_isValid() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .clientSecretExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .build();

        // when
        ClientRequest result = validator.validate(request);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validate_user_clientSecretExpiresAt_isValidNoDefaults() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfigNoDefaults);
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.USER)
                .clientSecretExpiresAt(Instant.now().plus(1000, ChronoUnit.DAYS))
                .build();

        // when
        ClientRequest result = validator.validate(request);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_nonuser_clientSecretExpiresAt_isValid() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        RegisteredClient registeredClient = RegisteredClient.withId("123456")
                .clientId("non-user-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSecretExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS))
                .build();
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .clientId(registeredClient.getClientId())
                .clientSecretExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .build();

        // when
        ClientRequest result = validator.validateUpdate(registeredClient, request);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_nonuser_clientSecretExpiresAt_validExtensionForExistingClient() {
        // given registered client 85 days ago
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        RegisteredClient registeredClient = RegisteredClient.withId("123456")
                .clientId("non-user-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientIdIssuedAt(Instant.now().plus(-85, ChronoUnit.DAYS))
                .clientSecretExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();
        // extend
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .clientId(registeredClient.getClientId())
                .clientSecretExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS))
                .build();

        // when
        ClientRequest result = validator.validateUpdate(registeredClient, request);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_nonuser_clientSecretExpiresAt_validExtensionForExistingClientWithoutExpiration() {
        // given registered client 115 days ago without a current expiration.
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        RegisteredClient registeredClient = RegisteredClient.withId("123456")
                .clientId("non-user-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientIdIssuedAt(Instant.now().plus(-115, ChronoUnit.DAYS))
                .clientSecretExpiresAt(null)
                .build();
        // extend
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .clientId(registeredClient.getClientId())
                .clientSecretExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        // when new max expire is 120 days allow up to 30 day extension
        ClientRequest result = validator.validateUpdate(registeredClient, request);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_nonuser_clientSecretExpiresAt_existingClientWithoutExpiration_extension_cannot_be_over_max() {
        // given registered client 115 days ago without a current expiration.
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        RegisteredClient registeredClient = RegisteredClient.withId("123456")
                .clientId("non-user-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientIdIssuedAt(Instant.now().plus(-115, ChronoUnit.DAYS))
                .clientSecretExpiresAt(null)
                .build();
        // extend
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .clientId(registeredClient.getClientId())
                .clientSecretExpiresAt(Instant.now().plus(40, ChronoUnit.DAYS))
                .build();

        // when validate exception is thrown when extension is over max allowed
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validateUpdate(registeredClient, request));
        assertThat(ex.getMessage()).isEqualTo(String.format(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX.label, multipleSecretPerClientConfig.getMissingExpireExtensionDays()));

    }

    @Test
    public void validateUpdate_nonuser_clientSecretExpiresAt_cannot_after_maxAllowed() {
        // given
        ClientServiceValidator validator = new ClientServiceValidator(multipleSecretPerClientConfig);
        RegisteredClient registeredClient = RegisteredClient.withId("123456")
                .clientId("non-user-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSecretExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS))
                .build();
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .clientId(registeredClient.getClientId())
                .clientSecretExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .build();

        // when validate exception is thrown
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> validator.validateUpdate(registeredClient, request));
        assertThat(ex.getMessage()).isEqualTo(String.format(ExceptionReason.CLIENT_SECRET_EXPIRES_AT_OVER_MAX.label, multipleSecretPerClientConfig.getMaxExpireDays()));
    }



    private static RegisteredClientConfig registeredClientConfig(boolean allowMultipleSecretsPerClient,
                                                          long maxExpireDays,
                                                          long missingExpireExtensionDays) {
        RegisteredClientConfig registeredClientConfig = new RegisteredClientConfig();
        registeredClientConfig.setAllowMultipleSecretsPerClient(allowMultipleSecretsPerClient);
        registeredClientConfig.setMaxExpireDays(maxExpireDays);
        registeredClientConfig.setMissingExpireExtensionDays(missingExpireExtensionDays);
        return registeredClientConfig;
    }
}
