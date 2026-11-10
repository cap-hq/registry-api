// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class ApiSecretAuthenticationFilterTests {

    private static final String API_SECRET = "my-secret";
    private ApiSecretAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new ApiSecretAuthenticationFilter(API_SECRET);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noHeader_continuesFilterChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_validSecret_setsAuthenticationAndContinues() throws ServletException, IOException {
        request.addHeader("X-Api-Secret", API_SECRET);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("registry");
    }

    @Test
    void doFilter_invalidSecret_returnsForbidden() throws ServletException, IOException {
        request.addHeader("X-Api-Secret", "wrong-secret");

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_emptyHeader_returnsForbidden() throws ServletException, IOException {
        request.addHeader("X-Api-Secret", "");

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void constructor_blankSecret() {
        // a blank secret would authenticate any request sending an empty X-Api-Secret header
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new ApiSecretAuthenticationFilter(" "));

        assertThat(ex.getMessage()).contains("registry.api-secret");
    }

    @Test
    void constructor_nullSecret() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> new ApiSecretAuthenticationFilter(null));

        assertThat(ex.getMessage()).contains("registry.api-secret");
    }

    @Test
    void apiSecretAuthentication_principalIsRegistry() {
        ApiSecretAuthenticationFilter.ApiSecretAuthentication auth = new ApiSecretAuthenticationFilter.ApiSecretAuthentication();

        assertThat(auth.getPrincipal()).isEqualTo("registry");
        assertThat(auth.getName()).isEqualTo("registry");
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getDetails()).isNull();
        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void apiSecretAuthentication_setAuthenticatedFalse() {
        ApiSecretAuthenticationFilter.ApiSecretAuthentication auth = new ApiSecretAuthenticationFilter.ApiSecretAuthentication();
        auth.setAuthenticated(false);

        assertThat(auth.isAuthenticated()).isFalse();
    }
}
