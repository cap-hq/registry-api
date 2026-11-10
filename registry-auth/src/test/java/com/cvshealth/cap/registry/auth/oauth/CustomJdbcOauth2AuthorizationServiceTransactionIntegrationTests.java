// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * Kept separate from {@link OAuth2AuthorizationServiceIntegrationTests} because the spied
 * {@code authJdbcTemplate} forces its own application context.
 */
public class CustomJdbcOauth2AuthorizationServiceTransactionIntegrationTests extends RegistryIntegrationTests {

    @MockitoSpyBean(name = "authJdbcTemplate")
    JdbcTemplate authJdbcTemplate;

    @Autowired
    OAuth2AuthorizationService authorizationService;

    @Autowired
    RegisteredClientRepository registeredClientRepository;

    @Test
    public void save_failureAfterInsert_rollsBackWholeSave() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        UUID.randomUUID().toString(), issuedAt, issuedAt.plus(1, ChronoUnit.HOURS)))
                .token(new OAuth2RefreshToken(UUID.randomUUID().toString(),
                        issuedAt, issuedAt.plus(48, ChronoUnit.HOURS)))
                .build();

        // Fail the last statement of save() (the refresh-token cap), after the row has been inserted.
        doThrow(new DataAccessResourceFailureException("simulated crash"))
                .when(authJdbcTemplate).update(argThat((String sql) -> sql != null && sql.contains("LEAST(")), any(Object[].class));

        assertThatThrownBy(() -> authorizationService.save(authorization))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(authorizationService.findById(authorization.getId())).isNull();
    }
}
