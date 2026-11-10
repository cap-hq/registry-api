// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.rollup.metric.MetricAttributePair;
import com.cvshealth.cap.registry.rollup.config.CacheConfig;
import com.cvshealth.cap.registry.rollup.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.rollup.model.User;
import com.cvshealth.cap.registry.rollup.mapper.UserMapper;
import com.cvshealth.cap.registry.rollup.model.exception.BadRequestException;
import com.cvshealth.cap.registry.rollup.model.exception.ExceptionReason;
import com.microsoft.graph.core.content.BatchRequestContent;
import com.microsoft.graph.core.content.BatchResponseContent;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AzureAdUserService {

    private static final String SELECT = "id,employeeId,displayName,givenName,surname,mail,mailNickname,userType,jobTitle";
    private static final String EXPAND = "manager($levels=max;$select=" + SELECT + ")";
    private static final String DEFAULT_FILTER = "mail ne null and displayName ne null and userType eq 'Member' and accountEnabled eq true";
    private static final Integer BATCH_REQUEST_LIMIT = 20;
    private static final Integer SEARCH_LIMIT = 100;
    private static final int SEARCH_MIN_LENGTH = 3;

    private final GraphServiceClient graphServiceClient;
    private final UserMapper mapper;

    public boolean exists(String id) {
        try {
            return graphServiceClient.users().byUserId(id).get() != null;
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Cacheable(value = CacheConfig.CACHE_AZURE, key = "#id")
    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "query", value = "users.byUserId")
            })
    public Optional<User> findById(String id) {
        try {
            return Optional.ofNullable(mapper.toDto(graphServiceClient.users().byUserId(id)
                    .get(requestConfiguration -> {
                        requestConfiguration.queryParameters.select = new String[]{SELECT};
                        requestConfiguration.queryParameters.expand = new String[]{EXPAND};
                        requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    })));
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public RequestInformation getByUserIdRequestInformation(String externalId) {
        return graphServiceClient.users().byUserId(externalId)
                .toGetRequestInformation(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                });
    }

    public RequestInformation getByUserAliasRequestInformation(String externalAlias) {
        return graphServiceClient.users()
                .toGetRequestInformation(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.search =
                            searchQuery(externalAlias, "mailNickname");
                    requestConfiguration.queryParameters.filter = DEFAULT_FILTER;
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                    requestConfiguration.queryParameters.top = SEARCH_LIMIT;
                    requestConfiguration.queryParameters.orderby = new String[]{"displayName"};
                });
    }

    public RequestInformation getByUserEmployeeIdRequestInformation(String employeeId) {
        String filterExpression = String.format("employeeId eq '%s' and %s", employeeId, DEFAULT_FILTER);
        return graphServiceClient.users()
                .toGetRequestInformation(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.count = true;
                    requestConfiguration.queryParameters.filter = filterExpression;
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                });
    }

    public RequestInformation getByUserNameOrEmailRequestInformation(String nameOrEmail) {
        validateNameOrEmail(nameOrEmail);
        return graphServiceClient.users()
                .toGetRequestInformation(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.search =
                            searchQuery(nameOrEmail, "displayName", "mail");
                    requestConfiguration.queryParameters.filter = DEFAULT_FILTER;
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                    requestConfiguration.queryParameters.top = SEARCH_LIMIT;
                    requestConfiguration.queryParameters.orderby = new String[]{"displayName"};
                });
    }

    public List<User> batchFindUsers(List<RequestInformation> requests) {
        if (requests.size() > BATCH_REQUEST_LIMIT) {
            throw new ApiException(String.format("Azure batch request size limit[%s] exceeded", BATCH_REQUEST_LIMIT));
        }

        // batch up requests
        List<String> requestIds = new ArrayList<>();
        BatchRequestContent batchRequestContent = new BatchRequestContent(graphServiceClient);
        requests.forEach(request -> {
            requestIds.add(batchRequestContent.addBatchRequestStep(request));
        });

        // execute batch requests
        try {
            List<User> users = new ArrayList<>();
            BatchResponseContent batchResponseContent = Objects.requireNonNull(
                    graphServiceClient.getBatchRequestBuilder().post(batchRequestContent, null));
            if (!StringUtils.isEmpty(batchResponseContent.getNextLink())) {
                moreResultsExistEvent();
            }
            requestIds.forEach(requestId -> {
                com.microsoft.graph.models.User adUser = batchResponseContent.getResponseById(requestId,
                        com.microsoft.graph.models.User::createFromDiscriminatorValue);
                if (adUser != null) {
                    Optional<User> user = Optional.ofNullable(mapper.toDto(adUser));
                    user.ifPresent(users::add);
                }
            });
            return users;
        } catch (IOException e) {
            log.error("Error while fetching users from Active Directory", e);
            throw new ApiException("Error while fetching users from Active Directory. Try again later");
        }
    }

    public List<User> batchFindUserCollections(List<RequestInformation> requests) {
        if (requests.size() > BATCH_REQUEST_LIMIT) {
            throw new ApiException(String.format("Azure batch request size limit[%s] exceeded", BATCH_REQUEST_LIMIT));
        }

        // batch up requests
        List<String> requestIds = new ArrayList<>();
        BatchRequestContent batchRequestContent = new BatchRequestContent(graphServiceClient);
        requests.forEach(request -> {
            requestIds.add(batchRequestContent.addBatchRequestStep(request));
        });

        // execute batch requests
        try {
            List<User> users = new ArrayList<>();
            BatchResponseContent batchResponseContent = Objects.requireNonNull(
                    graphServiceClient.getBatchRequestBuilder().post(batchRequestContent, null));
            if (!StringUtils.isEmpty(batchResponseContent.getNextLink())) {
                moreResultsExistEvent();
            }
            requestIds.forEach(requestId -> {
                com.microsoft.graph.models.UserCollectionResponse collectionResponse = batchResponseContent.getResponseById(requestId,
                        com.microsoft.graph.models.UserCollectionResponse::createFromDiscriminatorValue);
                if (collectionResponse != null) {
                    if (collectionResponse.getOdataNextLink() != null) {
                        moreResultsExistEvent();
                    } else if (collectionResponse.getValue() != null) {
                        for (com.microsoft.graph.models.User adUser : collectionResponse.getValue()) {
                            Optional<User> user = Optional.ofNullable(mapper.toDto(adUser));
                            user.ifPresent(users::add);
                        }
                    }
                }
            });
            return users;
        } catch (IOException e) {
            log.error("Error while fetching users from Active Directory", e);
            throw new ApiException("Error while fetching users from Active Directory. Try again later");
        }
    }

    void moreResultsExistEvent() {
        log.debug("Max number of results limit has been reached fetching users from Active Directory");
        throw new ApiException("Max number of results limit has been reached.  Please refine search");
    }

    public int getSearchLimit() {
        return SEARCH_LIMIT;
    }

    public int getBatchSearchLimit() {
        return BATCH_REQUEST_LIMIT;
    }

    private String searchQuery(String value, String... fields) {
        return Arrays.stream(fields)
                .map(f -> f + ":" + value.toLowerCase())
                .map(q -> '"' + q + '"')
                .collect(Collectors.joining(" OR "));
    }

    void validateNameOrEmail(String request) throws ApiException {
        if (StringUtils.isEmpty(request)) {
            throw new BadRequestException(ExceptionReason.USER_SEARCH_REQUIRED);
        }
        if (request.length() < SEARCH_MIN_LENGTH) {
            throw new BadRequestException(ExceptionReason.USER_SEARCH_INVALID_LENGTH,
                    String.format(ExceptionReason.USER_SEARCH_INVALID_LENGTH.label, SEARCH_MIN_LENGTH));

        }
    }
}
