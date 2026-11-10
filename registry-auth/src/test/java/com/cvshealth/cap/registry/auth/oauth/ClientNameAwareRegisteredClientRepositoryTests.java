// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientNameAwareRegisteredClientRepositoryTests {

    @Mock
    private CustomJdbcRegisteredClientRepository delegate;

    private ClientNameAwareRegisteredClientRepository repository;

    @BeforeEach
    public void setUp() {
        repository = new ClientNameAwareRegisteredClientRepository(delegate);
    }

    @Test
    public void findByClientId_resolvesByClientIdOrName() {
        // given
        RegisteredClient versioned = client("MyClient-v2", "MyClient");
        when(delegate.findByClientIdOrName("MyClient")).thenReturn(versioned);

        // when
        RegisteredClient result = repository.findByClientId("MyClient");

        // then
        assertThat(result).isSameAs(versioned);
    }

    @Test
    public void findByClientId_returnsNullWhenUnknown() {
        // given
        when(delegate.findByClientIdOrName("Unknown")).thenReturn(null);

        // when / then
        assertThat(repository.findByClientId("Unknown")).isNull();
    }

    @Test
    public void findById_delegates() {
        // given
        RegisteredClient client = client("MyClient", "MyClient");
        when(delegate.findById("id-1")).thenReturn(client);

        // when / then
        assertThat(repository.findById("id-1")).isSameAs(client);
    }

    @Test
    public void save_delegates() {
        // given
        RegisteredClient client = client("MyClient", "MyClient");

        // when
        repository.save(client);

        // then
        verify(delegate).save(client);
    }

    private static RegisteredClient client(String clientId, String clientName) {
        return RegisteredClient.withId(clientId + "-id")
                .clientId(clientId)
                .clientName(clientName)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/oauth-callback")
                .build();
    }
}
