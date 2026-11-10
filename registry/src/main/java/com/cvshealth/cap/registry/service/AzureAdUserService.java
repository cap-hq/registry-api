// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.mapper.UserGroupMapper;
import com.cvshealth.cap.registry.mapper.UserMapper;
import com.cvshealth.cap.registry.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.metric.MetricAttributePair;
import com.cvshealth.cap.registry.model.Group;
import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.model.UserGroups;
import com.microsoft.graph.core.content.BatchRequestContent;
import com.microsoft.graph.core.content.BatchResponseContent;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.DirectoryObjectCollectionResponse;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.getbyids.GetByIdsPostRequestBody;
import com.microsoft.graph.users.getbyids.GetByIdsPostResponse;
import com.microsoft.graph.users.getbyids.GetByIdsRequestBuilder;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AzureAdUserService {

    private static final String SELECT = "id,employeeId,displayName,givenName,surname,mail,mailNickname,userType,jobTitle,accountEnabled,onPremisesSamAccountName";
    private static final String EXPAND = "manager($levels=max;$select=" + SELECT + ")";
    private static final String BASE_FILTER = "mail ne null and displayName ne null and userType eq 'Member'";
    private static final String DEFAULT_FILTER = BASE_FILTER + " and accountEnabled eq true";
    private static final Integer SEARCH_LIMIT = 100;
    private static final Integer AZURE_BATCH_REQUEST_LIMIT = 20;
    private static final Integer AZURE_GET_BY_IDS_LIMIT = 1000;
    private final GraphServiceClient graphServiceClient;
    private final UserMapper mapper;
    private final UserGroupMapper userGroupMapper;
    private final AzureAdUserServiceValidator serviceValidator;

    @Value("${app.job.azure-manager-sync.omit-manager-level}")
    List<String> userTitleLevelOmit;

    private Pattern userTitlePattern;

    @PostConstruct
    void init() {
        userTitlePattern = MemberTitleUtil.createJobTitleLevelPattern(userTitleLevelOmit);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "exists"),
                    @MetricAttributePair(key = "query", value = "users.byUserId")
            })
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

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findById"),
                    @MetricAttributePair(key = "query", value = "users.byUserId")
            })
    public Optional<User> findById(String id) {
        try {
            return Optional.ofNullable(mapper.toDto(graphServiceClient.users().byUserId(id)
                    .get(requestConfiguration -> {
                        requestConfiguration.queryParameters.select = new String[]{SELECT};
                    })));
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findById"),
                    @MetricAttributePair(key = "query", value = "users.byUserId")
            })
    public Optional<User> findByIdWithManager(String id) {
        try {
            if (!exists(id)) {
                return Optional.empty();
            }
            com.microsoft.graph.models.User user = graphServiceClient.users().byUserId(id)
                    .get(requestConfiguration -> {
                        requestConfiguration.queryParameters.select = new String[]{SELECT};
                        requestConfiguration.queryParameters.expand = new String[]{EXPAND};
                        requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    });
            // when user manager is returned from AD, check if it can be added to Registry
            if (user != null && !includeManager((com.microsoft.graph.models.User)user.getManager())) {
                user.setManager(null);
            }
            return Optional.ofNullable(mapper.toDto(user));
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findByAlias"),
                    @MetricAttributePair(key = "query", value = "users")
            })
    public Optional<User> findByAlias(String externalAlias) throws ApiException {
        var page = graphServiceClient.users()
                .get(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.search =
                            searchQuery(externalAlias, "mailNickname");
                    requestConfiguration.queryParameters.filter = DEFAULT_FILTER;
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                    requestConfiguration.queryParameters.top = SEARCH_LIMIT;
                    requestConfiguration.queryParameters.orderby = new String[]{"displayName"};
                });
        if (page == null || page.getValue() == null) {
            return Optional.empty();
        }
        // NOTE: EXPAND is not supported on calls that can return multiple users
        return page.getValue().stream().map(mapper::toDto).findFirst();
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findAllByNameOrEmail"),
                    @MetricAttributePair(key = "query", value = "users")
            })
    public List<User> findAllByNameOrEmail(String nameOrEmail) throws ApiException {
        return findAllByNameOrEmail(nameOrEmail, false);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findAllByNameOrEmail"),
                    @MetricAttributePair(key = "query", value = "users")
            })
    public List<User> findAllByNameOrEmail(String nameOrEmail, Boolean includeDisabledAccounts) throws ApiException {
        serviceValidator.validate(nameOrEmail);
        var page = graphServiceClient.users()
                .get(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.search =
                            searchQuery(nameOrEmail, "displayName", "mail");
                    requestConfiguration.queryParameters.filter = Boolean.TRUE == includeDisabledAccounts ? BASE_FILTER : DEFAULT_FILTER;
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                    requestConfiguration.queryParameters.top = SEARCH_LIMIT;
                    requestConfiguration.queryParameters.orderby = new String[]{"displayName"};
                });
        if (page == null || page.getValue() == null) {
            return Collections.emptyList();
        }
        return page.getValue().stream().map(mapper::toDto).collect(Collectors.toList());
    }

    public Optional<User> findByEmail(String email) throws ApiException {
        serviceValidator.validate(email);
        var page = graphServiceClient.users()
                .get(requestConfiguration -> {
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    requestConfiguration.queryParameters.search =
                            searchQuery(email, "mail");
                    requestConfiguration.queryParameters.filter = DEFAULT_FILTER;
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                    requestConfiguration.queryParameters.top = SEARCH_LIMIT;
                    requestConfiguration.queryParameters.orderby = new String[]{"displayName"};
                });
        if (page == null || page.getValue() == null) {
            return Optional.empty();
        }
        return page.getValue().stream().map(mapper::toDto).findFirst();
    }

    /**
     * Batch find members by Azure external ID (limited to 20 requests)
     * @throws IOException
     */
    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "batchFindByIds"),
                    @MetricAttributePair(key = "query", value = "batch.users.byUserId")
            })
    public Map<MemberView, Optional<User>> batchFindMembersByIds(List<MemberView> members) throws IOException {
        if (members.size() > AZURE_BATCH_REQUEST_LIMIT) {
            throw new ApiException(String.format("Azure batch request size limit[%s] exceeded", AZURE_BATCH_REQUEST_LIMIT));
        }

        // batch up requests
        Map<String, MemberView> requestIdMemberMap = new HashMap<>();
        BatchRequestContent batchRequestContent = new BatchRequestContent(graphServiceClient);
        members.forEach(member -> {
            RequestInformation requestInformation = graphServiceClient.users().byUserId(member.getExternalId())
                    .toGetRequestInformation(requestConfiguration -> {
                        requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                        requestConfiguration.queryParameters.select = new String[]{SELECT};
            });
            requestIdMemberMap.put(batchRequestContent.addBatchRequestStep(requestInformation), member);
        });

        // execute batch requests
        Map<MemberView, Optional<User>> memberToUserMap = new HashMap<>(members.size());
        BatchResponseContent batchResponseContent = Objects.requireNonNull(
                graphServiceClient.getBatchRequestBuilder().post(batchRequestContent, null));
        for (Map.Entry<String, MemberView> requestIdMapEntry : requestIdMemberMap.entrySet()) {
            try {
                Optional<User> user = Optional.ofNullable(mapper.toDto(batchResponseContent.getResponseById(requestIdMapEntry.getKey(),
                        com.microsoft.graph.models.User::createFromDiscriminatorValue)));
                memberToUserMap.put(requestIdMapEntry.getValue(), user);
            } catch (ApiException e) {
                if (e.getResponseStatusCode() == 404) {
                    memberToUserMap.put(requestIdMapEntry.getValue(),  Optional.empty());
                }
            }
        }
        return memberToUserMap;
    }

    /**
     * Find members by Azure external ID (limited by Microsoft GraphQL API to 1000 requests)
     */
    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findByIds"),
                    @MetricAttributePair(key = "query", value = "users.byUserIds")
            })
    public Map<MemberView, Optional<User>> findMembersByIds(List<MemberView> members) throws ApiException {
        if (members.size() > AZURE_GET_BY_IDS_LIMIT) {
            throw new ApiException(String.format("Azure getByIds request size limit[%s] exceeded", AZURE_GET_BY_IDS_LIMIT));
        }

        // build userIds request
        Map<String, MemberView> userIdMemberMap = members.stream().collect(Collectors.toMap(MemberView::getExternalId, member -> member));
        GetByIdsPostRequestBody requestBody = new GetByIdsPostRequestBody();
        requestBody.setIds(userIdMemberMap.keySet().stream().toList());

        GetByIdsRequestBuilder requestBuilder = graphServiceClient.users().getByIds();
        RequestInformation requestInformation = requestBuilder.toPostRequestInformation(requestBody,
                postRequestConfiguration -> postRequestConfiguration.headers.add("ConsistencyLevel", "eventual"));

        // NOTE: Add explicit override to template to add the user fields returned.
        requestInformation.urlTemplate = requestInformation.urlTemplate + "{?%24select}";
        requestInformation.addQueryParameter("%24select", new String[]{SELECT});

        // explicitly submit request
        HashMap<String, ParsableFactory<? extends Parsable>> errorMapping = new HashMap<>();
        errorMapping.put("XXX", ODataError::createFromDiscriminatorValue);
        GetByIdsPostResponse response = graphServiceClient.getRequestAdapter().send(requestInformation, errorMapping, GetByIdsPostResponse::createFromDiscriminatorValue);

        Map<MemberView, Optional<User>> memberToUserMap = new HashMap<>(members.size());
        if (response != null && !CollectionUtils.isEmpty(response.getValue())) {
            response.getValue().forEach(directoryObject -> {
                User user = mapper.toDto((com.microsoft.graph.models.User) directoryObject);
                memberToUserMap.put(userIdMemberMap.get(directoryObject.getId()), Optional.of(user));
            });
        }

        // fill missing members not in the response
        userIdMemberMap.values().forEach(member -> {
            if (!memberToUserMap.containsKey(member)) {
                memberToUserMap.put(member, Optional.empty());
            }
        });
        return memberToUserMap;
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "findUserManager"),
                    @MetricAttributePair(key = "query", value = "users.byUserId")
            })
    public Optional<User> findUserManager(String id) {
        try {
            if (!exists(id)) {
                return Optional.empty();
            }
            DirectoryObject directoryObject = graphServiceClient.users().byUserId(id).manager().get();
            if (directoryObject == null) {
                return Optional.empty();
            }
            // check whether manager should be made available to Registry
            com.microsoft.graph.models.User manager = ((com.microsoft.graph.models.User) directoryObject);
            return Optional.ofNullable(includeManager(manager) ? mapper.toDto(manager) : null);
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Batch find the Registry members associated manager in AD
     */
    public Map<MemberView, Optional<User>> batchFindUserManagers(List<MemberView> members) throws ApiException {
        // batch up requests
        Map<String, MemberView> requestIdMemberMap = new HashMap<>();
        BatchRequestContent batchRequestContent = new BatchRequestContent(graphServiceClient);
        members.forEach(member -> {
            RequestInformation requestInformation = graphServiceClient.users().byUserId(member.getExternalId()).manager()
                    .toGetRequestInformation(requestConfiguration -> {
                        requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    });
            requestIdMemberMap.put(batchRequestContent.addBatchRequestStep(requestInformation), member);
        });

        // execute batch requests
        try {
            Map<MemberView, Optional<User>> userToManaagerMap = new HashMap<>(members.size());
            BatchResponseContent batchResponseContent = Objects.requireNonNull(
                    graphServiceClient.getBatchRequestBuilder().post(batchRequestContent, null));
            for (Map.Entry<String, MemberView> requestIdMapEntry : requestIdMemberMap.entrySet()) {
                try {
                    com.microsoft.graph.models.User manager = batchResponseContent.getResponseById(requestIdMapEntry.getKey(),
                            com.microsoft.graph.models.User::createFromDiscriminatorValue);
                    // check whether manager should be made available to Registry
                    Optional<User> user = Optional.ofNullable(includeManager(manager) ? mapper.toDto(manager) : null);
                    userToManaagerMap.put(requestIdMapEntry.getValue(), user);
                } catch (ApiException e) {
                    if (e.getResponseStatusCode() == 404) {
                        userToManaagerMap.put(requestIdMapEntry.getValue(), Optional.empty());
                    }
                }
            }
            return userToManaagerMap;
        } catch (IOException e) {
            throw new ApiException(e);
        }
    }

    /*
     * Returns whether the user representing a manager in Active Directory should be made available to Registry, based on the user title level.
     * When the user title matches the configured max level threshold, the manager is omitted; not available to Registry.  Any other title including null or blank is available to Registry.
     *
     * When a member is removed from An Registry the member's manager is used for reassigning
     * any Registry resources owned by the departing member.
     *
     * This rule assumes that AD users with titles higher than the max level threshold will not own resources in Registry,
     * so no check is made for ALL title levels higher than the max level threshold
     */
    boolean includeManager(com.microsoft.graph.models.User manager) {
        if (manager == null) return false;
        if (StringUtils.isBlank(manager.getJobTitle()) || userTitlePattern == null) return true;
        return !userTitlePattern.matcher(manager.getJobTitle()).matches();
    }

    /**
     * Finds the direct reports for a given manager in AD
     */
    public List<User> findManagerDirectReports(String managerId) {
        List<User> users = new ArrayList<>();
        DirectoryObjectCollectionResponse response = graphServiceClient.users().byUserId(managerId).directReports()
                .get(requestConfiguration -> {
                    requestConfiguration.queryParameters.select = new String[]{SELECT};
                    requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                });
        if (response != null && !CollectionUtils.isEmpty(response.getValue())) {
            users.addAll(mapToUser(response.getValue()));

            while (response != null && response.getOdataNextLink() != null) {
                String nextLink = response.getOdataNextLink();
                response = graphServiceClient.users().byUserId(managerId).directReports().withUrl(nextLink).get();
                if (response != null && !CollectionUtils.isEmpty(response.getValue())) {
                    users.addAll(mapToUser(response.getValue()));
                }
            }
        }
        return users;
    }

    private List<User> mapToUser(List<DirectoryObject> directoryObjects) {
        return directoryObjects.stream().map(directoryObject -> mapper.toDto((com.microsoft.graph.models.User) directoryObject)).toList();
    }

    /**
     * Batch find the direct reports for a set of managers in AD
     */
    public Map<String, List<String>> batchFindManagerDirectReports(List<String> managers) throws ApiException {
        // batch up requests
        Map<String, String> requestIdManagerMap = new HashMap<>();
        BatchRequestContent batchRequestContent = new BatchRequestContent(graphServiceClient);
        managers.forEach(managerId -> {
            RequestInformation requestInformation = graphServiceClient.users().byUserId(managerId).directReports()
                    .toGetRequestInformation(requestConfiguration -> {
                        requestConfiguration.headers.add("ConsistencyLevel", "eventual");
                    });
            requestIdManagerMap.put(batchRequestContent.addBatchRequestStep(requestInformation), managerId);
        });

        // execute batch requests
        try {
            Map<String, List<String>> managerDirectReportsMap = new HashMap<>(managers.size());
            BatchResponseContent batchResponseContent = Objects.requireNonNull(
                    graphServiceClient.getBatchRequestBuilder().post(batchRequestContent, null));
            for (Map.Entry<String, String> requestIdMapEntry : requestIdManagerMap.entrySet()) {
                try {
                    // Tech decision to not return all pages of a manager's direct reports, as this can result in several additional paging requests.
                    // As almost all managers will have a single page of direct reports, this should only result in a small number of larger teams that may result is miss finding the direct report.
                    // If a miss occurs, this will result in the manager being removed from the member record and being re-added in the subsequent job step
                    // The downside is that this will appear as a member record update in the audit log, but will have a much lower performance cost that several additional external calls.
                    DirectoryObjectCollectionResponse response = batchResponseContent.getResponseById(requestIdMapEntry.getKey(), DirectoryObjectCollectionResponse::createFromDiscriminatorValue);
                    if (response == null || response.getValue() == null) {
                        return Collections.emptyMap();
                    }
                    List<String> directReportsIds = response.getValue().stream().map(DirectoryObject::getId).toList();
                    managerDirectReportsMap.put(requestIdMapEntry.getValue(), directReportsIds);
                } catch (ApiException e) {
                    if (e.getResponseStatusCode() == 404) {
                        managerDirectReportsMap.put(requestIdMapEntry.getValue(), Collections.emptyList());
                    }
                }
            }
            return managerDirectReportsMap;
        } catch (IOException e) {
            throw new ApiException(e);
        }
    }

    private String searchQuery(String value, String... fields) {
        return Arrays.stream(fields)
                .map(f -> f + ":" + value.toLowerCase())
                .map(q -> '"' + q + '"')
                .collect(Collectors.joining(" OR "));
    }

    public List<Group> findUserEmailGroups(String userEmail) {
        try {
            List<Group> groups = new ArrayList<>();
            DirectoryObjectCollectionResponse
                    userGroupDirectoryObjectResponse = graphServiceClient.users().byUserId(userEmail).memberOf().get();
            if (userGroupDirectoryObjectResponse != null) {
                UserGroups userGroups = userGroupMapper.toDto(userGroupDirectoryObjectResponse);
                groups.addAll(filterUserGroups(userGroups));

                while (userGroupDirectoryObjectResponse != null && userGroupDirectoryObjectResponse.getOdataNextLink() != null) {
                    String nextLink = userGroupDirectoryObjectResponse.getOdataNextLink();
                    userGroupDirectoryObjectResponse = graphServiceClient.users().byUserId(userEmail).memberOf().withUrl(nextLink).get();
                    userGroups = userGroupMapper.toDto(userGroupDirectoryObjectResponse);
                    groups.addAll(filterUserGroups(userGroups));
                }
            }
            return groups;
        } catch (ApiException e) {
            if (e.getResponseStatusCode() == 404) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    /**
     * Returns only the email groups the user is a member of
     */
    List<Group> filterUserGroups(UserGroups usergroups) {
        if (usergroups == null || CollectionUtils.isEmpty(usergroups.getValue())) {
            return Collections.emptyList();
        }
        return usergroups.getValue().stream()
                .filter(group -> "true".equalsIgnoreCase(group.getMailEnabled()) && "false".equals(group.getSecurityEnabled()))
                .toList();
    }
}
