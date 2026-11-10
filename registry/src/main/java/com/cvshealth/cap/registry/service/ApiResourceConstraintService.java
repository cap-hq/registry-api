// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.ApiForgeConfig;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeApi;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeConsumerEnvironment;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeConsumerEnvironmentApi;
import com.cvshealth.cap.registry.model.apiforge.RegistryApplicationResources;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.ResourceConstraintException;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Blocks changes to an application that still has API resources in ApiForge.
 *
 * <p>Used to guard two operations: deleting an application, and rotating an
 * application's external id (the correlation key used by downstream API integrations).
 *
 * <p>An application is constrained when either:
 * <ul>
 *     <li>it <b>owns</b> an API with a definition deployed to {@code blocking-environment}
 *     in one of {@code blocking-states}, or</li>
 *     <li>it <b>consumes</b> an API through a consumer environment in
 *     {@code blocking-environment} with an approval state in {@code blocking-approval-states}.</li>
 * </ul>
 *
 * <p>ApiForge does not expose a state on consumer environments, so {@code blocking-states}
 * applies to the ownership check only. All configured values are matched case-insensitively.
 *
 * <p>The check is a no-op when {@code app.api-resource-check.enabled} is false.
 */
@Service
@Slf4j
public class ApiResourceConstraintService {

    protected static final String QUERY_NAME = "appRegistryApplication";

    protected static final String APP_RESOURCES_QUERY = """
        query {
            appRegistryApplication(name: "%s") {
                name
                apis {
                    id
                    name
                    apiDefinitions {
                        id
                        majorVersion
                        state
                        environments {
                            id
                            environment
                            state
                        }
                    }
                }
                consumer {
                    id
                    consumerEnvironments {
                        id
                        environment
                        consumerEnvironmentApis {
                            approvalState
                            api {
                                id
                                name
                            }
                        }
                    }
                }
            }
        }
    """;

    private final ObjectProvider<WebClient> apiForgeWebClient;
    private final ApiForgeConfig config;
    private final RetryTemplate retryTemplate;

    public ApiResourceConstraintService(@Qualifier("apiForgeWebClient") ObjectProvider<WebClient> apiForgeWebClient,
                                        ApiForgeConfig config) {
        this.apiForgeWebClient = apiForgeWebClient;
        this.config = config;
        this.retryTemplate = RetryTemplate.builder()
                .exponentialBackoff(1000, 2, 10000)
                .maxAttempts(config.getMaxRetries())
                .build();
    }

    public void validateNoApiResourcesExist(String applicationName) {
        validateNoApiResourcesExist(applicationName,
                ExceptionReason.APPLICATION_API_FORGE_OWNERSHIP_CONSTRAINT,
                ExceptionReason.APPLICATION_API_FORGE_CONSUMPTION_CONSTRAINT);
    }

    /**
     * Blocks an external id change while the application still has API resources in ApiForge.
     * The external id is the correlation key used by downstream API integrations, so it cannot
     * be rotated while those relationships exist.
     */
    public void validateNoApiResourcesExistForExternalIdChange(String applicationName) {
        validateNoApiResourcesExist(applicationName,
                ExceptionReason.APPLICATION_EXTERNAL_ID_API_FORGE_OWNERSHIP_CONSTRAINT,
                ExceptionReason.APPLICATION_EXTERNAL_ID_API_FORGE_CONSUMPTION_CONSTRAINT);
    }

