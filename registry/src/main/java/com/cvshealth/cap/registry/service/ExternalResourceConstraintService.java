// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.CapResource;
import com.cvshealth.cap.registry.model.ExternalResourceResponse;
import com.cvshealth.cap.registry.model.LabeledClient;
import com.cvshealth.cap.registry.model.ServiceResourceConstraint;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.ResourceConstraintException;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ExternalResourceConstraintService {

    @Qualifier("externalResourceClients")
    private final List<LabeledClient> externalResourceClients;

    @Value("${app.external-resource-check.graphql.query-name}")
    private String graphQLQueryName;

    @Value("${app.external-resource-check.rest.endpoint}")
    private String restEndpoint;

    private final RetryTemplate getRetryTemplate;

    protected static String GRAPHQL_RESOURCES_QUERY = """
        query {
            %s(applicationName: "%s") {
                name
                type
                environment
            }
        }
    """;

    public ExternalResourceConstraintService(List<LabeledClient> externalResourceClients,
                                             @Value("${app.external-resource-check.rest.max-retries}") int restMaxRetries) {
        this.externalResourceClients = externalResourceClients;
        this.getRetryTemplate = RetryTemplate.builder()
                .exponentialBackoff(1000, 2, 10000)
                .maxAttempts(restMaxRetries).build();
    }

    public void validateNoExternalResourcesExist(String applicationName) {
        validateNoExternalResourcesExist(applicationName, ExceptionReason.APPLICATION_DELETE_EXTERNAL_RESOURCE_UNREACHABLE);
    }

    /**
     * @param unreachableReason what to report when an external source cannot be reached; its label takes the
     *                          source's name, so callers can word it for what they are guarding (delete, update, ...)
     */
    public void validateNoExternalResourcesExist(String applicationName, ExceptionReason unreachableReason) {
        final List<CapResource> externalResources = new ArrayList<>();
        externalResourceClients.forEach(client -> {
            try {
                switch (client.getClientType()) {
                    case REST -> {
                        if (client.getRestClient() != null) {
                            externalResources.addAll(getRetryTemplate.execute(context -> fetchRest(client, applicationName)));
                        }
                    }
                    case GRAPHQL -> {
                        if (client.getWebClient() != null) {
                            WebClient webClient = client.getWebClient().get();
                            if (webClient != null) {
                                externalResources.addAll(getCapResources(webClient, applicationName));
                            }
                        }
                    }
                }
            } catch (BadRequestException | ResourceConstraintException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "external-resource-unreachable",
                        unreachableReason,
                        String.format(unreachableReason.label, client.getLabel()));
            }
        });
        if (!CollectionUtils.isEmpty(externalResources)) {
            List<String> formattedErrorMessages = externalResources.stream().map(resource -> resource.getEnvironment() != null ?
                    String.format("%s:%s[%s]", resource.getType(), resource.getName(), resource.getEnvironment()) :
                    String.format("%s:%s", resource.getType(), resource.getName())).toList();
            throw new ResourceConstraintException(ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT, formattedErrorMessages);
        }
    }

    private List<CapResource> fetchRest(LabeledClient client, String applicationName)  {
        List<CapResource> resp = client.getRestClient().get()
                .uri(String.format("/%s/%s", restEndpoint, applicationName))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (resp == null) {
            return Collections.emptyList();
        }
        return resp;
    }

    public List<CapResource> getCapResources(WebClient webClient, String applicationName) {
        return graphQLClient(webClient)
                .document(String.format(GRAPHQL_RESOURCES_QUERY, graphQLQueryName, applicationName))
                .retrieve(graphQLQueryName)
                .toEntityList(CapResource.class)
                .block();
    }

    private HttpGraphQlClient graphQLClient(WebClient webClient) {
        return HttpGraphQlClient.builder(webClient)
                .header("Authorization", "Bearer " + SecurityHelper.getBearerToken())
                .build();
    }
}
