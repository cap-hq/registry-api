// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.AuthClient;
import com.cvshealth.cap.registry.model.AuthClientRequest;
import com.cvshealth.cap.registry.model.AuthClientSearchFilter;
import com.cvshealth.cap.registry.model.AuthClientView;
import com.cvshealth.cap.registry.model.PaginatedAuthClientResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.AuthClientService;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@GraphQLApi
@AllArgsConstructor
public class AuthClientResolver {
    private final AuthClientService authClientService;
    private final RegistryService registryService;

    @GraphQLQuery(name = "getAuthClients", description = "fetches all registry auth clients")
    public PaginatedAuthClientResponse getAuthClients(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                      @Valid AuthClientSearchFilter filter,
                                                      @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return authClientService.getAuthClients(paginationInput, filter, graphQLEnv);
    }

    @GraphQLQuery(name = "getAuthClient", description = "fetches registry auth client")
    public AuthClientView getAuthClient(@GraphQLNonNull String clientId,
                                        Integer clientVersion,
                                        @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return authClientService.getAuthClient(clientId, clientVersion, graphQLEnv);
    }

    @GraphQLMutation(name = "createAuthClient", description = "creates registry auth client")
    @PreAuthorize("hasPermission(#authClient, 'AuthClient', 'create')")
    public AuthClient createAuthClient(@GraphQLNonNull AuthClientRequest authClient) {
        return registryService.createAuthClient(authClient);
    }

    @GraphQLMutation(name = "resetAuthClientSecret", description = "creates registry auth client")
    @PreAuthorize("hasPermission(#clientId, 'AuthClient', 'update')")
    public AuthClient resetAuthClientSecret(@GraphQLNonNull String clientId, Integer clientVersion) {
        return authClientService.resetAuthClientSecret(clientId, clientVersion);
    }

    @GraphQLMutation(name = "updateAuthClient", description = "updates registry auth client")
    @PreAuthorize("hasPermission(#authClient, 'AuthClient', 'update')")
    public AuthClient updateAuthClient(@GraphQLNonNull AuthClientRequest authClient) {
        return authClientService.updateAuthClient(authClient);
    }

    @GraphQLMutation(name = "deleteAuthClient", description = "deletes registry auth client")
    @PreAuthorize("hasPermission(#clientId, 'AuthClient', 'delete')")
    public void deleteAuthClient(@GraphQLNonNull String clientId, Integer clientVersion) {
        registryService.deleteAuthClient(clientId, clientVersion);
    }
}