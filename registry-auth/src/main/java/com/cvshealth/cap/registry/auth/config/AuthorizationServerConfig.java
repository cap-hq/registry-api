// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.security.BlockedClientFilter;

import com.cvshealth.cap.registry.auth.metric.ClientScopeStats;
import com.cvshealth.cap.registry.auth.model.CustomClaim;
import com.cvshealth.cap.registry.auth.model.CustomScope;
import com.cvshealth.cap.registry.auth.model.AuthorizeUrlCustomParameter;
import com.cvshealth.cap.registry.auth.oauth.*;
import com.cvshealth.cap.registry.auth.service.AuditService;
import com.cvshealth.cap.registry.auth.service.ClientService;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import com.cvshealth.cap.registry.auth.federation.FederatedIdentityIdTokenCustomizer;
import com.cvshealth.cap.registry.auth.jose.DatabaseJwkSetEndpointFilter;
import com.cvshealth.cap.registry.auth.jose.JwksService;
import com.cvshealth.cap.registry.auth.service.RegistryService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.security.web.util.matcher.RequestMatcher;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class AuthorizationServerConfig {
    private final RefreshTokenConfig refreshTokenConfig;

    public AuthorizationServerConfig(RefreshTokenConfig refreshTokenConfig) {
        this.refreshTokenConfig = refreshTokenConfig;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      CorsConfiguration corsConfiguration,
                                                                      List<AuthenticationConverter> authenticationConverters,
                                                                      List<AuthenticationProvider> authenticationProviders,
                                                                      List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters,
                                                                      DatabaseJwkSetEndpointFilter databaseJwkSetEndpointFilter,
                                                                      CustomJdbcOauth2AuthorizationService authorizationService,
                                                                      BlockedClientFilter blockedClientFilter,
                                                                      RegisteredClientRepository registeredClientRepository,
                                                                      RegisteredClientConfig registeredClientConfig,
                                                                      @Value("${app.login-page:}") String loginPage,
                                                                      @Value("${app.login-processing-url}") String loginProcessingUrl) throws Exception {
        // Spring Authorization Server 7 validates client_id at the authorization endpoint in a filter
        // that runs before any AuthenticationProvider, via RegisteredClientRepository.findByClientId.
        // Give the authorization server the name-aware lookup so a rotated (versioned) client is still
        // resolvable by its original client_id/client_name; the bean itself stays exact-match for
        // client administration. OAuth2ConfigurerUtils reads this shared object before the bean.
        if (registeredClientConfig.isAllowMultipleSecretsPerClient()
                && registeredClientRepository instanceof CustomJdbcRegisteredClientRepository customRepository) {
            http.setSharedObject(RegisteredClientRepository.class,
                    new ClientNameAwareRegisteredClientRepository(customRepository));
        }
        // Spring Security 7 folded Spring Authorization Server in and dropped
        // OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http). The three
        // calls below are exactly what it did: scope this chain to the authorization
        // server endpoints, register the configurer, and require authentication. It did
        // not touch CSRF - the configurer handles that for its own endpoints.
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = (context) -> {
            OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
            JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();
            Map<String, Object> accessTokenClaims = principal.getToken().getClaims();
            return OidcUserInfo.builder()
                    .subject((String) accessTokenClaims.get(StandardClaimNames.SUB))
                    .preferredUsername((String) accessTokenClaims.get(StandardClaimNames.PREFERRED_USERNAME))
                    .email((String) accessTokenClaims.get(StandardClaimNames.EMAIL))
                    .name((String) accessTokenClaims.get(StandardClaimNames.NAME))
                    .givenName((String) accessTokenClaims.get(StandardClaimNames.GIVEN_NAME))
                    .familyName((String) accessTokenClaims.get(StandardClaimNames.FAMILY_NAME))
                    .build();
        };

        // Configured directly on the instance now; there is no http.getConfigurer(...)
        // to reach into once the configurer is registered via http.with(...).
        authorizationServerConfigurer
                .clientAuthentication(clientAuthentication -> {
                    authenticationConverters.forEach(clientAuthentication::authenticationConverter);
                    authenticationProviders.forEach(clientAuthentication::authenticationProvider);
                })
                // Wrap the built-in OAuth2RefreshTokenAuthenticationProvider so the session-expiry
                // check runs as part of the single handler for refresh tokens. Using authenticationProvider()
                // (prepend) would NOT work because ProviderManager catches AuthenticationException from
                // intermediate providers and continues to the next — allowing the built-in provider to
                // succeed even after our check throws. Wrapping makes ours the only handler, so the
                // thrown exception propagates through ProviderManager as lastException.
                // Startup fails if the built-in provider is not found, so enforcement can't silently vanish.
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint.authenticationProviders(providers ->
                                wrapRefreshTokenProvider(providers, authorizationService, refreshTokenConfig)))
                .authorizationEndpoint(authorizationEndpoint -> {
                        if (!CollectionUtils.isEmpty(authorizeUrlCustomParameters)) {
                            authorizationEndpoint.authorizationRequestConverter(new CustomParameterAuthorizationRequestConverter(authorizeUrlCustomParameters));
                        }
                        authorizationEndpoint.authenticationProviders(configureAuthenticationValidator());
                })
                .oidc((oidc) -> {
                    oidc.userInfoEndpoint((userInfo) -> userInfo.userInfoMapper(userInfoMapper));
                    if (!CollectionUtils.isEmpty(authorizeUrlCustomParameters)) {
                        // Add authorization_endpoint_parameters_supported
                        Map<String, Object> customParameters = authorizeUrlCustomParameters.stream().collect(Collectors.toMap(AuthorizeUrlCustomParameter::getName, AuthorizeUrlCustomParameter::getValues));
                        oidc.providerConfigurationEndpoint((providerConfig) -> providerConfig
                                .providerConfigurationCustomizer((builder) -> builder.claim("authorization_endpoint_parameters_supported", customParameters)));
                    }
                });
        http
                .securityMatcher(endpointsMatcher)
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2Login(oauth2LoginCustomizer -> {
                    oauth2LoginCustomizer.loginProcessingUrl(loginProcessingUrl);
                    if (StringUtils.isNotEmpty(loginPage)) {
                        log.info("Registry config override of the OAuth2 login page: {}", loginPage);
                        oauth2LoginCustomizer.loginPage(loginPage);
                    }
                })
                .oauth2ResourceServer(oauth2ResourceServer ->
                        oauth2ResourceServer.jwt(Customizer.withDefaults()))
                .cors(cors -> cors.configurationSource(request -> corsConfiguration))
                .addFilterBefore(databaseJwkSetEndpointFilter, AbstractPreAuthenticatedProcessingFilter.class)
                // OAuth2ClientAuthenticationFilter has no registered order so it cannot be used as a
                // reference here; the authorization server places it immediately after this position, so
                // registering ahead of AbstractPreAuthenticatedProcessingFilter runs the block list first.
                .addFilterBefore(blockedClientFilter, AbstractPreAuthenticatedProcessingFilter.class);
        return http.build();
    }

    /**
     * Block list of client_ids whose token requests are rejected before any registered-client lookup.
     * Override at runtime with the APP_SECURITY_BLOCKED_CLIENT_IDS environment variable (comma separated)
     * so a client can be blocked or released without a code change.
     */
    @Bean
    public BlockedClientFilter blockedClientFilter(ClientBlockListConfig clientBlockListConfig) {
        return new BlockedClientFilter(clientBlockListConfig.getBlockedClientIds());
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(@Qualifier("authJdbcTemplate") JdbcTemplate authJdbcTemplate) {
        return new CustomJdbcRegisteredClientRepository(authJdbcTemplate);
    }

    // BUG: random loss of "sid" claim (needed for OIDC logout)
    // https://github.com/spring-projects/spring-authorization-server/issues/1361
    @Bean
    public CustomJdbcOauth2AuthorizationService authorizationService(@Qualifier("authJdbcTemplate") JdbcTemplate authJdbcTemplate,
                                                                     RegisteredClientRepository registeredClientRepository) {
        return new CustomJdbcOauth2AuthorizationService(authJdbcTemplate, registeredClientRepository, refreshTokenConfig);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> idTokenCustomizer(RegistryService registryService,
                                                                       ClientService clientService,
                                                                       RegisteredClientConfig registeredClientConfig,
                                                                       List<CustomScope> customAuthorizationScopes,
                                                                       @Qualifier("customIdentityClaims") List<CustomClaim> customIdentityClaims,
                                                                       List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters,
                                                                       ClientScopeStats clientScopeStats,
                                                                       AuditService auditService,
                                                                       AccessTokenConfig accessTokenConfig,
                                                                       ApplicationEventPublisher eventPublisher,
                                                                       CustomJdbcOauth2AuthorizationService authorizationService,
                                                                       @Value("${app.job.client-auth-stats.enabled:false}") boolean captureAuthorizationStats) {
        return new FederatedIdentityIdTokenCustomizer(registryService, clientService, registeredClientConfig,
                customAuthorizationScopes, customIdentityClaims, authorizeUrlCustomParameters,
                captureAuthorizationStats ? clientScopeStats : null, auditService, accessTokenConfig.getScopeExpireMinutes(),
                eventPublisher, refreshTokenConfig, accessTokenConfig.getDefaultExpireMinutes(),
                authorizationService);
    }

    @Bean
    public DatabaseJwkSetEndpointFilter databaseJwkSetEndpointFilter(JwksService jwksService) {
        return new DatabaseJwkSetEndpointFilter(jwksService);
    }

    // Prevent Spring Boot from auto-registering DatabaseJwkSetEndpointFilter as a servlet-level
    // filter. It must only run inside the Spring Security filter chain.
    @Bean
    public FilterRegistrationBean<DatabaseJwkSetEndpointFilter> databaseJwkSetEndpointFilterRegistration(
            DatabaseJwkSetEndpointFilter filter) {
        FilterRegistrationBean<DatabaseJwkSetEndpointFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${app.oauth2.issuer:}") String issuerOverride) {
        log.info("Registry Auth Provider Token Check (active:{})", OAuthUtils.getAuthProviderCredentialPrefix());
        AuthorizationServerSettings.Builder settingsBuilder = AuthorizationServerSettings.builder();
        if (StringUtils.isNotEmpty(issuerOverride)) {
            log.info("Registry config override of the OAuth2 issuer: {}", issuerOverride);
            settingsBuilder.issuer(issuerOverride);
        }
        return settingsBuilder.build();
    }

    @Bean
    // generate refresh_token for public clients
    public OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource, OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        NimbusJwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        // When multiple keys are present (e.g., during rotation), always sign with the newest key.
        // RotatingJwkSource returns keys newest-first, so selecting index 0 picks the current key.
        jwtEncoder.setJwkSelector(jwks -> jwks.get(0));
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        OAuth2TokenGenerator<OAuth2RefreshToken> refreshTokenGenerator = new PublicClientRefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, refreshTokenGenerator);
    }

    @Bean
    public List<AuthenticationConverter> authenticationConverters() {
        return List.of(new PublicClientRefreshTokenAuthenticationConverter());
    }

    @Bean
    public List<AuthenticationProvider> authenticationProviders(RegisteredClientRepository registeredClientRepository,
                                                                OAuth2AuthorizationService authorizationService,
                                                                RegisteredClientConfig registeredClientConfig) {
        List<AuthenticationProvider> providers = new java.util.ArrayList<>(4);
        providers.add(new PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository));
        if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
            OAuth2AuthorizationCodeRequestAuthenticationProvider oauth2RequestProvider = new OAuth2AuthorizationCodeRequestAuthenticationProvider(registeredClientRepository, authorizationService, new InMemoryOAuth2AuthorizationConsentService());
            oauth2RequestProvider.setAuthenticationValidator(new CustomRedirectUriValidator().andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR));
            providers.add(new CustomOAuth2AuthorizationCodeRequestAuthenticationProvider(registeredClientRepository, oauth2RequestProvider));
            providers.add(new CustomOAuth2AuthorizationCodeAuthenticationProvider(authorizationService));
            providers.add(new CustomClientSecretAuthenticationProvider(registeredClientRepository, authorizationService));
        }
        return providers;
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        DefaultAuthenticationEventPublisher eventPublisher = new DefaultAuthenticationEventPublisher(applicationEventPublisher);
        eventPublisher.setAdditionalExceptionMappings(Map.of(
                OAuth2AuthorizationCodeRequestAuthenticationException.class, AuthorizationCodeRequestAuthenticationFailureEvent.class,
                OAuth2AuthenticationException.class, ClientSecretAuthenticationFailureEvent.class
        ));
        return eventPublisher;
    }

    private Consumer<List<AuthenticationProvider>> configureAuthenticationValidator() {
        return (authenticationProviders) ->
                authenticationProviders.forEach((authenticationProvider) -> {
                    if (authenticationProvider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider) {
                        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> authenticationValidator =
                                // Override default redirect_uri validator
                                new CustomRedirectUriValidator()
                                        // Reuse default scope validator
                                        .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR);

                        ((OAuth2AuthorizationCodeRequestAuthenticationProvider) authenticationProvider)
                                .setAuthenticationValidator(authenticationValidator);
                    }
                });
    }

    static void wrapRefreshTokenProvider(List<AuthenticationProvider> providers,
                                         CustomJdbcOauth2AuthorizationService authorizationService,
                                         RefreshTokenConfig refreshTokenConfig) {
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i) instanceof OAuth2RefreshTokenAuthenticationProvider) {
                providers.set(i, new SessionExpiryOAuth2RefreshTokenAuthenticationProvider(
                        authorizationService, refreshTokenConfig, providers.get(i)));
                return;
            }
        }
        throw new IllegalStateException("OAuth2RefreshTokenAuthenticationProvider not found among token endpoint "
                + "providers; session-expiry enforcement on refresh cannot be installed");
    }

}
