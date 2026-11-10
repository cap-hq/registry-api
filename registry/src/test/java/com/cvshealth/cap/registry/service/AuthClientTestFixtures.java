// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.persistent.AuthClientViewEntity;
import com.cvshealth.cap.registry.persistent.TeamAuthClientViewEntity;
import com.cvshealth.cap.registry.persistent.TeamAuthClientViewId;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import io.leangen.graphql.execution.ResolutionEnvironment;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

final class AuthClientTestFixtures {

    private AuthClientTestFixtures() {
    }

    static AuthClientViewEntity authClient(String clientId, int clientVersion) {
        return AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build();
    }

    static TeamViewEntity teamWithClients(AuthClientViewEntity... authClients) {
        List<TeamAuthClientViewEntity> teamAuthClients = Arrays.stream(authClients)
                .map(authClient -> TeamAuthClientViewEntity.builder()
                        .id(TeamAuthClientViewId.builder().authClient(authClient).build())
                        .build())
                .collect(Collectors.toList());
        return TeamViewEntity.builder().teamAuthClients(teamAuthClients).build();
    }

    // verifies the auth service was asked for clients and returns the ids its supplier produces
    static Collection<String> capturedClientIds(RegistryAuthService registryAuthService, ResolutionEnvironment graphQLEnv) {
        ArgumentCaptor<Supplier<? extends Collection<String>>> captor = ArgumentCaptor.captor();
        verify(registryAuthService).getClientsForIds(captor.capture(), eq(graphQLEnv));
        return captor.getValue().get();
    }
}
