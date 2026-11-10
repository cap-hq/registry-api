// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.ClientRequest;
import com.cvshealth.cap.registry.auth.model.ClientSecretExpiration;
import com.cvshealth.cap.registry.auth.model.ClientType;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ClientServiceTests extends RegistryIntegrationTests {

    private static final String CAP_UI_CLIENT = "CapUIClient";
    private static final String API_NON_USER_CLIENT = "apiplatform";
    private static final String AUTH_CODE_FLOW = "auth-code-flow";

    @Autowired
    private ClientService clientService;

    @Autowired
    private RegisteredClientConfig registeredClientConfig;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @BeforeEach
    public void setUp() {
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
    }

    @Test
    public void getClients() {
        // when
        List<Client> clients = clientService.getClients();

        // then
        assertThat(clients.size()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void getClient() {
        // when
        Client client = clientService.getClient(CAP_UI_CLIENT);

        // then
        assertThat(client).isNotNull();
    }

    @Test
    public void getClientsByIdOrName_byClientId_returnsMatch() {
        // when
        List<Client> results = clientService.getClientsByIdOrName(CAP_UI_CLIENT);

        // then
        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(c -> CAP_UI_CLIENT.equals(c.getClientId()))).isTrue();
    }

    @Test
    public void getClientsByIdOrName_noMatch_returnsEmpty() {
        // when
        List<Client> results = clientService.getClientsByIdOrName("zzz-no-match-zzz");

        // then
        assertThat(results).isEmpty();
    }

    @Test
    public void createClient_nonUser_AllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_non_user_2")
                .clientName("cap_non_user")
                .type(ClientType.NON_USER)
                .clientSecretExpiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(Duration.between(result.getClientSecretExpiresAt(), request.getClientSecretExpiresAt()).toSeconds()).isLessThan(1);
    }

    @Test
    public void createClient_nonUser_AllowMultipleSecretsPerClient_noExpiresAt() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        registeredClientConfig.setDefaultExpireDays(0);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_non_user_3")
                .clientName("cap_non_user")
                .type(ClientType.NON_USER)
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(result.getClientSecretExpiresAt()).isNull();
    }

    @Test
    public void createClient_nonUser_AllowMultipleSecretsPerClient_defaultExpiresAt() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        registeredClientConfig.setDefaultExpireDays(90);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_non_user_4")
                .clientName("cap_non_user")
                .type(ClientType.NON_USER)
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(result.getClientSecretExpiresAt()).isNotNull();
    }

    @Test
    public void createClient_nonUser_DoNotAllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_non_user_1")
                .type(ClientType.NON_USER)
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isNull();
        assertThat(result.getClientSecretExpiresAt()).isNull();
    }

    @Test
    public void createClient_user_AllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_user_2")
                .clientName("cap_user")
                .type(ClientType.USER)
                .clientSecretExpiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .redirectUrls(List.of("http://localhost:3000"))
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(Duration.between(result.getClientSecretExpiresAt(), request.getClientSecretExpiresAt()).toSeconds()).isLessThan(1);
        assertThat(result.getRedirectUrls()).isNotEmpty();
        assertThat(result.getAccessTokenExpireMinutes()).isNotNull();
        assertThat(result.getRefreshTokenExpireMinutes()).isNotNull();
    }

    @Test
    public void createClient_user_AllowMultipleSecretsPerClient_noExpiresAt() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        registeredClientConfig.setDefaultExpireDays(0);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_user_3")
                .clientName("cap_user")
                .type(ClientType.USER)
                .redirectUrls(List.of("http://localhost:3000"))
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(result.getClientSecretExpiresAt()).isNull();
        assertThat(result.getRedirectUrls()).isNotEmpty();
        assertThat(result.getAccessTokenExpireMinutes()).isNotNull();
        assertThat(result.getRefreshTokenExpireMinutes()).isNotNull();
    }

    @Test
    public void createClient_user_AllowMultipleSecretsPerClient_defaultExpiresAt() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        registeredClientConfig.setDefaultExpireDays(90);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_user_4")
                .clientName("cap_user")
                .type(ClientType.USER)
                .redirectUrls(List.of("http://localhost:3000"))
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(result.getClientSecretExpiresAt()).isNotNull();
        assertThat(result.getRedirectUrls()).isNotEmpty();
        assertThat(result.getAccessTokenExpireMinutes()).isNotNull();
        assertThat(result.getRefreshTokenExpireMinutes()).isNotNull();
    }

    @Test
    public void createClient_user_DoNotAllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
        ClientRequest request = ClientRequest.builder()
                .clientId("cap_user_1")
                .type(ClientType.USER)
                .redirectUrls(List.of("http://localhost:3000"))
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isNull();
        assertThat(result.getClientSecretExpiresAt()).isNull();
        assertThat(result.getRedirectUrls()).isNotEmpty();
        assertThat(result.getAccessTokenExpireMinutes()).isNotNull();
        assertThat(result.getRefreshTokenExpireMinutes()).isNotNull();
    }

    @Test
    public void resetClientSecret_DoNotAllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);

        // when
        Client result = clientService.resetClientSecret(CAP_UI_CLIENT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientSecretExpiresAt()).isNull();
    }

    @Test
    public void resetClientSecret_AllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);

        // when
        Client result = clientService.resetClientSecret(CAP_UI_CLIENT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientSecretExpiresAt()).isNotNull();
    }

    @Test
    public void updateClient_nonUser_AllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        ClientRequest request = ClientRequest.builder()
                .clientId(API_NON_USER_CLIENT)
                .type(ClientType.NON_USER)
                .clientName(API_NON_USER_CLIENT)
                .clientSecretExpiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        // when
        Client result = clientService.updateClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(Duration.between(result.getClientSecretExpiresAt(), request.getClientSecretExpiresAt()).toSeconds()).isLessThan(1);
    }

    @Test
    public void updateClient_nonUser_AllowMultipleSecretsPerClient_noExpiresAt() {
        // given a previously created legacy NON_USER cliewnt
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
        ClientRequest request = ClientRequest.builder()
                .clientId("update_non_user_1")
                .type(ClientType.NON_USER)
                .build();
        Client result = clientService.createClient(request);
        assertThat(result).isNotNull();

        // given multiple secrets per client are allowed, update the client with a client name
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        registeredClientConfig.setDefaultExpireDays(0);
        request.setClientName("update_non_user");

        // when
        Client updateResult = clientService.updateClient(request);

        // then
        assertThat(updateResult).isNotNull();
        assertThat(updateResult.getType()).isEqualTo(request.getType());
        assertThat(updateResult.getId()).isNotNull();
        assertThat(updateResult.getClientId()).isEqualTo(request.getClientId());
        assertThat(updateResult.getClientSecret()).isNotNull();
        assertThat(updateResult.getClientName()).isEqualTo(request.getClientName());
        assertThat(updateResult.getClientSecretExpiresAt()).isNull();
    }

    @Test
    public void updateClient_nonUser_AllowMultipleSecretsPerClient_defaultExpiresAt() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        registeredClientConfig.setDefaultExpireDays(90);
        ClientRequest request = ClientRequest.builder()
                .clientId(API_NON_USER_CLIENT)
                .type(ClientType.NON_USER)
                .clientName(API_NON_USER_CLIENT)
                .build();

        // when
        Client result = clientService.updateClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isEqualTo(request.getClientName());
        assertThat(result.getClientSecretExpiresAt()).isNotNull();
    }

    @Test
    public void updateClient_nonUser_DoNotAllowMultipleSecretsPerClient() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
        ClientRequest request = ClientRequest.builder()
                .clientId(API_NON_USER_CLIENT)
                .type(ClientType.NON_USER)
                .build();

        // when
        Client result = clientService.updateClient(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(request.getType());
        assertThat(result.getId()).isNotNull();
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
        assertThat(result.getClientSecret()).isNotNull();
        assertThat(result.getClientName()).isNull();
        assertThat(result.getClientSecretExpiresAt()).isNull();
    }

    @Test
    public void findAllClientSecretExpirations() {
        //when
        List<ClientSecretExpiration> clientSecretExpirations = clientService.findAllClientSecretExpirations();

        // then
        assertThat(clientSecretExpirations).isNotEmpty();
        Optional<ClientSecretExpiration> result = clientSecretExpirations.stream().filter(c -> AUTH_CODE_FLOW.equals(c.getClientId())).findFirst();
        assertThat(result.isPresent()).isTrue();
        ClientSecretExpiration client = result.get();
        assertThat(client.getClientId()).isEqualTo(AUTH_CODE_FLOW);
        assertThat(client.getClientName()).isNotNull();
        assertThat(client.getClientIdIssuedAt()).isNotNull();
        assertThat(client.getClientSecretExpiresAt()).isNotNull();
        assertThat(client.getAgeInDays()).isEqualTo(10);
        assertThat(client.getExpirationInDays()).isEqualTo(80 - 1); // test accounts for auth client creation few milliseconds prior to SQL now date diff check
    }

    @Test
    public void createClient() {
        // given
        String clientId = "client-1";
        ClientRequest request = ClientRequest.builder()
                .type(ClientType.NON_USER)
                .clientId(clientId)
                .clientName(clientId)
                .build();

        // when
        Client result = clientService.createClient(request);

        // then
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        assertThat(registeredClient).isNotNull();
        assertThat(registeredClient.getScopes()).containsAll(
                Set.of(
                    OidcScopes.OPENID,
                    OidcScopes.PROFILE,
                    OidcScopes.EMAIL,
                    "App.User",
                    "App.Registry",
                    "App.AppReg",
                    "App.OPA"
                ));
    }

    @Test
    public void clientIdVersion_multipleSecretsPerClient_not_allowed() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
        Client client = mock(Client.class);

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
        verifyNoInteractions(client);
    }

    @Test
    public void getClientVersion_v1() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");
        when(client.getClientName()).thenReturn("C123456");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
    }

    @Test
    public void getClientVersion_v2() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id-v2");
        when(client.getClientName()).thenReturn("my-client-id");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(2);
    }

    @Test
    public void getClientVersion_badVersion() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-service-validator");
        when(client.getClientName()).thenReturn("my-client-service-validator");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
    }

    @Test
    public void getClientVersion_clientIdDoesNotStartWithClientName() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("e08f8bf6-a627-498d-8ac2-af82f6e01508");
        when(client.getClientName()).thenReturn("grafanaSync");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
    }

    @Test
    public void getClientVersion_clientIdEqualsClientName() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("cap-validator");
        when(client.getClientName()).thenReturn("cap-validator");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
    }

    @Test
    public void getClientVersion_capValidatorV2() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("cap-validator-v2");
        when(client.getClientName()).thenReturn("cap-validator");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(2);
    }

    @Test
    public void getClientVersion_exampleRefreshTestNoSuffix() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-test");
        when(client.getClientName()).thenReturn("my-client-test");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
    }

    @Test
    public void getClientVersion_exampleRefreshTestV2() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-test-v2");
        when(client.getClientName()).thenReturn("my-client-test");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(2);
    }

    @Test
    public void getClientVersion_exampleRefreshTest2NoSuffix() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-test-2");
        when(client.getClientName()).thenReturn("my-client-test-2");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(1);
    }

    @Test
    public void getClientVersion_exampleRefreshTest2V3() {
        // given
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-test-2-v3");
        when(client.getClientName()).thenReturn("my-client-test-2");

        // when
        int version = clientService.getClientVersion(client);

        // then
        AssertionsForClassTypes.assertThat(version).isEqualTo(3);
    }

    @Test
    public void getBaseClientId_multipleSecretsPerClient_not_allowed() {
        // given
        String clientId = "my-client-id";
        registeredClientConfig.setAllowMultipleSecretsPerClient(false);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(clientId);

        // when
        String result = clientService.getBaseClientId(client);

        // then
        AssertionsForClassTypes.assertThat(result).isEqualTo(clientId);
        verify(client).getClientId();
        verifyNoMoreInteractions(client);
    }

    @Test
    public void getBaseClientId_v1() {
        // given
        String clientId = "my-client-id";
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getClientName()).thenReturn(clientId);

        // when
        String result = clientService.getBaseClientId(client);

        // then
        AssertionsForClassTypes.assertThat(result).isEqualTo(clientId);
    }

    @Test
    public void getBaseClientId_v2() {
        // given
        String clientId = "my-client-id-v2";
        String clientName = "my-client-id";
        registeredClientConfig.setAllowMultipleSecretsPerClient(true);
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getClientName()).thenReturn(clientName);

        // when
        String result = clientService.getBaseClientId(client);

        // then
        AssertionsForClassTypes.assertThat(result).isEqualTo(clientName);
    }
}
