// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.rollup.metric.MetricAttributePair;
import com.cvshealth.cap.registry.rollup.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.rollup.model.PaginatedTeamView;
import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import com.cvshealth.cap.registry.rollup.model.TeamSearchFilter;
import com.cvshealth.cap.registry.rollup.model.TeamType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class TeamService extends RegistryService {
    protected static String GRAPHQL_TEAM_QUERY = """
        query GetTeams($pageSize: Int!, $pageNumber: Int!, $names: [String], $type: TeamType) {
           getTeams(
               paginationInput: { pageSize: $pageSize, pageNumber: $pageNumber }
               filter: { names: $names, type: $type }
            ) {
                teams {
                    id
                    name
                    type
                    description
                    version
                    createUser
                    createTimestamp
                    updateUser
                    updateTimestamp
                    members {
                        externalId
                        externalAlias
                        email
                        name
                        role
                    }
                }
                pageSize
                pageNumber
                totalPageCount
                totalRecordCount
            }
        }
        """;

    public TeamService(WebClient webClient) {
        super(webClient);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry"),
                    @MetricAttributePair(key = "query", value = "getTeams")
            })
    public PaginatedTeamView getTeams(PaginationInput paginationInput, TeamSearchFilter filter) {
        return constructGraphQLClient()
                .document(GRAPHQL_TEAM_QUERY)
                .variables(buildVariables(paginationInput, filter))
                .retrieve("getTeams")
                .toEntity(PaginatedTeamView.class)
                .retryWhen(Retry.backoff(3, Duration.of(1, ChronoUnit.SECONDS))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                retrySignal.failure()))
                .block();
    }

    protected Map<String, Object> buildVariables(PaginationInput pagination, TeamSearchFilter filter) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("pageSize", pagination.getPageSize());
        vars.put("pageNumber", pagination.getPageNumber());
        vars.put("type", TeamType.SHARED);  // default to shared teams if no filter provided, as personal teams should only be included when explicitly requested via filter
        if (filter != null) {
            if (filter.getNameList() != null) {
                vars.put("names", filter.getNameList());
            }
            if (filter.getTeamType() != null) {
                vars.put("type", filter.getTeamType());
            } else if (Boolean.TRUE.equals(filter.getIncludePersonalTeams())) {
                vars.put("type", null); // include all teams
            }
        }
        return vars;
    }
}
