// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.rollup.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.rollup.metric.MetricAttributePair;
import com.cvshealth.cap.registry.rollup.model.*;
import com.cvshealth.cap.registry.rollup.model.exception.BadRequestException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.cvshealth.cap.registry.rollup.model.exception.ExceptionReason.FILTER_REQUIRED;

@Service
public class MemberService extends RegistryService {
    protected static String MEMBER_FILTER = "[MEMBER_FILTER]";
    protected static String GRAPHQL_QUERY = """
        query {
            getMembers(
                paginationInput: { pageSize: %s, pageNumber: %s }
                [MEMBER_FILTER]
            ) {
                members {
                    id
                    name
                    lastName
                    firstName
                    email
                    externalId
                    employeeId
                    externalAlias
                    createUser
                    createTimestamp
                    updateUser
                    updateTimestamp
                }
                pageSize
                pageNumber
                totalPageCount
                totalRecordCount
            }
        }
        """;

    public MemberService(WebClient webClient) {
        super(webClient);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry"),
                    @MetricAttributePair(key = "query", value = "getMembers")
    })
    public PaginatedMemberView getMembers(PaginationInput paginationInput, MemberSearchFilter filter) {
        String query = String.format(GRAPHQL_QUERY, paginationInput.getPageSize(), paginationInput.getPageNumber());
        String filterString = buildMembersFilter(filter);
        if (StringUtils.isBlank(filterString)) {
            throw new BadRequestException(FILTER_REQUIRED);
        }
        query = query.replace(MEMBER_FILTER, filterString);

        return constructGraphQLClient()
                .document(query)
                .retrieve("getMembers")
                .toEntity(PaginatedMemberView.class)
                .retryWhen(Retry.backoff(3, Duration.of(1, ChronoUnit.SECONDS))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                retrySignal.failure()))
                .block();
    }

    String buildMembersFilter(MemberSearchFilter filter) {
        if (filter == null) {
            return null;
        }
        List<String> appliedFilters = new ArrayList<>(5);
        if (StringUtils.isNotBlank(filter.getEmail())) {
            appliedFilters.add(getFilter("email", filter.getEmail()));
        } else if (!CollectionUtils.isEmpty(filter.getEmails())) {
            appliedFilters.add(getFilter("emails", filter.getEmails()));
        }
        if (StringUtils.isNotBlank(filter.getName())) {
            appliedFilters.add(getFilter("name", filter.getName()));
        } else if (!CollectionUtils.isEmpty(filter.getNames())) {
            appliedFilters.add(getFilter("names", filter.getNames()));
        }
        if (StringUtils.isNotBlank(filter.getExternalId())) {
            appliedFilters.add(getFilter("externalId", filter.getExternalId()));
        } else if (!CollectionUtils.isEmpty(filter.getExternalIds())) {
            appliedFilters.add(getFilter("externalIds", filter.getExternalIds()));
        }
        if (StringUtils.isNotBlank(filter.getEmployeeId())) {
            appliedFilters.add(getFilter("employeeId", filter.getEmployeeId()));
        } else if (!CollectionUtils.isEmpty(filter.getEmployeeIds())) {
            appliedFilters.add(getFilter("employeeIds", filter.getEmployeeIds()));
        }
        if (StringUtils.isNotBlank(filter.getExternalAlias())) {
            appliedFilters.add(getFilter("externalAlias", filter.getExternalAlias()));
        } else if (!CollectionUtils.isEmpty(filter.getExternalAliases())) {
            appliedFilters.add(getFilter("externalAliases", filter.getExternalAliases()));
        }
        if (StringUtils.isNotBlank(filter.getLastName())) {
            appliedFilters.add(getFilter("lastName", filter.getLastName()));
        } else if (!CollectionUtils.isEmpty(filter.getLastNames())) {
            appliedFilters.add(getFilter("lastNames", filter.getLastNames()));
        }
        return appliedFilters.isEmpty() ? "" : String.format("filter: { %s }", String.join(",", appliedFilters));
    }

    private String getFilter(String label, String value) {
        return String.format("%s: \"%s\"", label, value);
    }

    private String getFilter(String label, List<String> values) {
        return String.format("%s: [\"%s\"]", label, String.join("\",\"", values));
    }
}
