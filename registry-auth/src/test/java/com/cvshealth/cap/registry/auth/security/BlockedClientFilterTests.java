// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BlockedClientFilterTests {

    private static final List<String> BLOCK_LIST = List.of("code-scanning-check", "other-bad-client");

    private final FilterChain filterChain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static MockHttpServletRequest tokenRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/oauth2/token");
        request.setContextPath("/auth");
        return request;
    }

    private static void withBasicAuth(MockHttpServletRequest request, String clientId, String secret) {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + credentials);
    }

    @Test
    void blockedClientIsRejectedWithoutReachingTheChain() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        withBasicAuth(request, "code-scanning-check", "any-secret");

        new BlockedClientFilter(BLOCK_LIST).doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_client");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic");
    }

    @Test
    void everyClientIdOnTheListIsBlocked() throws Exception {
        for (String blocked : BLOCK_LIST) {
            MockHttpServletRequest request = tokenRequest();
            withBasicAuth(request, blocked, "any-secret");
            MockHttpServletResponse each = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            new BlockedClientFilter(BLOCK_LIST).doFilter(request, each, chain);

            assertThat(each.getStatus()).as("clientId %s", blocked).isEqualTo(401);
            verify(chain, never()).doFilter(request, each);
        }
    }

    @Test
    void blockedClientIdSuppliedAsRequestParameterIsRejected() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", "code-scanning-check");

        new BlockedClientFilter(BLOCK_LIST).doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void unlistedClientPassesThrough() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        withBasicAuth(request, "a-good-client", "any-secret");

        new BlockedClientFilter(BLOCK_LIST).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void requestsOutsideTheTokenEndpointPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/oauth2/introspect");
        request.setContextPath("/auth");
        withBasicAuth(request, "code-scanning-check", "any-secret");

        new BlockedClientFilter(BLOCK_LIST).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void emptyBlockListPassesEverythingThrough() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        withBasicAuth(request, "code-scanning-check", "any-secret");

        new BlockedClientFilter(List.of()).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void malformedBasicHeaderFallsThroughToClientAuthentication() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        request.addHeader("Authorization", "Basic !!!not-base64!!!");

        new BlockedClientFilter(BLOCK_LIST).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
