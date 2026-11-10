// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.cvshealth.cap.registry.auth.model.ClientSecretExpiration;
import com.cvshealth.cap.registry.auth.model.CustomRegisteredClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomJdbcRegisteredClientRepositoryTests extends RegistryIntegrationTests {

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    private CustomJdbcRegisteredClientRepository getCustomerRepository() {
        return (CustomJdbcRegisteredClientRepository) registeredClientRepository;
    }

    @Test
    public void findAllCustomRegisteredClients() {
        // when
        List<CustomRegisteredClient> clients = getCustomerRepository().findAllCustomRegisteredClients();

        // then
        assertThat(clients.size()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void findCustomRegisteredClient() {
        // given
        String clientId = "CapUIClient";

        // when
        CustomRegisteredClient client = getCustomerRepository().findCustomRegisteredClient(clientId);

        // then
        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo(clientId);
    }

    @Test
    public void findAllByClientIdOrName() {
        // given
        String clientId = "CapUIClient";

        // when
        List<RegisteredClient> clients = getCustomerRepository().findAllByClientIdOrName(clientId);

        // then
        assertThat(clients.size()).isEqualTo(1);

        // given the client name
        RegisteredClient registeredClient = clients.get(0);
        String clientName = registeredClient.getClientName();

        // when
        clients = getCustomerRepository().findAllByClientIdOrName(clientName);

        // then
        assertThat(clients.size()).isEqualTo(1);
        assertThat(clients.get(0).getClientId()).isEqualTo(registeredClient.getClientId());
    }

    @Test
    public void findByClientIdOrName_prefersExactClientId() {
        // given: TestUIClient (exact id) and TestUIClient-v2 (client_name = TestUIClient) both exist
        RegisteredClient client = getCustomerRepository().findByClientIdOrName("TestUIClient");

        // then
        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("TestUIClient");
    }

    @Test
    public void findByClientIdOrName_fallsBackToClientName() {
        // given: only a versioned row exists for this name
        String clientName = "RotatedRepoClient";
        RegisteredClient versioned = RegisteredClient.from(getCustomerRepository().findByClientIdOrName("TestUIClient-v2"))
                .id("rotated-repo-client-v2")
                .clientId(clientName + "-v2")
                .clientName(clientName)
                .clientSecret("{noop}rotated-repo-client-v2-secret")
                .build();
        getCustomerRepository().save(versioned);

        // when
        RegisteredClient client = getCustomerRepository().findByClientIdOrName(clientName);

        // then
        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo(clientName + "-v2");
    }

    @Test
    public void findByClientIdOrName_returnsNullWhenUnknown() {
        assertThat(getCustomerRepository().findByClientIdOrName("no-such-client")).isNull();
    }

    @Test
    public void resetClientIssuedAt() {
        // given
        String clientId = "CapUIClient";
        List<RegisteredClient> clients = getCustomerRepository().findAllByClientIdOrName(clientId);
        assertThat(clients.size()).isEqualTo(1);
        RegisteredClient registeredClient = clients.get(0);

        // when
        getCustomerRepository().resetClientIssuedAt(clientId, Instant.now().plus(1, ChronoUnit.DAYS));

        // then
        clients = getCustomerRepository().findAllByClientIdOrName(clientId);
        assertThat(clients.size()).isEqualTo(1);
        RegisteredClient updatedRegisteredClient = clients.get(0);

        assertThat(updatedRegisteredClient.getClientIdIssuedAt()).isAfter(registeredClient.getClientIdIssuedAt());
        assertThat(updatedRegisteredClient.getClientSecretExpiresAt()).isNotNull();
        assertThat(updatedRegisteredClient.getClientSecretExpiresAt()).isAfter(registeredClient.getClientIdIssuedAt());
    }

    @Test
    public void findAllClientSecretExpirations() {
        // when
        List<ClientSecretExpiration> result = getCustomerRepository().findAllClientSecretExpirations();

        // then
        assertThat(result.size()).isGreaterThan(0);
    }

    @Test
    public void updateLastAccessTokenIssuedAt() {
        // given
        String clientId = "CapUIClient";
        CustomRegisteredClient client = getCustomerRepository().findCustomRegisteredClient(clientId);
        assertThat(client.getLastAccessTokenIssuedAt()).isNull();

        // when
        getCustomerRepository().updateLastAccessTokenIssuedAt(clientId);

        // then
        client = getCustomerRepository().findCustomRegisteredClient(clientId);
        assertThat(client.getLastAccessTokenIssuedAt()).isNotNull();
    }
}
