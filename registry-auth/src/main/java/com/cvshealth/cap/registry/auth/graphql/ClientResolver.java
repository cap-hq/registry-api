// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.graphql;

import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.ClientSecretExpiration;
import com.cvshealth.cap.registry.auth.service.ClientService;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@GraphQLApi
@AllArgsConstructor
public class ClientResolver {

    private final ClientService clientService;

    @GraphQLQuery(name = "getClients", description = "fetches all registered clients. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public List<Client> getClients() {
        return clientService.getClients();
    }

    @GraphQLQuery(name = "getClientById", description = "fetches a registered client by client ID. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public Client getClientById(@GraphQLNonNull String clientId) {
        return clientService.getClient(clientId);
    }

    @GraphQLQuery(name = "getClientsByIdOrName", description = "fetches registered clients matching the given client ID or client name. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public List<Client> getClientsByIdOrName(@GraphQLNonNull String clientIdOrName) {
        return clientService.getClientsByIdOrName(clientIdOrName);
    }

    @GraphQLQuery(name = "getClientSecretExpirations", description = "fetches secret expiration details for all clients using client_secret_basic authentication. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public List<ClientSecretExpiration> getClientSecretExpirations() {
        return clientService.findAllClientSecretExpirations();
    }
}
