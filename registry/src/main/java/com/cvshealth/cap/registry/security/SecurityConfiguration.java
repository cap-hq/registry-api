// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import com.cvshealth.cap.registry.config.CorsConfig;
import com.cvshealth.cap.registry.config.JwtPropertyConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.web.cors.CorsConfiguration;

import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.security.config.Customizer.withDefaults;

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)
@RequiredArgsConstructor
@Configuration
public class SecurityConfiguration {
    private final AuthenticationProvider authenticationProvider;
    private final JwtPropertyConfig jwtProperties;
    private final CorsConfig corsConfig;
    @Value("${registry-auth.api-secret}")
    private String apiSecret;

    @Bean
    public CorsConfiguration corsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOriginPatterns(corsConfig.getAllowedOriginPatterns());
        configuration.setAllowedMethods(corsConfig.getAllowedMethods());
        configuration.setAllowedHeaders(corsConfig.getAllowedHeaders());
        configuration.setMaxAge(corsConfig.getMaxAge());
        return configuration;
    }

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(authenticationProvider);
        return authenticationManagerBuilder.build();
    }

    /**
     * Allow all requests to actuator endpoints
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .anonymous(withDefaults())
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain anonymousSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/swagger/**", "/favicon.ico", "/error", "/api/gui","/api/v1/schema")
                .anonymous(withDefaults())
                .build();
    }

    @Bean
    @Profile("!local & !test")
    @Order
    public SecurityFilterChain jwtSecurity(HttpSecurity http) throws Exception {
        csrfMode(http)
                .cors(cors -> cors.configurationSource(request -> corsConfiguration()))
                .authorizeHttpRequests(req -> req.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2ResourceServer -> {
                    oauth2ResourceServer.authenticationManagerResolver(getAuthenticationManagerResolver());
                });

        http.addFilterBefore(new ApiSecretAuthenticationFilter(apiSecret), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Profile({"local", "test"})
    @Order
    @ConditionalOnMissingBean(name = "jwtSecurityLocalTest")
    public SecurityFilterChain jwtSecurityLocalTest(HttpSecurity http, AuthenticationManager authManager) throws Exception {
        csrfMode(http)
                .cors(cors -> cors.configurationSource(request -> corsConfiguration()))
                .authorizeHttpRequests(req -> req.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2ResourceServer -> {
                    oauth2ResourceServer.authenticationManagerResolver(getAuthenticationManagerResolver());
                });

        http.addFilterBefore(new ApiSecretAuthenticationFilter(apiSecret), BasicAuthenticationFilter.class);
        http.addFilterBefore(new AuthenticationFilter(authManager), BasicAuthenticationFilter.class);

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

    private JwtIssuerAuthenticationManagerResolver getAuthenticationManagerResolver() {
        Map<String, AuthenticationManager> authenticationManagers = new HashMap<>();
        jwtProperties.getSources().forEach(source -> addManager(authenticationManagers, source.getJwkSetUri(), source.getIssuerUri()));
        return new JwtIssuerAuthenticationManagerResolver(authenticationManagers::get);
    }

    private void addManager(Map<String, AuthenticationManager> authenticationManagers, String jwkSetUri, String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        JwtAuthenticationProvider authenticationProvider = new JwtAuthenticationProvider(decoder);
        authenticationProvider.setJwtAuthenticationConverter(jwtConverter());
        authenticationManagers.put(issuerUri, authenticationProvider::authenticate);
    }

    @Bean
    public Converter<Jwt, JwtAuthenticationToken> jwtConverter() {
        return new Converter<Jwt, JwtAuthenticationToken>() {
            @Override
            public JwtAuthenticationToken convert(Jwt source) {
                return new JwtAuthenticationToken(source);
            }
        };
    }

    @Bean
    @ConditionalOnBean(PermissionEvaluator.class)
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

}
