// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import com.cvshealth.cap.registry.auth.federation.FederatedIdentityAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.web.cors.CorsConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

@EnableWebSecurity
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class DefaultSecurityConfig<S extends Session> {

    @Autowired
    private FindByIndexNameSessionRepository<S> sessionRepository;

    /**
     * External base URL for the authorization server. Used to rewrite redirect URLs
     * when running behind a load balancer that doesn't forward X-Forwarded-* headers.
     */
    @Value("${app.oauth2.external-base-url:}")
    private String externalBaseUrl;

    @Value("${app.oauth2.retrieve-user-info:#{null}}")
    private Boolean retrieveUserInfo;

    @Bean
    @ConfigurationProperties(prefix = "app.cors.allowed-origin-patterns")
    public List<String> allowedOriginPatterns() {
        return new ArrayList<>();
    }

    /**
     * Records the authentication factor on the federated login authentication so that the
     * authorization server can emit the OIDC {@code auth_time} claim.
     * <p>
     * Spring Authorization Server 7.x always adds {@code auth_time} to an id_token issued for an
     * authorization_code grant, and derives it from the newest
     * {@link org.springframework.security.core.authority.FactorGrantedAuthority} on the
     * authenticated principal. With none present it fails the whole token request with
     * {@code IllegalArgumentException: authenticationTime cannot be null}.
     * <p>
     * Spring Security 7's {@code OAuth2LoginAuthenticationProvider} adds that authority itself, but
     * {@code OidcAuthorizationCodeAuthenticationProvider} - the provider used for the federated OIDC
     * login here - does not. This mapper closes that gap with the same authority and the same
     * issued-at semantics (login time) that the non-OIDC provider records, so {@code auth_time}
     * reports when the user actually authenticated.
     */
    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            Collection<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
            mapped.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));
            return mapped;
        };
    }

    @Bean
    public CorsConfiguration corsConfiguration(CorsConfig corsConfig) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOriginPatterns(corsConfig.getAllowedOriginPatterns());
        configuration.setAllowedMethods(corsConfig.getAllowedMethods());
        configuration.setAllowedHeaders(corsConfig.getAllowedHeaders());
        configuration.setMaxAge(corsConfig.getMaxAge());
        return configuration;
    }

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, CorsConfiguration corsConfiguration) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestHandler() {
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
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers("/error", "/assets/**", "/webjars/**", "/login", "/v1/**").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(oauth2Login -> {
                    OidcUserService oidcUserService = new OidcUserService();
                    // Unset app.oauth2.retrieve-user-info keeps the library default: retrieve UserInfo
                    // whenever the provider advertises an endpoint for an authorization_code grant.
                    if (retrieveUserInfo != null) {
                        oidcUserService.setRetrieveUserInfo(request -> retrieveUserInfo);
                    }
                    oauth2Login
                            .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                            .successHandler(authenticationSuccessHandler());
                })
                .cors(cors -> cors.configurationSource(request -> corsConfiguration));
        return http.build();
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
    }

    @Bean
    public SpringSessionBackedSessionRegistry<S> sessionRegistry() {
        return new SpringSessionBackedSessionRegistry<>(this.sessionRepository);
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    @ConditionalOnBean(PermissionEvaluator.class)
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