    private void validateNoApiResourcesExist(String applicationName,
                                             ExceptionReason ownershipReason,
                                             ExceptionReason consumptionReason) {
        WebClient webClient = apiForgeWebClient.getIfAvailable();
        if (!config.isEnabled() || webClient == null) {
            return;
        }

        RegistryApplicationResources resources;
        try {
            resources = retryTemplate.execute(context -> fetch(webClient, applicationName));
        } catch (Exception e) {
            log.error("Unable to reach ApiForge during validation for application {}", applicationName, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "external-resource-unreachable",
                    ExceptionReason.APPLICATION_DELETE_EXTERNAL_RESOURCE_UNREACHABLE,
                    String.format(ExceptionReason.APPLICATION_DELETE_EXTERNAL_RESOURCE_UNREACHABLE.label, "ApiForge"));
        }

        if (resources == null) {
            return;
        }

        List<String> ownedApis = ownedApiNames(resources);
        List<String> consumedApis = consumedApiNames(resources);

        if (ownedApis.isEmpty() && consumedApis.isEmpty()) {
            return;
        }

        List<String> constraints = new ArrayList<>();
        ownedApis.forEach(name -> constraints.add("owns API:" + name));
        consumedApis.forEach(name -> constraints.add("consumes API:" + name));

        log.info("ApiForge constraint blocked operation on application {} - environment: {}, owned APIs: {}, consumed APIs: {}",
                applicationName, config.getBlockingEnvironment(), ownedApis, consumedApis);

        if (!ownedApis.isEmpty()) {
            throw new ResourceConstraintException(ownershipReason, constraints);
        }
        throw new ResourceConstraintException(consumptionReason, constraints);
    }

    private RegistryApplicationResources fetch(WebClient webClient, String applicationName) {
        return HttpGraphQlClient.builder(webClient)
                .header("Authorization", "Bearer " + SecurityHelper.getBearerToken())
                .build()
                .document(String.format(APP_RESOURCES_QUERY, applicationName))
                .retrieve(QUERY_NAME)
                .toEntity(RegistryApplicationResources.class)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    private List<String> ownedApiNames(RegistryApplicationResources resources) {
        if (CollectionUtils.isEmpty(resources.getApis())) {
            return List.of();
        }
        return resources.getApis().stream()
                .filter(this::hasBlockingEnvironment)
                .map(ApiForgeApi::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean hasBlockingEnvironment(ApiForgeApi api) {
        if (CollectionUtils.isEmpty(api.getApiDefinitions())) {
            return false;
        }
        return api.getApiDefinitions().stream()
                .filter(definition -> !CollectionUtils.isEmpty(definition.getEnvironments()))
                .anyMatch(definition -> definition.getEnvironments().stream()
                        .anyMatch(environment -> isBlockingEnvironment(environment.getEnvironment())
                                && isBlockingState(environment.getState() != null
                                        ? environment.getState() : definition.getState())));
    }

    private List<String> consumedApiNames(RegistryApplicationResources resources) {
        if (resources.getConsumer() == null
                || CollectionUtils.isEmpty(resources.getConsumer().getConsumerEnvironments())) {
            return List.of();
        }
        return resources.getConsumer().getConsumerEnvironments().stream()
                .filter(environment -> isBlockingEnvironment(environment.getEnvironment()))
                .map(ApiForgeConsumerEnvironment::getConsumerEnvironmentApis)
                .filter(apis -> !CollectionUtils.isEmpty(apis))
                .flatMap(List::stream)
                .filter(this::isBlockingApprovalState)
                .map(ApiForgeConsumerEnvironmentApi::getApi)
                .filter(Objects::nonNull)
                .map(ApiForgeApi::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean isBlockingEnvironment(String environment) {
        return environment != null && environment.equalsIgnoreCase(config.getBlockingEnvironment());
    }

    private boolean isBlockingState(String state) {
        if (CollectionUtils.isEmpty(config.getBlockingStates())) {
            return true;
        }
        return containsIgnoreCase(config.getBlockingStates(), state);
    }

    private boolean isBlockingApprovalState(ApiForgeConsumerEnvironmentApi consumption) {
        return containsIgnoreCase(config.getBlockingApprovalStates(), consumption.getApprovalState());
    }

    private boolean containsIgnoreCase(List<String> configuredValues, String value) {
        return value != null && configuredValues.stream().anyMatch(value::equalsIgnoreCase);
    }
}

