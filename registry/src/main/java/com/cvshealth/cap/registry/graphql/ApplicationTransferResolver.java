// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.ApplicationTransferService;
import io.leangen.graphql.annotations.*;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@GraphQLApi
@AllArgsConstructor
public class ApplicationTransferResolver {
    private final ApplicationTransferService applicationTransferService;
    private final RegistryService registryService;

    @GraphQLQuery(name = "getApplicationTransfers", description = "fetches all application transfers")
    public List<ApplicationTransfer> getApplicationTransfers() {
        return applicationTransferService.getApplicationTransfers();
    }

    @GraphQLQuery(name = "getApplicationTransfer", description = "fetches application transfer by id")
    public ApplicationTransfer getApplicationTransfer(@GraphQLArgument(name = "id") Long id) {
        return applicationTransferService.getApplicationTransfer(id);
    }

    @GraphQLQuery(name = "getApplicationTransfersForTeam", description = "fetches all application transfers for team")
    public List<ApplicationTransfer> getApplicationTransfersForTeam(@GraphQLArgument(name = "teamId") Long teamId) {
        return applicationTransferService.getApplicationTransfersForTeam(teamId);
    }

    @GraphQLQuery(name = "getApplicationTransfersForTeamByName", description = "fetches all application transfers for team by the team name")
    public List<ApplicationTransferView> getApplicationTransfersForTeamByName(@GraphQLArgument(name = "teamName") String teamName) {
        return applicationTransferService.getApplicationTransfersForTeamName(teamName);
    }

    @GraphQLQuery(name = "getApplicationTransfersForTeamsByName", description = "fetches all application transfers for teams by team name")
    public PaginatedApplicationTransferResponse getApplicationTransfersForTeamsByName(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                                                      @Valid ApplicationTransferSearchFilter filter) {
        return applicationTransferService.getApplicationTransfersForTeamsByName(paginationInput, filter);
    }

    @GraphQLMutation(name = "createApplicationTransfer", description = "transfer ownership of application")
    @PreAuthorize("hasPermission(#request.applicationName, 'Application', 'update') and hasPermission(#request.sourceTeamName, 'ApplicationTransfer', 'create')")
    public ApplicationTransfer createApplicationTransfer(@GraphQLArgument(name = "applicationTransfer") ApplicationTransferRequest request) {
        return registryService.createApplicationTransfer(request);
    }

    @GraphQLMutation(name = "acceptApplicationTransfer", description = "updates application transfer")
    @PreAuthorize("hasPermission(#request.sourceTeamName, 'ApplicationTransfer', 'update') or hasPermission(#request.receiveTeamName, 'ApplicationTransfer', 'update')")
    public ApplicationTransfer acceptApplicationTransfer(@GraphQLArgument(name = "applicationTransfer") ApplicationTransferRequest request) {
        return registryService.updateApplicationTransfer(request, ApplicationTransferStatus.COMPLETED);
    }

    @GraphQLMutation(name = "rejectApplicationTransfer", description = "updates application transfer")
    @PreAuthorize("hasPermission(#request.sourceTeamName, 'ApplicationTransfer', 'update') or hasPermission(#request.receiveTeamName, 'ApplicationTransfer', 'update')")
    public ApplicationTransfer rejectApplicationTransfer(@GraphQLArgument(name = "applicationTransfer") ApplicationTransferRequest request) {
        return registryService.updateApplicationTransfer(request, ApplicationTransferStatus.REJECTED);
    }

    @GraphQLMutation(name = "cancelApplicationTransfer", description = "updates application transfer")
    @PreAuthorize("hasPermission(#request.sourceTeamName, 'ApplicationTransfer', 'update') or hasPermission(#request.receiveTeamName, 'ApplicationTransfer', 'update')")
    public ApplicationTransfer cancelApplicationTransfer(@GraphQLArgument(name = "applicationTransfer") ApplicationTransferRequest request) {
        return registryService.updateApplicationTransfer(request, ApplicationTransferStatus.CANCELLED);
    }

}
