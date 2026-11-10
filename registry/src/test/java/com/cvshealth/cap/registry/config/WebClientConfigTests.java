// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.externalApplication.AuthToken;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebClientConfigTests {
    private final WireMockServer server = new WireMockServer(options().dynamicPort());

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"externalAppAuthWebClient", "externalAppApiWebClient", "rollupApiWebClient"})
    void usesApplicationYamlNullHandling(String beanName) throws IOException {
        stubTokenResponse("""
                {"token_type":"Bearer","expires_in":3600,"ext_expires_in":null,"access_token":"test-token"}
                """);

        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            AuthToken token = readToken(context.getBean(beanName, Supplier.class));

            assertThat(token.getTokenType()).isEqualTo("Bearer");
            assertThat(token.getExpiresIn()).isEqualTo(3600);
            assertThat(token.getExtExpiresIn()).isZero();
            assertThat(token.getAccessToken()).isEqualTo("test-token");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"externalAppAuthWebClient", "externalAppApiWebClient", "rollupApiWebClient",})
    void honorsStrictNullHandlingOverride(String beanName) throws IOException {
        stubTokenResponse("""
                {"expires_in":3600,"ext_expires_in":null,"access_token":"test-token"}
                """);

        contextRunner()
                .withPropertyValues("spring.jackson.deserialization.fail-on-null-for-primitives=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> readToken(context.getBean(beanName, Supplier.class)))
                            .hasStackTraceContaining("Cannot map `null` into type `int`");
                });
    }

    @Test
    void preservesHeadersAndRollupBufferLimitWithoutLeakingBetweenClients() throws IOException {
        String largeToken = "x".repeat(300_000);
        stubTokenResponse("{\"access_token\":\"" + largeToken + "\"}");

        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(readToken(context.getBean("rollupApiWebClient", Supplier.class)).getAccessToken())
                    .isEqualTo(largeToken);
            server.verify(getRequestedFor(urlEqualTo("/token"))
                    .withHeader("X-Api-Secret", equalTo("test-api-secret")));

            stubTokenResponse("{\"access_token\":\"test-token\"}");
            readToken(context.getBean("externalAppAuthWebClient", Supplier.class));
            server.verify(getRequestedFor(urlEqualTo("/token"))
                    .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withoutHeader("X-Api-Secret"));
        });
    }

    private ApplicationContextRunner contextRunner() throws IOException {
        var sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(WebClientConfig.class)
                .withInitializer(context -> {
                    for (var source : sources) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                })
                .withPropertyValues(
                        "external-application-api.base-uri=" + server.baseUrl(),
                        "external-application-api.auth-provider.base-uri=" + server.baseUrl(),
                        "registry-rollup.base-uri=" + server.baseUrl(),
                        "registry-rollup.api-secret=test-api-secret",
                        "registry-rollup.codec.max-in-memory-size=1048576");
    }

    private void stubTokenResponse(String json) {
        server.stubFor(get(urlEqualTo("/token"))
                .willReturn(okJson(json)));
    }

    private AuthToken readToken(Supplier<?> supplier) {
        Object supplied = supplier.get();
        assertThat(supplied).isInstanceOf(WebClient.class);
        return ((WebClient) supplied).get().uri("/token")
                .retrieve().bodyToMono(AuthToken.class).block(Duration.ofSeconds(5));
    }
}
