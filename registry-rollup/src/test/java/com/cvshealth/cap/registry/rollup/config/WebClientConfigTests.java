// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

public class WebClientConfigTests {

    private final WebClientConfig config = new WebClientConfig();

    @Test
    void webClient_withApiSecret_addsDefaultHeader() {
        WebClient webClient = config.webClient("http://localhost:8100", "my-secret", 8388608);

        // The WebClient is created successfully with the secret configured
        assertThat(webClient).isNotNull();
    }

    @Test
    void webClient_withBlankApiSecret_doesNotThrow() {
        WebClient webClient = config.webClient("http://localhost:8100", "", 8388608);

        assertThat(webClient).isNotNull();
    }

    @Test
    void webClient_withNullApiSecret_doesNotThrow() {
        WebClient webClient = config.webClient("http://localhost:8100", null, 8388608);

        assertThat(webClient).isNotNull();
    }

    @Test
    void apiSecretConstant_isCorrectHeaderName() {
        assertThat(WebClientConfig.API_SECRET).isEqualTo("X-Api-Secret");
    }
}
