// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.metric.MetricAttributePair;
import com.cvshealth.cap.registry.model.AuthProviderCheckClient;
import com.cvshealth.cap.registry.model.CredentialInfo;
import com.microsoft.graph.models.Application;
import com.microsoft.graph.models.ApplicationCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AzureApplicationService {

    private final GraphServiceClient graphServiceClient;

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "azure"),
                    @MetricAttributePair(key = "method", value = "clientCredentials"),
                    @MetricAttributePair(key = "query", value = "applications")
            })
    public List<CredentialInfo> clientCredentials(AuthProviderCheckClient authProviderClient) {
        // Fetch Azure application with display name
        ApplicationCollectionResponse result = graphServiceClient.applications().get(requestConfiguration -> {
            requestConfiguration.queryParameters.filter = String.format("startswith(displayName, '%s')", authProviderClient.getName());
            requestConfiguration.queryParameters.count = true;
            requestConfiguration.queryParameters.top = 1;
            requestConfiguration.queryParameters.orderby = new String []{"displayName"};
            requestConfiguration.headers.add("ConsistencyLevel", "eventual");
        });

        if (result == null || CollectionUtils.isEmpty(result.getValue())) {
            log.warn("Unable to find Azure application[{}]", authProviderClient.getName());
            return null;
        }

        Application application = result.getValue().get(0);
        if (!CollectionUtils.isEmpty(application.getPasswordCredentials())) {
            return application.getPasswordCredentials().stream()
                    .filter(credentials -> Objects.requireNonNull(credentials.getDisplayName()).startsWith(authProviderClient.getSecretNamePrefix()))
                    .map(credentials -> {
                        boolean active = System.getenv().containsKey(authProviderClient.getSecretEnvVar()) && credentials.getHint() != null &&
                                System.getenv(authProviderClient.getSecretEnvVar()).startsWith(credentials.getHint());
                        return new CredentialInfo(credentials.getDisplayName(), credentials.getStartDateTime(),
                                credentials.getEndDateTime(), credentials.getHint(), active);
                    })
                    .peek(credentialInfo -> {
                        log.info("Registry Auth Provider Token Check [{}] (active:{} ageInDays:{} daysToExpiration:{})]",
                                credentialInfo.credentialName(), credentialInfo.active(),
                                credentialInfo.ageInDays(), credentialInfo.expirationInDays());
                    })
                    .collect(Collectors.toList());
        }
        return null;
    }

}
