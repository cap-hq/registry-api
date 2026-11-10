// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the block list sits ahead of client authentication in the real filter chain.
 * <p>
 * Both clients used here are valid registered client_credentials clients with correct secrets, and both are
 * issued tokens when the block list is empty -- see {@code whenRequestingClientCredentialsTokenThenSucceed}.
 * A 401 for correct credentials can therefore only come from the filter rejecting the request before the
 * authorization server authenticates it, which is what makes this a test of filter ordering. A unit test
 * driving the filter directly cannot demonstrate ordering, and a nonexistent client id would be rejected
 * with 401 either way, so neither would catch a misplaced filter.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.blocked-client-ids=client-credentials-flow,apiplatform")
// Boot 4 no longer registers TestRestTemplateTestAutoConfiguration from
// AutoConfiguration.imports; the spring-boot-resttestclient jar leaves that file empty and
// registers it only through this annotation, the same way @AutoConfigureMockMvc works.
@AutoConfigureTestRestTemplate
class BlockedClientFilterIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private TestRestTemplate testRestTemplate;

    private static HttpEntity<MultiValueMap<String, String>> clientCredentialsRequest(String clientId, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        return new HttpEntity<>(body, headers);
    }

    private URI tokenEndpoint() throws URISyntaxException {
        return new URI("http://localhost:" + serverPort + "/auth/oauth2/token");
    }

    @ParameterizedTest(name = "{0} is blocked despite valid credentials")
    @CsvSource({"client-credentials-flow,test3", "apiplatform,test6"})
    void everyBlockedClientIdIsRejectedDespiteValidCredentials(String clientId, String secret) throws URISyntaxException {
        ResponseEntity<String> response = testRestTemplate.exchange(
                tokenEndpoint(), HttpMethod.POST, clientCredentialsRequest(clientId, secret), String.class);

        assertThat(response.getStatusCode().value())
                .as("valid credentials for blocked client %s must not yield a token; body=%s", clientId, response.getBody())
                .isEqualTo(401);
        // The filter must commit its own response: an uncommitted error status gets its body replaced with an
        // empty one by the container, leaving a blocked client with no parseable OAuth2 error to act on.
        assertThat(response.getBody())
                .as("blocked client %s must receive a parseable OAuth2 error", clientId)
                .contains("invalid_client");
    }
}
