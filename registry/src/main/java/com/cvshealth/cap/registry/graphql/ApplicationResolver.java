// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.Application;
import com.cvshealth.cap.registry.model.ApplicationRequest;
import com.cvshealth.cap.registry.model.ApplicationSearchFilter;
import com.cvshealth.cap.registry.model.ApplicationView;
import com.cvshealth.cap.registry.model.PaginatedApplicationResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.ApplicationService;
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

import java.util.List;

@Component
@GraphQLApi
@AllArgsConstructor
public class ApplicationResolver {
    private final ApplicationService applicationService;
    private final RegistryService registryService;

    @GraphQLMutation(name = "createApplication", description = "creates registry application")
    @PreAuthorize("hasPermission(#application.teamName, 'Application', 'create')")
    public Application createApplication(@GraphQLNonNull ApplicationRequest application) {
        return registryService.createApplication(application);
    }

    @GraphQLMutation(name = "updateApplication", description = "updates registry application")
    @PreAuthorize("hasPermission(#application.name, 'Application', 'update')")
    public Application updateApplication(@GraphQLNonNull ApplicationRequest application) {
        return registryService.updateApplication(application);
    }

    @GraphQLQuery(name = "getApplications", description = "fetches all registry applications")
    public PaginatedApplicationResponse getApplications(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                        @Valid ApplicationSearchFilter filter,
                                                        @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return applicationService.getApplications(paginationInput, filter, graphQLEnv);
    }

    @GraphQLQuery(name = "getApplication", description = "fetches registry application by id")
    public ApplicationView getApplication(@GraphQLNonNull Long id,
                                          @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return applicationService.getApplicationView(id, graphQLEnv);
    }

    @GraphQLQuery(name = "getApplicationByName", description = "fetches registry application by name")
    public ApplicationView getApplicationByName(@GraphQLNonNull String name,
                                                @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return applicationService.getApplicationView(ApplicationSearchFilter.builder().name(name).build(), graphQLEnv);
    }

    @GraphQLQuery(name = "getApplicationsByExternalId", description = "fetches all registry applications for the given external Id")
    public List<ApplicationView> getApplicationsByExternalId(@GraphQLNonNull String externalId,
                                                             @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return applicationService.getApplicationViewsByExternalId(externalId, graphQLEnv);
    }


    @GraphQLMutation(name = "deleteApplication", description = "deletes registry application by name")
    @PreAuthorize("hasPermission(#name, 'Application', 'delete')")
    public void deleteApplication(@GraphQLNonNull String name) {
        registryService.deleteApplication(name);
    }

}

