// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql;

import com.cvshealth.cap.registry.rollup.model.ApplicationSearchFilter;
import com.cvshealth.cap.registry.rollup.model.PaginatedApplicationRollupResponse;
import com.cvshealth.cap.registry.rollup.model.PaginatedTeamRollupResponse;
import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import com.cvshealth.cap.registry.rollup.service.ApplicationRollupService;
import com.cvshealth.cap.registry.rollup.service.TeamRollupService;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@GraphQLApi
@AllArgsConstructor
public class ApplicationResolver {
    private final ApplicationRollupService applicationRollupService;

    @GraphQLQuery(name = "getApplicationRollup", description = "fetches registry application with manager rollup")
    public PaginatedApplicationRollupResponse getApplicationsWithManager(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                                 @Valid ApplicationSearchFilter filter) {
        return applicationRollupService.getApplicationRollup(paginationInput, filter);
    }

}