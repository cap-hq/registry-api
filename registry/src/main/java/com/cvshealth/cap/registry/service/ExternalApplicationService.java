// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.config.ExternalApplicationClientAuthConfig;
import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.metric.MetricAttributePair;
import com.cvshealth.cap.registry.model.ModelPair;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.externalApplication.AuthToken;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Service
@Slf4j
public class ExternalApplicationService {
    private final Supplier<WebClient> externalAppApiWebClient;
    private final Supplier<WebClient> externalAppAuthWebClient;
    private final ExternalApplicationClientAuthConfig externalAppAuthConfig;

    private final AtomicReference<ModelPair<String, Long>> apiAuthToken = new AtomicReference<>();

    private static final String CVS_APP_ID_QUERY = "/apm/application?queryType=CVS_APP_ID&id=%s";
    private static final String ACCEPT_EXTERNAL_APP_JSON = "application/vnd.snapii.v2+json";

    public ExternalApplicationService(@Qualifier("externalAppApiWebClient") Supplier<WebClient> externalAppApiWebClient,
                                      @Qualifier("externalAppAuthWebClient") Supplier<WebClient> externalAppAuthWebClient,
                                      ExternalApplicationClientAuthConfig externalAppAuthConfig) {
        this.externalAppApiWebClient = externalAppApiWebClient;
        this.externalAppAuthWebClient = externalAppAuthWebClient;
        this.externalAppAuthConfig = externalAppAuthConfig;
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "external-application"),
                    @MetricAttributePair(key = "method", value = "getExternalApplication")
            })
    @Cacheable(value = CacheConfig.CACHE_EXTERNAL_APP_INFO)
    public ExternalApplication getExternalApplication(String externalId) {
        return constructClient(HttpMethod.GET, String.format(CVS_APP_ID_QUERY, externalId))
                .header(HttpHeaders.ACCEPT, ACCEPT_EXTERNAL_APP_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(ExternalApplication.class);
                    } else if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return Mono.empty();
                    } else {
                        log.error("Unexpected externalApplicationService response code: {}", response.statusCode());
                        return Mono.error(new ApiException(HttpStatus.valueOf(response.statusCode().value()), "externalApplicationService",
                                    ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR, "could not retrieve external application"));
                    }
                })
                .block(Duration.of(externalAppAuthConfig.getTimeoutInSeconds(), ChronoUnit.SECONDS));
    }

    private WebClient.RequestBodySpec constructClient(HttpMethod method, String uri) {
        WebClient apiClient = externalAppApiWebClient.get();
        if (apiClient == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "externalApplicationService",
                    ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR, "external-application-api.base-uri is not configured");
        }
        return apiClient
                .method(method)
                .uri(uri)
                .header("Authorization", "Bearer " + getBearerToken());
    }

    String getBearerToken() {
        ModelPair<String, Long> tokenInfo = apiAuthToken.get();
        if (refreshToken(tokenInfo)) {
            AuthToken authToken = retrieveBearerToken();
            if (authToken == null || authToken.getAccessToken() == null) {
                log.error("could not parse external application api auth token");
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "externalApplicationService",
                        ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR, "could not retrieve external application service authorization token");
            }
            long expirationInMillis = 1000L * (authToken.getExpiresIn() - externalAppAuthConfig.getRefreshBufferInSeconds());
            tokenInfo = new ModelPair<>(authToken.getAccessToken(), System.currentTimeMillis() + expirationInMillis);
            apiAuthToken.set(tokenInfo);
        }
        return tokenInfo.getFirst();
    }

    private boolean refreshToken(ModelPair<String, Long> tokenInfo) {
        return tokenInfo == null || System.currentTimeMillis() > tokenInfo.getSecond();
    }

    AuthToken retrieveBearerToken() {
        WebClient authClient = externalAppAuthWebClient.get();
        if (authClient == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "externalApplicationService",
                    ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR, "external-application-api.auth-provider.base-uri is not configured");
        }
        return authClient.post().uri("/oauth2/token")
                .body(BodyInserters.fromFormData("grant_type", externalAppAuthConfig.getGrantType())
                        .with("scope", externalAppAuthConfig.getScope()))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + java.util.Base64.getEncoder().encodeToString((externalAppAuthConfig.getClientId() + ":" + externalAppAuthConfig.getClientSecret()).getBytes()))
                .retrieve()
                .bodyToMono(AuthToken.class)
                .onErrorMap(e -> {
                    log.error("could not retrieve external application service authorization token", e);
                    return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "externalApplicationService",
                            ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR, "could not retrieve external application service authorization token");
                })
                .block(Duration.of(externalAppAuthConfig.getTimeoutInSeconds(), ChronoUnit.SECONDS));
    }

    void clearApiAuthToken() {
        apiAuthToken.set(null);
    }
}
