// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.metric.MetricAttributePair;
import com.cvshealth.cap.registry.rollup.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.rollup.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.rollup.model.ApplicationSearchFilter;
import com.cvshealth.cap.registry.rollup.model.PaginatedApplicationView;
import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
public class ApplicationService extends RegistryService {
    protected static String APP_NAME_FILTER = "[APP_NAME_FILTER]";
    protected static String GRAPHQL_APP_NAME_FILTER = " filter: { names: [\"%s\"] } ";
    protected static String GRAPHQL_APP_QUERY = """
        query {
            getApplications(
                paginationInput: { pageSize: %s, pageNumber: %s }
                [APP_NAME_FILTER]
            ) {
                applications {
                    id
                    name
                    description
                    version
                    createUser
                    createTimestamp
                    updateUser
                    updateTimestamp
                    team {
                        name
                        type
                        members {
                            externalId
                            externalAlias
                            email
                            name
                            role
                        }
                    }
                }
                pageSize
                pageNumber
                totalPageCount
                totalRecordCount
            }
        }
        """;

    public ApplicationService(WebClient webClient) {
        super(webClient);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry"),
                    @MetricAttributePair(key = "query", value = "getApplications")
    })
    public PaginatedApplicationView getApplications(PaginationInput paginationInput, ApplicationSearchFilter filter) {
        String query = String.format(GRAPHQL_APP_QUERY, paginationInput.getPageSize(), paginationInput.getPageNumber());
        if (filter != null && filter.getNameList() != null) {
            String teamNameFilter = String.format(GRAPHQL_APP_NAME_FILTER, String.join("\",\"", filter.getNameList()));
            query = query.replace(APP_NAME_FILTER, teamNameFilter);
        } else {
            query = query.replace(APP_NAME_FILTER, "");
        }
        return constructGraphQLClient()
                .document(query)
                .retrieve("getApplications")
                .toEntity(PaginatedApplicationView.class)
                .retryWhen(Retry.backoff(3, Duration.of(1, ChronoUnit.SECONDS))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                retrySignal.failure()))
                .block();
    }

}
