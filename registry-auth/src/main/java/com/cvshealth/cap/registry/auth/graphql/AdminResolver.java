// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.graphql;

import com.cvshealth.cap.registry.auth.job.AuthEventPartitionManager;
import com.cvshealth.cap.registry.auth.model.*;
import com.cvshealth.cap.registry.auth.oauth.JwksRepository;
import com.cvshealth.cap.registry.auth.service.AuthenticationEventService;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Component
@GraphQLApi
@AllArgsConstructor
public class AdminResolver {

    private final AuthenticationEventService authenticationEventService;
    private final JwksRepository jwksRepository;
    private final AuthEventPartitionManager authEventPartitionManager;

    @GraphQLQuery(name = "getJwks", description = "fetches all entries from the oauth2_jwks table. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public List<JwkPublicKey> getJwks() {
        return jwksRepository.findAll();
    }

    @GraphQLQuery(name = "getAuthenticationEvents", description = "fetches raw authentication events with pagination and optional filtering. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public PaginatedAuthEventResponse getAuthenticationEvents(
            @GraphQLNonNull @Valid PaginationInput paginationInput,
            @GraphQLNonNull @Valid AuthenticationEventFilter filter) {
        return authenticationEventService.getAuthenticationEvents(paginationInput, filter);
    }

    @GraphQLQuery(name = "getAuthenticationEventsDaily", description = "fetches daily aggregated authentication events with pagination and optional filtering. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'query')")
    public PaginatedAuthEventDailyResponse getAuthenticationEventsDaily(
            @GraphQLNonNull @Valid PaginationInput paginationInput,
            @GraphQLNonNull @Valid AuthenticationEventDailyFilter filter) {
        return authenticationEventService.getAuthenticationEventsDaily(paginationInput, filter);
    }

    @GraphQLMutation(name = "runAuthenticationEventManager", description = "manually triggers authentication event partition maintenance. Requires admin JWT authentication.")
    @PreAuthorize("hasPermission(null, 'Admin', 'mutation')")
    public void runAuthenticationEventManager() {
        authEventPartitionManager.runMaintenanceWithLock("manual");
    }
}
