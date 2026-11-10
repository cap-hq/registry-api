// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql;

import com.cvshealth.cap.registry.rollup.model.PaginatedTeamRollupResponse;
import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import com.cvshealth.cap.registry.rollup.model.TeamSearchFilter;
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
public class TeamResolver {
    private final TeamRollupService teamRollupService;

    @GraphQLQuery(name = "getTeamRollup", description = "fetches registry team with manager rollup. filter fields teamType and includePersonalTeams are mutually exclusive and cannot both be specified.")
    public PaginatedTeamRollupResponse getTeamsWithManager(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                                           @Valid TeamSearchFilter filter) {
        return teamRollupService.getTeamRollup(paginationInput, filter);
    }

}