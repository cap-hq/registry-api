// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.web.cors.CorsConfiguration;

import java.util.function.Supplier;

/**
 * Overrides the local/test security filter chain for the {@code test} Spring profile.
 *
 * <p>Replaces {@link SecurityConfiguration#jwtSecurityLocalTest} with a chain that uses
 * {@link TestAuthenticationFilter}, enabling integration tests to authenticate as a non-user
 * client via the {@code X-Auth-Client-Id} request header. This configuration must never be
 * loaded outside of the test context.
 */
@TestConfiguration
public class TestSecurityConfiguration {

    /**
     * Overrides the {@code jwtSecurityLocalTest} bean from {@link SecurityConfiguration}.
     * {@link SecurityConfiguration#jwtSecurityLocalTest} is suppressed via
     * {@code @ConditionalOnMissingBean(name = "jwtSecurityLocalTest")}.
     */
    @Bean("jwtSecurityLocalTest")
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain jwtSecurityLocalTest(
            HttpSecurity http,
            AuthenticationManager authManager,
            CorsConfiguration corsConfiguration) throws Exception {

        http.csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestHandler() {
                    @Override
                    public void handle(HttpServletRequest request, HttpServletResponse response,
                                       Supplier<CsrfToken> deferredCsrfToken) {
                        deferredCsrfToken.get();
                    }

                    @Override
                    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
                        return csrfToken.getToken();
                    }
                }))
                .cors(cors -> cors.configurationSource(request -> corsConfiguration))
                .authorizeHttpRequests(req -> req.anyRequest().authenticated());

        http.addFilterBefore(new TestAuthenticationFilter(authManager), BasicAuthenticationFilter.class);

        return http.build();
    }
}
