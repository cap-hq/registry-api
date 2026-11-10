// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import io.leangen.graphql.execution.ResolutionEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.authClient;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.teamWithClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class RegistryAuthServiceTests {

    private RegistryAuthService service;
    private final List<OAuthClient> clients = List.of(OAuthClient.builder().clientId("client-1").build());

    @BeforeEach
    void setUp() {
        service = spy(new RegistryAuthService(null, null));
    }

    @Test
    void getClientsForIds_whenAuthFieldsNotSelected_returnsNullWithoutEvaluatingIds() {
        Supplier<List<String>> ids = mock();

        assertThat(service.getClientsForIds(ids, null)).isNull();
        assertThat(service.getClientsForIds(ids, graphQLEnv("TeamView.name"))).isNull();

        verifyNoInteractions(ids);
        verify(service, never()).getClients();
        verify(service, never()).getClientsByIds(anyList());
    }

    @Test
    void getClientsForIds_whenNoIds_returnsEmptyWithoutCallingAuthApi() {
        assertThat(service.getClientsForIds(List::of, authFieldsEnv())).isEmpty();

        verify(service, never()).getClients();
        verify(service, never()).getClientsByIds(anyList());
    }

    @Test
    void getClientsForIds_whenWithinThreshold_searchesDistinctNonNullIds() {
        doReturn(clients).when(service).getClientsByIds(anyList());

        List<String> ids = new ArrayList<>(List.of("client-1", "client-1", "client-2"));
        ids.add(null);
        List<OAuthClient> result = service.getClientsForIds(() -> ids, authFieldsEnv());

        assertThat(result).isSameAs(clients);
        verify(service).getClientsByIds(List.of("client-1", "client-2"));
        verify(service, never()).getClients();
    }

    @Test
    void getClientsForIds_whenDistinctIdsAboveThreshold_fetchesAllClients() {
        doReturn(clients).when(service).getClients();
        List<String> ids = IntStream.rangeClosed(0, service.oauthClientFetchSize).mapToObj(i -> "client-" + i).toList();

        assertThat(service.getClientsForIds(() -> ids, authFieldsEnv())).isSameAs(clients);
        verify(service, never()).getClientsByIds(anyList());
    }

    @Test
    void getClientsForIds_whenDuplicatesExceedThresholdButDistinctDoNot_searchesByIds() {
        doReturn(clients).when(service).getClientsByIds(anyList());
        List<String> ids = Collections.nCopies(service.oauthClientFetchSize + 1, "client-1");

        service.getClientsForIds(() -> ids, authFieldsEnv());

        verify(service).getClientsByIds(List.of("client-1"));
        verify(service, never()).getClients();
    }

    @Test
    void getClientsForIds_whenConfiguredThresholdLowered_fetchesAllAboveThreshold() {
        doReturn(clients).when(service).getClients();
        service.oauthClientFetchSize = 5;
        List<String> ids = IntStream.rangeClosed(1, 6).mapToObj(i -> "client-" + i).toList();

        assertThat(service.getClientsForIds(() -> ids, authFieldsEnv())).isSameAs(clients);
        verify(service, never()).getClientsByIds(anyList());
    }

    @Test
    void getClientsForIds_whenConfiguredThresholdLowered_searchesByIdsAtThreshold() {
        doReturn(clients).when(service).getClientsByIds(anyList());
        service.oauthClientFetchSize = 5;
        List<String> ids = IntStream.rangeClosed(1, 5).mapToObj(i -> "client-" + i).toList();

        assertThat(service.getClientsForIds(() -> ids, authFieldsEnv())).isSameAs(clients);
        verify(service).getClientsByIds(ids);
        verify(service, never()).getClients();
    }

    @Test
    void teamAuthClientIds_buildsVersionedIdsAndHandlesMissingData() {
        assertThat(RegistryAuthService.teamAuthClientIds(null)).isEmpty();
        assertThat(RegistryAuthService.teamAuthClientIds(TeamViewEntity.builder().build())).isEmpty();
        assertThat(RegistryAuthService.teamAuthClientIds(teamWithClients(authClient("a", 1), authClient("a", 2))))
                .containsExactly("a", "a-v2");
    }

    private static ResolutionEnvironment authFieldsEnv() {
        return graphQLEnv("Query/teams/TeamView.authClients/TeamViewAuthClient.type");
    }

    private static ResolutionEnvironment graphQLEnv(String fullyQualifiedFieldName) {
        SelectedField field = mock(SelectedField.class);
        when(field.getFullyQualifiedName()).thenReturn(fullyQualifiedFieldName);
        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        when(selectionSet.getFields()).thenReturn(List.of(field));
        DataFetchingEnvironment dfe = mock(DataFetchingEnvironment.class);
        when(dfe.getSelectionSet()).thenReturn(selectionSet);
        return new ResolutionEnvironment(null, dfe, null, null, null, null);
    }
}
