// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.security.ApiSecretAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.web.cors.CorsConfiguration;

import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
public class GraphQLSecurityConfig {

    private final CorsConfiguration corsConfiguration;

    @Value("${registry-auth.api-secret}")
    private String apiSecret;

    @Bean
    @Order(1)
    public SecurityFilterChain graphqlSchemaSecurityFilterChain(HttpSecurity http) throws Exception {
        csrfMode(http)
            .securityMatcher("/v1/schema")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain graphqlSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        csrfMode(http)
            .cors(cors -> cors.configurationSource(request -> corsConfiguration))
            .securityMatcher("/graphql", "/graphql/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)));

        http.addFilterBefore(new ApiSecretAuthenticationFilter(apiSecret), BasicAuthenticationFilter.class);
        return http.build();
    }


    // APIs are stateless secured with JWT
    private HttpSecurity csrfMode(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response,
                               Supplier<CsrfToken> deferredCsrfToken) {
                deferredCsrfToken.get();
            }
            @Override
            public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
                return csrfToken.getToken();
            }
        }));
    }
}
