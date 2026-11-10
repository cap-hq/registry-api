// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.auth.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.auth.metric.MetricAttributePair;
import com.cvshealth.cap.registry.auth.model.AuthClient;
import com.cvshealth.cap.registry.auth.model.CustomClaim;
import com.cvshealth.cap.registry.auth.model.Member;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class RegistryApiService {
    private final HttpGraphQlClient graphQlClient;
    private final MemberRequestCache memberRequestCache;
    private final AuthClientRequestCache authClientRequestCache;
    // "informationClassification" when a configured claim filters on it, otherwise "". Older registries do not know the
    // field, and an unknown field fails the whole query, so it is only requested when something needs it.
    private final String applicationExtraFields;

    protected static String GRAPHQL_MEMBER_INFO_QUERY = """
        query {
            getMemberByExternalId(externalId: "%s") {
                name
                firstName
                lastName
                externalId
                externalAlias
            }
        }
        """;

    protected static String GRAPHQL_MEMBER_QUERY = """
        query {
            getMemberByExternalId(externalId: "%s") {
                name
                firstName
                lastName
                externalId
                externalAlias
                teams {
                    name
                    type
                    role
                    applications {
                        name
                        complianceTypes
                        externalId
                        %s
                    }
                }
            }
        }
        """;

    protected static String GRAPHQL_CLIENT_VERSION_INFO_QUERY = """
        query {
            getAuthClient(clientId: "%s", clientVersion: %d) {
                clientId
                clientVersion
                teams {
                    name
                    role
                }
            }
        }
        """;

    protected static String GRAPHQL_CLIENT_QUERY = """
        query {
            getAuthClient(clientId: "%s", clientVersion: %d) {
                clientId
                clientVersion
                teams {
                    name
                    type
                    role
                    applications {
                        name
                        complianceTypes
                        externalId
                        %s
                    }
                }
            }
        }
        """;

    public RegistryApiService(WebClient webClient, MemberRequestCache memberRequestCache, AuthClientRequestCache authClientRequestCache,
                                 @Qualifier("customClaims") List<CustomClaim> customClaims,
                                 @Qualifier("customIdentityClaims") List<CustomClaim> customIdentityClaims) {
        this.graphQlClient = HttpGraphQlClient.builder(webClient).build();
        this.memberRequestCache = memberRequestCache;
        this.authClientRequestCache = authClientRequestCache;
        this.applicationExtraFields = filtersOnInformationClassification(customClaims)
                || filtersOnInformationClassification(customIdentityClaims) ? "informationClassification" : "";
    }

    private static boolean filtersOnInformationClassification(List<CustomClaim> claims) {
        return claims != null && claims.stream()
                .filter(CustomClaim::isSourceRegistry)
                .anyMatch(CustomClaim::filtersOnInformationClassification);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry"),
                    @MetricAttributePair(key = "query", value = "getMemberByExternalId")
            })
    public Member getMember(String externalId, boolean fullGraph) {
        Optional<Optional<Member>> cached = memberRequestCache.get(externalId, fullGraph);
        if (cached.isPresent()) {
            return cached.get().orElse(null);
        }
        Member member = graphQlClient
                .document(String.format(fullGraph ? GRAPHQL_MEMBER_QUERY : GRAPHQL_MEMBER_INFO_QUERY, externalId, applicationExtraFields))
                .retrieve("getMemberByExternalId")
                .toEntity(Member.class)
                .retryWhen(Retry.backoff(3, Duration.of(2, ChronoUnit.SECONDS))
                        .filter(e -> !(e instanceof WebClientResponseException &&
                                ((WebClientResponseException) e).getStatusCode().value() == 500))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                retrySignal.failure()))
                .block();
        memberRequestCache.put(externalId, fullGraph, Optional.ofNullable(member));
        return member;
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry"),
                    @MetricAttributePair(key = "query", value = "getAuthClient")
            })
    public AuthClient getAuthClient(String clientId, int clientVersion, boolean fullGraph) {
        Optional<Optional<AuthClient>> cached = authClientRequestCache.get(clientId, clientVersion, fullGraph);
        if (cached.isPresent()) {
            return cached.get().orElse(null);
        }
        AuthClient authClient = graphQlClient
                .document(String.format(fullGraph ? GRAPHQL_CLIENT_QUERY : GRAPHQL_CLIENT_VERSION_INFO_QUERY, clientId, clientVersion, applicationExtraFields))
                .retrieve("getAuthClient")
                .toEntity(AuthClient.class)
                .retryWhen(Retry.backoff(3, Duration.of(2, ChronoUnit.SECONDS))
                        .filter(e -> !(e instanceof WebClientResponseException &&
                                ((WebClientResponseException) e).getStatusCode().value() == 500))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                retrySignal.failure()))
                .block();
        authClientRequestCache.put(clientId, clientVersion, fullGraph, Optional.ofNullable(authClient));
        return authClient;
    }

}
