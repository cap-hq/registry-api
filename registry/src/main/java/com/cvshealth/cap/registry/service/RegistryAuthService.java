// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.mapper.AuthClientMapper;
import com.cvshealth.cap.registry.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.metric.MetricAttributePair;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.model.OAuthClientExpiration;
import com.cvshealth.cap.registry.model.exception.AuthApiException;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import graphql.schema.DataFetchingFieldSelectionSet;
import io.leangen.graphql.execution.ResolutionEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.cvshealth.cap.registry.mapper.AuthClientMapper.buildAuthClientId;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.AUTH_API_ERROR;

@Service
public class RegistryAuthService {

    // above this many distinct client ids, fetching all clients is cheaper than a targeted search
    @Value("${app.auth-client.client-fetch-size:100}")
    int oauthClientFetchSize = 100;

    private final RestClient restClient;
    private final AuthClientMapper mapper;

    public RegistryAuthService(RestClient restClient, AuthClientMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    private static final List<String> OAUTH_CLIENT_SELECTIONS = List.of(
            "AuthClientView.clientSecret",
            "AuthClientView.type",
            "AuthClientView.redirectUrls",
            "AuthClientView.accessTokenExpireMinutes",
            "AuthClientView.refreshTokenExpireMinutes",
            "AuthClientView.lastAccessTokenIssuedAt",

            "ApplicationViewAuthClient.clientSecret",
            "ApplicationViewAuthClient.type",
            "ApplicationViewAuthClient.redirectUrls",
            "ApplicationViewAuthClient.accessTokenExpireMinutes",
            "ApplicationViewAuthClient.refreshTokenExpireMinutes",
            "ApplicationViewAuthClient.lastAccessTokenIssuedAt",

            "TeamViewAuthClient.clientSecret",
            "TeamViewAuthClient.type",
            "TeamViewAuthClient.redirectUrls",
            "TeamViewAuthClient.accessTokenExpireMinutes",
            "TeamViewAuthClient.refreshTokenExpireMinutes",
            "TeamViewAuthClient.lastAccessTokenIssuedAt",

            "MemberViewAuthClient.clientSecret",
            "MemberViewAuthClient.type",
            "MemberViewAuthClient.redirectUrls",
            "MemberViewAuthClient.accessTokenExpireMinutes",
            "MemberViewAuthClient.refreshTokenExpireMinutes",
            "MemberViewAuthClient.lastAccessTokenIssuedAt"
    );

    // this method will determine if the graphQL query includes any auth client fields that are
    // sourced from the app reg auth api, and ONLY fetch those as needed to minimize wire traffic
    private static boolean enhanceAuthClient(ResolutionEnvironment graphQLEnv) {
        if (graphQLEnv == null) {
            return false;
        }
        DataFetchingFieldSelectionSet selectionSet = graphQLEnv.dataFetchingEnvironment.getSelectionSet();
        return OAUTH_CLIENT_SELECTIONS.stream().anyMatch(oas -> selectionSet.getFields().stream()
                .anyMatch(sf -> sf.getFullyQualifiedName().contains(oas)));
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "getClientsByIds")
            })
    public List<OAuthClient> getClientsByIds(List<String> clientIds, ResolutionEnvironment graphQLEnv) {
        if (enhanceAuthClient(graphQLEnv)) {
            return getClientsByIds(clientIds);
        }
        return null;
    }

    /**
     * Fetches only the auth API clients needed for the given (versioned) client ids, and only when the
     * graphQL query selects auth API sourced fields. The supplier is evaluated lazily so that callers can
     * walk lazy entity associations without cost when no auth fields are requested.
     */
    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "getClientsForIds")
            })
    public List<OAuthClient> getClientsForIds(Supplier<? extends Collection<String>> clientIdSupplier, ResolutionEnvironment graphQLEnv) {
        if (!enhanceAuthClient(graphQLEnv)) {
            return null;
        }
        List<String> clientIds = clientIdSupplier.get().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (clientIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (clientIds.size() > oauthClientFetchSize) {
            return getClients();
        }
        return getClientsByIds(clientIds);
    }

    static Stream<String> teamAuthClientIds(TeamViewEntity team) {
        if (team == null || team.getTeamAuthClients() == null) {
            return Stream.empty();
        }
        return team.getTeamAuthClients().stream()
                .map(tac -> tac.getId().getAuthClient())
                .map(authClient -> buildAuthClientId(authClient.getClientId(), authClient.getClientVersion()));
    }

    public List<OAuthClient> getClients() {
        return restClient.get()
                .uri("/clients")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<OAuthClient> getClientsByType(AuthClientType clientType) {
        return restClient.get()
                .uri("/clients/type/" + clientType)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<OAuthClient> getClientsByIds(List<String> clientIds) {
        return restClient.post()
                .uri("/clients/search")
                .body(clientIds)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "getClient")
            })
    public OAuthClient getClient(String clientId) {
        return restClient.get()
                .uri("/client/" + clientId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(OAuthClient.class);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "createClients")
            })
    public OAuthClient createClient(OAuthClient oAuthClient) {
        return restClient.post()
                .uri("/client")
                .body(oAuthClient)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(OAuthClient.class);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "resetClientSecret")
            })
    public OAuthClient resetClientSecret(String clientId) {
        return restClient.get()
                .uri("/client/" + clientId + "/secret")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(OAuthClient.class);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "updateClient")
            })
    public OAuthClient updateClient(OAuthClient oAuthClient) {
        return restClient.put()
                .uri("/client")
                .body(oAuthClient)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(OAuthClient.class);
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "deleteClient")
            })
    public void deleteClient(String clientId) {
        ResponseEntity<Void> response = restClient.delete()
                .uri("/client/" + clientId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .toBodilessEntity();
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST,
            attributes = {
                    @MetricAttributePair(key = "client", value = "registry-auth"),
                    @MetricAttributePair(key = "method", value = "getClientExpirations")
            })
    public List<OAuthClientExpiration> getClientExpirations() {
        return restClient.get()
                .uri("/clientExpirations")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleError)
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        try {
            byte[] stringBytes = response.getBody().readAllBytes();
            String body = new String(stringBytes);
            throw new AuthApiException(HttpStatus.valueOf(response.getStatusCode().value()), body);
        } catch (AuthApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthApiException(HttpStatus.valueOf(response.getStatusCode().value()), AUTH_API_ERROR.label);
        }
    }

}
