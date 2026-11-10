// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.Application;
import com.cvshealth.cap.registry.auth.model.AuthClient;
import com.cvshealth.cap.registry.auth.model.CustomClaim;
import com.cvshealth.cap.registry.auth.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RegistryApiServiceTests {

    @Mock
    private MemberRequestCache memberRequestCache;

    @Mock
    private AuthClientRequestCache authClientRequestCache;

    @Mock
    private ExchangeFunction exchangeFunction;

    private RegistryApiService service;

    @BeforeEach
    void setUp() {
        service = serviceWithClaims(Collections.emptyList(), Collections.emptyList());
    }

    // ─── getMember ───────────────────────────────────────────────────────────

    @Test
    void getMember_returnsCachedMember_whenCacheHit() {
        Member member = Member.builder().externalId("ext-001").firstName("Jane").build();
        when(memberRequestCache.get("ext-001", false)).thenReturn(Optional.of(Optional.of(member)));

        Member result = service.getMember("ext-001", false);

        assertThat(result).isEqualTo(member);
        verifyNoInteractions(exchangeFunction);
    }

    @Test
    void getMember_returnsNull_whenCacheHasEmptyValue() {
        when(memberRequestCache.get("ext-001", false)).thenReturn(Optional.of(Optional.empty()));

        Member result = service.getMember("ext-001", false);

        assertThat(result).isNull();
        verifyNoInteractions(exchangeFunction);
    }

    @Test
    void getMember_fetchesMemberAndCachesResult_whenCacheMiss_partialGraph() {
        when(memberRequestCache.get("ext-001", false)).thenReturn(Optional.empty());

        stubExchange("""
                {"data":{"getMemberByExternalId":{"name":"Jane Doe","firstName":"Jane","lastName":"Doe","externalId":"ext-001","externalAlias":"ext-alias"}}}
                """);

        Member result = service.getMember("ext-001", false);

        assertThat(result).isNotNull();
        assertThat(result.getExternalId()).isEqualTo("ext-001");
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getExternalAlias()).isEqualTo("ext-alias");
        verify(memberRequestCache).put("ext-001", false, Optional.of(result));
    }

    @Test
    void getMember_fetchesMemberAndCachesResult_whenCacheMiss_fullGraph() {
        when(memberRequestCache.get("ext-001", true)).thenReturn(Optional.empty());

        stubExchange("""
                {"data":{"getMemberByExternalId":{"name":"Jane Doe","firstName":"Jane","lastName":"Doe","externalId":"ext-001","externalAlias":"ext-alias","teams":[]}}}
                """);

        Member result = service.getMember("ext-001", true);

        assertThat(result).isNotNull();
        assertThat(result.getExternalId()).isEqualTo("ext-001");
        assertThat(result.getTeams()).isEmpty();
        verify(memberRequestCache).put("ext-001", true, Optional.of(result));
    }

    @Test
    void getMember_returnsNullAndCachesEmpty_whenMemberNotFound() {
        when(memberRequestCache.get("ext-999", false)).thenReturn(Optional.empty());

        stubExchange("{\"data\":{\"getMemberByExternalId\":null}}");

        Member result = service.getMember("ext-999", false);

        assertThat(result).isNull();
        verify(memberRequestCache).put("ext-999", false, Optional.empty());
    }

    @Test
    void getMember_sendsPartialGraphQuery_whenFullGraphIsFalse() {
        when(memberRequestCache.get("ext-001", false)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\"}}}");

        service.getMember("ext-001", false);

        String requestBody = captureRequestBody();
        assertThat(requestBody).contains("getMemberByExternalId");
        assertThat(requestBody).doesNotContain("teams");
    }

    @Test
    void getMember_sendsFullGraphQuery_whenFullGraphIsTrue() {
        when(memberRequestCache.get("ext-001", true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\",\"teams\":[]}}}");

        service.getMember("ext-001", true);

        String requestBody = captureRequestBody();
        assertThat(requestBody).contains("teams");
    }

    @Test
    void getMember_sendsApiSecretHeader() {
        when(memberRequestCache.get("ext-001", false)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\"}}}");

        service.getMember("ext-001", false);

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());
        assertThat(captor.getValue().headers().getFirst("X-Api-Secret"))
                .isEqualTo("test-secret");
    }

    // ─── getAuthClient ────────────────────────────────────────────────────────

    @Test
    void getAuthClient_returnsCachedClient_whenCacheHit() {
        AuthClient authClient = AuthClient.builder().clientId("client-001").build();
        when(authClientRequestCache.get("client-001", 1, false)).thenReturn(Optional.of(Optional.of(authClient)));

        AuthClient result = service.getAuthClient("client-001", 1, false);

        assertThat(result).isEqualTo(authClient);
        verifyNoInteractions(exchangeFunction);
    }

    @Test
    void getAuthClient_returnsNull_whenCacheHasEmptyValue() {
        when(authClientRequestCache.get("client-001", 1, false)).thenReturn(Optional.of(Optional.empty()));

        AuthClient result = service.getAuthClient("client-001", 1, false);

        assertThat(result).isNull();
        verifyNoInteractions(exchangeFunction);
    }

    @Test
    void getAuthClient_fetchesClientAndCachesResult_whenCacheMiss_partialGraph() {
        when(authClientRequestCache.get("client-001", 1, false)).thenReturn(Optional.empty());

        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-001\",\"teams\":[]}}}");

        AuthClient result = service.getAuthClient("client-001", 1, false);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo("client-001");
        verify(authClientRequestCache).put("client-001", 1, false, Optional.of(result));
    }

    @Test
    void getAuthClient_fetchesClientAndCachesResult_whenCacheMiss_fullGraph() {
        when(authClientRequestCache.get("client-001", 2, true)).thenReturn(Optional.empty());

        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-001\",\"teams\":[]}}}");

        AuthClient result = service.getAuthClient("client-001", 2, true);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo("client-001");
        verify(authClientRequestCache).put("client-001", 2, true, Optional.of(result));
    }

    @Test
    void getAuthClient_returnsNullAndCachesEmpty_whenClientNotFound() {
        when(authClientRequestCache.get("client-999", 1, false)).thenReturn(Optional.empty());

        stubExchange("{\"data\":{\"getAuthClient\":null}}");

        AuthClient result = service.getAuthClient("client-999", 1, false);

        assertThat(result).isNull();
        verify(authClientRequestCache).put("client-999", 1, false, Optional.empty());
    }

    @Test
    void getAuthClient_sendsPartialGraphQuery_whenFullGraphIsFalse() {
        when(authClientRequestCache.get("client-001", 1, false)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-001\"}}}");

        service.getAuthClient("client-001", 1, false);

        String requestBody = captureRequestBody();
        assertThat(requestBody).contains("getAuthClient");
        assertThat(requestBody).doesNotContain("applications");
    }

    @Test
    void getAuthClient_sendsFullGraphQuery_whenFullGraphIsTrue() {
        when(authClientRequestCache.get("client-001", 1, true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-001\",\"teams\":[]}}}");

        service.getAuthClient("client-001", 1, true);

        String requestBody = captureRequestBody();
        assertThat(requestBody).contains("applications");
    }

    @Test
    void getAuthClient_sendsApiSecretHeader() {
        when(authClientRequestCache.get("client-001", 1, false)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-001\"}}}");

        service.getAuthClient("client-001", 1, false);

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());
        assertThat(captor.getValue().headers().getFirst("X-Api-Secret"))
                .isEqualTo("test-secret");
    }

    // ─── informationClassification is requested only when a claim filters on it ──

    @Test
    void getMember_omitsInformationClassification_whenNoClaimFiltersOnIt() {
        when(memberRequestCache.get("ext-001", true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\"}}}");

        serviceWithClaims(List.of(tagClaim()), Collections.emptyList()).getMember("ext-001", true);

        assertThat(captureRequestBody()).doesNotContain("informationClassification");
    }

    @Test
    void getMember_requestsInformationClassification_whenAccessTokenClaimFiltersOnIt() {
        when(memberRequestCache.get("ext-001", true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\"}}}");

        serviceWithClaims(List.of(classificationClaim()), Collections.emptyList()).getMember("ext-001", true);

        assertThat(captureRequestBody()).contains("informationClassification");
    }

    @Test
    void getMember_requestsInformationClassification_whenIdTokenClaimFiltersOnIt() {
        when(memberRequestCache.get("ext-001", true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\"}}}");

        serviceWithClaims(Collections.emptyList(), List.of(classificationClaim())).getMember("ext-001", true);

        assertThat(captureRequestBody()).contains("informationClassification");
    }

    @Test
    void getMember_omitsInformationClassification_whenClaimFiltersOnItButNotFromRegistry() {
        when(memberRequestCache.get("ext-001", true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"ext-001\"}}}");
        CustomClaim fromAuthParameter = classificationClaim();
        fromAuthParameter.setSource(CustomClaim.SOURCE_AUTH_PARAMETER);

        serviceWithClaims(List.of(fromAuthParameter), Collections.emptyList()).getMember("ext-001", true);

        assertThat(captureRequestBody()).doesNotContain("informationClassification");
    }

    @Test
    void getAuthClient_omitsInformationClassification_whenNoClaimFiltersOnIt() {
        when(authClientRequestCache.get("client-1", 1, true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-1\"}}}");

        serviceWithClaims(List.of(tagClaim()), Collections.emptyList()).getAuthClient("client-1", 1, true);

        assertThat(captureRequestBody()).doesNotContain("informationClassification");
    }

    @Test
    void getAuthClient_requestsInformationClassification_whenClaimFiltersOnIt() {
        when(authClientRequestCache.get("client-1", 1, true)).thenReturn(Optional.empty());
        stubExchange("{\"data\":{\"getAuthClient\":{\"clientId\":\"client-1\"}}}");

        serviceWithClaims(List.of(classificationClaim()), Collections.emptyList()).getAuthClient("client-1", 1, true);

        assertThat(captureRequestBody()).contains("informationClassification");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private RegistryApiService serviceWithClaims(List<CustomClaim> claims, List<CustomClaim> identityClaims) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .defaultHeader("X-Api-Secret", "test-secret")
                .exchangeFunction(exchangeFunction)
                .build();
        return new RegistryApiService(webClient, memberRequestCache, authClientRequestCache, claims, identityClaims);
    }

    private static CustomClaim classificationClaim() {
        return CustomClaim.builder().name("app_information_classification_restricted").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().informationClassification("RESTRICTED").build()).build();
    }

    private static CustomClaim tagClaim() {
        return CustomClaim.builder().name("app_pci").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().complianceTypes(java.util.Set.of("pci")).build()).build();
    }

    private void stubExchange(String responseJson) {
        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(responseJson)
                .build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(mockResponse));
    }

    private String captureRequestBody() {
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());
        ClientRequest captured = captor.getValue();

        MockClientHttpRequest mockHttpRequest = new MockClientHttpRequest(
                HttpMethod.POST, URI.create("http://localhost:8080"));

        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        BodyInserter.Context context = new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return strategies.messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Collections.emptyMap();
            }
        };

        captured.body().insert(mockHttpRequest, context).block();

        return DataBufferUtils.join(mockHttpRequest.getBody())
                .map(buf -> {
                    try {
                        return buf.toString(StandardCharsets.UTF_8);
                    } finally {
                        DataBufferUtils.release(buf);
                    }
                })
                .defaultIfEmpty("")
                .block();
    }
}
