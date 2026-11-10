// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.ApiForgeConfig;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeApi;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeApiDefinition;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeApiDefinitionEnvironment;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeConsumer;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeConsumerEnvironment;
import com.cvshealth.cap.registry.model.apiforge.ApiForgeConsumerEnvironmentApi;
import com.cvshealth.cap.registry.model.apiforge.RegistryApplicationResources;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.ResourceConstraintException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiResourceConstraintServiceTests {

    private static final String APPLICATION_NAME = "apiforge";
    private static final int PORT = 8794;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WireMockServer wireMockServer;
    private ApiForgeConfig config;
    private ApiResourceConstraintService service;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().port(PORT));
        wireMockServer.start();

        config = new ApiForgeConfig();
        config.setEnabled(true);
        config.setUrl(String.format("http://localhost:%s/graphql", PORT));
        config.setMaxRetries(1);
        config.setTimeoutSeconds(5);

        service = new ApiResourceConstraintService(webClientProvider(config.getUrl()), config);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void allowsDeleteWhenNoApiResourcesExist() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of())
                .build());

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void allowsDeleteWhenApplicationIsUnknownToApiForge() {
        stubResources(null);

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void blocksDeleteWhenProductionOwnedApiExists() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "prismruleset", "production")))
                .build());

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ResourceConstraintException.class)
                .satisfies(thrown -> {
                    ResourceConstraintException e = (ResourceConstraintException) thrown;
                    assertThat(e.getReason()).isEqualTo(ExceptionReason.APPLICATION_API_FORGE_OWNERSHIP_CONSTRAINT);
                    assertThat(e.getMessage()).contains("owns API:prismruleset");
                });
    }

    @Test
    void allowsDeleteWhenOwnedApiIsOnlyInDevelopment() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "prismruleset", "development")))
                .build());

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void allowsDeleteWhenOwnedApiHasNoDefinitions() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(ApiForgeApi.builder().id("1").name("prismruleset").build()))
                .build());

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void allowsDeleteWhenOwnedApiEnvironmentStateIsNotBlocking() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "prismruleset", "production", "retired")))
                .build());

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void blocksDeleteWhenConfiguredEnvironmentAndStateMatch() {
        config.setBlockingEnvironment("development");
        config.setBlockingStates(List.of("draft"));
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "prismruleset", "development", "draft")))
                .build());

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ResourceConstraintException.class)
                .hasMessageContaining("owns API:prismruleset");
    }

    @Test
    void blocksDeleteWhenStateFilteringIsDisabled() {
        config.setBlockingStates(List.of());
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "prismruleset", "production", null)))
                .build());

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ResourceConstraintException.class)
                .hasMessageContaining("owns API:prismruleset");
    }

    @Test
    void matchesConfiguredValuesCaseInsensitively() {
        config.setBlockingEnvironment("PRODUCTION");
        config.setBlockingStates(List.of("Active"));
        config.setBlockingApprovalStates(List.of("Approved"));
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "ownedapi", "production", "active")))
                .consumer(consumer("production", "approved", "consumedapi"))
                .build());

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ResourceConstraintException.class)
                .hasMessageContaining("owns API:ownedapi")
                .hasMessageContaining("consumes API:consumedapi");
    }

    @Test
    void blocksDeleteWhenProductionConsumedApiExists() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of())
                .consumer(consumer("production", "approved", "prismruleset"))
                .build());

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ResourceConstraintException.class)
                .satisfies(thrown -> {
                    ResourceConstraintException e = (ResourceConstraintException) thrown;
                    assertThat(e.getReason()).isEqualTo(ExceptionReason.APPLICATION_API_FORGE_CONSUMPTION_CONSTRAINT);
                    assertThat(e.getMessage()).contains("consumes API:prismruleset");
                });
    }

    @Test
    void allowsDeleteWhenConsumptionIsRevoked() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of())
                .consumer(consumer("production", "revoked", "prismruleset"))
                .build());

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void allowsDeleteWhenConsumptionIsOnlyInDevelopment() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of())
                .consumer(consumer("development", "approved", "prismruleset"))
                .build());

        service.validateNoApiResourcesExist(APPLICATION_NAME);
    }

    @Test
    void reportsOwnershipConstraintWhenApplicationBothOwnsAndConsumes() {
        stubResources(RegistryApplicationResources.builder()
                .name(APPLICATION_NAME)
                .apis(List.of(api("1", "ownedapi", "production")))
                .consumer(consumer("production", "approved", "consumedapi"))
                .build());

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ResourceConstraintException.class)
                .satisfies(thrown -> {
                    ResourceConstraintException e = (ResourceConstraintException) thrown;
                    assertThat(e.getReason()).isEqualTo(ExceptionReason.APPLICATION_API_FORGE_OWNERSHIP_CONSTRAINT);
                    assertThat(e.getMessage()).contains("owns API:ownedapi").contains("consumes API:consumedapi");
                });
    }

    @Test
    void blocksDeleteWhenApiForgeIsUnavailable() {
        wireMockServer.stubFor(post("/graphql").willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> service.validateNoApiResourcesExist(APPLICATION_NAME))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    ApiException e = (ApiException) thrown;
                    assertThat(e.getReason())
                            .isEqualTo(ExceptionReason.APPLICATION_DELETE_EXTERNAL_RESOURCE_UNREACHABLE);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void skipsCheckWhenDisabled() {
        config.setEnabled(false);

        service.validateNoApiResourcesExist(APPLICATION_NAME);

        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsCheckWhenClientBeanIsAbsent() {
        ObjectProvider<WebClient> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        new ApiResourceConstraintService(emptyProvider, config).validateNoApiResourcesExist(APPLICATION_NAME);

        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<WebClient> webClientProvider(String url) {
        ObjectProvider<WebClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(WebClient.builder().baseUrl(url).build());
        return provider;
    }

    private ApiForgeApi api(String id, String name, String environment) {
        return api(id, name, environment, "active");
    }

    private ApiForgeApi api(String id, String name, String environment, String state) {
        return ApiForgeApi.builder()
                .id(id)
                .name(name)
                .apiDefinitions(List.of(ApiForgeApiDefinition.builder()
                        .id(id)
                        .majorVersion(1)
                        .state(state)
                        .environments(List.of(ApiForgeApiDefinitionEnvironment.builder()
                                .id(id)
                                .environment(environment)
                                .state(state)
                                .build()))
                        .build()))
                .build();
    }

    private ApiForgeConsumer consumer(String environment, String approvalState, String apiName) {
        return ApiForgeConsumer.builder()
                .id("consumer-1")
                .consumerEnvironments(List.of(ApiForgeConsumerEnvironment.builder()
                        .id("env-1")
                        .environment(environment)
                        .consumerEnvironmentApis(List.of(ApiForgeConsumerEnvironmentApi.builder()
                                .approvalState(approvalState)
                                .api(ApiForgeApi.builder().id("99").name(apiName).build())
                                .build()))
                        .build()))
                .build();
    }

    private void stubResources(RegistryApplicationResources resources) {
        wireMockServer.stubFor(post("/graphql")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(responseBody(resources))));
    }

    private JsonNode responseBody(RegistryApplicationResources resources) {
        try {
            String payload = resources == null ? "null" : objectMapper.writeValueAsString(resources);
            return objectMapper.readTree(String.format("""
                    {
                        "data": {
                            "appRegistryApplication": %s
                        }
                    }
                    """, payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
