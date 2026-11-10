// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.CacheConfig;
import com.cvshealth.cap.registry.rollup.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.rollup.annotation.FeatureFlag;
import com.cvshealth.cap.registry.rollup.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.rollup.metric.MetricAttributePair;
import com.cvshealth.cap.registry.rollup.model.LineBusinessView;
import com.cvshealth.cap.registry.rollup.model.LineBusinessManagerView;
import com.cvshealth.cap.registry.rollup.model.LineOfBusinessView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LineOfBusinessService extends RegistryService {
    protected static String LEGACY_GRAPHQL_QUERY = """
        query {
            getLinesOfBusiness {
                name
                description
                managerExternalId
                managerName
            }
        }
        """;

    protected static String GRAPHQL_QUERY = """
        query {
            getLinesOfBusiness {
                name
                description
                managers {
                    managerExternalId
                    managerName
                }
            }
        }
        """;

    @FeatureFlag("app.line-business.enabled")
    boolean lineOfBusinessEnabled;

    public LineOfBusinessService(WebClient webClient) {
        super(webClient);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry"),
                    @MetricAttributePair(key = "query", value = "getLinesOfBusiness")
            })
    public List<LineBusinessView> getLinesOfBusiness() {
        String query = String.format(GRAPHQL_QUERY);
        try {
            return constructGraphQLClient()
                    .document(query)
                    .retrieve("getLinesOfBusiness")
                    .toEntityList(LineBusinessView.class)
                    .retryWhen(Retry.backoff(3, Duration.of(1, ChronoUnit.SECONDS))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    retrySignal.failure()))
                    .block();
        } catch (FieldAccessException e) {
            log.warn("Failed to fetch lines of business, falling back to legacy data", e);
            List<LineOfBusinessView> lobs = getLegacyLinesOfBusiness();
            if (lobs == null) {
                return null;
            }
            return lobs.stream()
                    .map(lob -> LineBusinessView.builder()
                            .name(lob.getName())
                            .description(lob.getDescription())
                            .managers(List.of(LineBusinessManagerView.builder()
                                            .managerExternalId(lob.getManagerExternalId())
                                            .managerName(lob.getManagerName())
                                            .build()))
                            .build())
                    .toList();
        }
    }

    private List<LineOfBusinessView> getLegacyLinesOfBusiness() {
        String query = String.format(LEGACY_GRAPHQL_QUERY);
        return constructGraphQLClient()
                .document(query)
                .retrieve("getLinesOfBusiness")
                .toEntityList(LineOfBusinessView.class)
                .block();
    }

    @Cacheable(value = CacheConfig.CACHE_LOB)
    public Map<String, LineOfBusinessView> managerToLobMap() {
        List<LineBusinessView> linesOfBusiness = lineOfBusinessEnabled ? getLinesOfBusiness() : Collections.emptyList();
        if (CollectionUtils.isEmpty(linesOfBusiness)) {
            return Collections.emptyMap();
        }
        // Handle one LOB to many managers (one manager can only be mapped to one LOB)
        return linesOfBusiness.stream()
                .filter(lob -> !CollectionUtils.isEmpty(lob.getManagers()))
                .flatMap(lob -> lob.getManagers().stream()
                        .map(manager -> Map.entry(manager.getManagerExternalId(), mapLobManagerToLobView(manager, lob))))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, duplicate) -> {
                            log.error(
                                    "Data integrity error - duplicate managerExternalId '{}' mapped to multiple lines of business: '{}' and '{}'",
                                    existing.getManagerExternalId(),
                                    existing.getName(),
                                    duplicate.getName());
                            throw new IllegalStateException(String.format(
                                    "Data integrity error - duplicate managerExternalId '%s' mapped to multiple lines of business: '%s' and '%s'",
                                    existing.getManagerExternalId(),
                                    existing.getName(),
                                    duplicate.getName()));
                        }));
    }

    private LineOfBusinessView mapLobManagerToLobView(LineBusinessManagerView lobManager, LineBusinessView lob) {
        return LineOfBusinessView.builder()
                .name(lob.getName())
                .description(lob.getDescription())
                .managerExternalId(lobManager.getManagerExternalId())
                .managerName(lobManager.getManagerName())
                .build();
    }
}
