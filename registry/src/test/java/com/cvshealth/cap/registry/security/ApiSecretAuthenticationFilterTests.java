// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ApiSecretAuthenticationFilterTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void constructor_blankSecret() {
        // a blank secret would authenticate any request sending an empty X-Api-Secret header
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new ApiSecretAuthenticationFilter(" "));

        assertThat(ex.getMessage()).contains("registry-auth.api-secret");
    }

    @Test
    public void constructor_nullSecret() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> new ApiSecretAuthenticationFilter(null));

        assertThat(ex.getMessage()).contains("registry-auth.api-secret");
    }

    @Test
    public void doFilter_matchingSecret() throws Exception {
        // given
        ApiSecretAuthenticationFilter filter = new ApiSecretAuthenticationFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Secret", "secret");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("registry");
    }

    @Test
    public void doFilter_emptyHeader() throws Exception {
        // given
        ApiSecretAuthenticationFilter filter = new ApiSecretAuthenticationFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Secret", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
