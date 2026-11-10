// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.metric.MetricAttributePair;

import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.rollup.TeamRollupResult;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Supplier;

import static com.cvshealth.cap.registry.config.RestClientConfig.API_SECRET;

@Service
@Slf4j
public class RegistryRollupService {
    private final Supplier<WebClient> rollupApiWebClient;
    private final boolean lineOfBusinessEnabled;

    private static final String GRAPHQL_TEAM_ROLLUP_LOB_INFO = """
                    lineOfBusiness {
                        name
                        description
                        managerExternalId
                        managerName
                    }
    """;

    private static final String GRAPHQL_SINGLE_TEAM_FILTER = """
            	paginationInput: { pageSize: 1, pageNumber: 1 }
            	filter: { name: "%s", includePersonalTeams: true}
    """;

    private static final String GRAPHQL_ALL_TEAMS = """
            	paginationInput: { pageSize: %s, pageNumber: %s }
            	filter: { teamType: %s }
    """;

    protected static String GRAPHQL_TEAM_ROLLUP_QUERY = """
        query {
            getTeamRollup(
                %s
            ) {
            	teams {
                    id
                  	name
                    type
                    %s
                  	managers {
                  	    displayName
                        title
                        email
                        externalId
                        externalAlias
                        firstName
                        lastName
                  	}
                }
                pageSize
                pageNumber
                totalPageCount
                totalRecordCount
            }
        }
        """;

    public RegistryRollupService(Supplier<WebClient> rollupApiWebClient,
                                    @Value("${app.admin.line-business.enabled:false}") boolean lineOfBusinessEnabled) {
        this.rollupApiWebClient = rollupApiWebClient;
        this.lineOfBusinessEnabled = lineOfBusinessEnabled;
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-rollup"),
                    @MetricAttributePair(key = "method", value = "getTeamRollup")
            })
    @Cacheable(value = CacheConfig.TEAM_ROLLUP_INFO)
    public TeamRollupView getTeamRollup(String teamName) {
        String filter = String.format(GRAPHQL_SINGLE_TEAM_FILTER, teamName);
        TeamRollupResult teamRollupResult = constructGraphQLClient()
                .document(String.format(GRAPHQL_TEAM_ROLLUP_QUERY, filter,
                        lineOfBusinessEnabled ? GRAPHQL_TEAM_ROLLUP_LOB_INFO : ""))
                .retrieve("getTeamRollup")
                .toEntity(TeamRollupResult.class)
                .block();
        if (teamRollupResult == null || CollectionUtils.isEmpty(teamRollupResult.getTeams())) {
            return null;
        }
        return teamRollupResult.getTeams().get(0);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-rollup"),
                    @MetricAttributePair(key = "method", value = "getTeamRollup")
            })
    public TeamRollupResult getTeamRollup(PaginationInput paginationInput, TeamType teamType) {
        String pagination = String.format(GRAPHQL_ALL_TEAMS, paginationInput.getPageSize(), paginationInput.getPageNumber(), teamType);
        return constructGraphQLClient()
                .document(String.format(GRAPHQL_TEAM_ROLLUP_QUERY, pagination,
                        lineOfBusinessEnabled ? GRAPHQL_TEAM_ROLLUP_LOB_INFO : ""))
                .retrieve("getTeamRollup")
                .toEntity(TeamRollupResult.class)
                .block();
    }

    private HttpGraphQlClient constructGraphQLClient() {
        return HttpGraphQlClient.builder(rollupApiWebClient.get())
                .headers(h -> {
                    if (!h.containsHeader(API_SECRET)) {
                        if (SecurityHelper.getBearerToken() != null) {
                            h.add("Authorization", "Bearer " + SecurityHelper.getBearerToken());
                        }
                    }
                })
                .build();
    }
}
