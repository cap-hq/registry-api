// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseJwkSetEndpointFilterTests {

    @Mock
    private JwksService jwksService;

    private DatabaseJwkSetEndpointFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DatabaseJwkSetEndpointFilter(jwksService);
    }

    @Test
    void doFilterInternal_onJwksPath_writesPublicKeySetJson() throws Exception {
        when(jwksService.getJwksUrlKeys()).thenReturn(List.of(buildJwk("test-kid")));

        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        request.setServletPath(DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String body = response.getContentAsString();
        assertThat(response.getContentType()).contains("application/json");
        assertThat(body).contains("\"keys\"");
        assertThat(body).contains("\"RSA\"");
        assertThat(body).contains("test-kid");
        assertThat(body).contains("\"sig\"");
        assertThat(body).contains("\"RS256\"");
        // filter handled response — chain should NOT have been invoked
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilterInternal_onOtherPath_delegatesToFilterChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/some/other/path");
        request.setServletPath("/some/other/path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwksService);
    }

    @Test
    void doFilterInternal_onPostMethod_delegatesToFilterChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        request.setServletPath(DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwksService);
    }

    @Test
    void doFilterInternal_withNoPublicKeys_returns404AndDoesNotWriteBody() throws Exception {
        when(jwksService.getJwksUrlKeys()).thenReturn(List.of());

        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        request.setServletPath(DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).isEmpty();
        // filter short-circuits — chain should NOT have been invoked
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilterInternal_withKeyMissingOptionalFields_producesValidJson() throws Exception {
        JWK bareKey = new RSAKey.Builder(new Base64URL("sampleModulusBase64url"), new Base64URL("AQAB"))
                .keyID("bare-kid")
                .build();
        when(jwksService.getJwksUrlKeys()).thenReturn(List.of(bareKey));

        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        request.setServletPath(DatabaseJwkSetEndpointFilter.JWKS_ENDPOINT_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String body = response.getContentAsString();
        assertThat(body).contains("\"keys\"");
        assertThat(body).contains("bare-kid");
    }

    private JWK buildJwk(String kid) {
        return new RSAKey.Builder(new Base64URL("sampleModulusBase64url"), new Base64URL("AQAB"))
                .keyID(kid)
                .keyUse(new KeyUse("sig"))
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }
}
