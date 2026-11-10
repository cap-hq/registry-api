// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ApiSecretAuthenticationFilterTests {

    private final FilterChain filterChain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsBlankSecret() {
        // a blank secret would authenticate any request that sends an empty X-Api-Secret header
        assertThatThrownBy(() -> new ApiSecretAuthenticationFilter(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registry-auth.api-secret");
    }

    @Test
    void rejectsNullSecret() {
        assertThatThrownBy(() -> new ApiSecretAuthenticationFilter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registry-auth.api-secret");
    }

    @Test
    void authenticatesMatchingSecret() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Secret", "secret");

        new ApiSecretAuthenticationFilter("secret").doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("registry");
    }

    @Test
    void emptyHeaderIsNotAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Secret", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiSecretAuthenticationFilter("secret").doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
