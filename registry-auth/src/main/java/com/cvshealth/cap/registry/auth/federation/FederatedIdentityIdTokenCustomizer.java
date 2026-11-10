// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.federation;

import com.cvshealth.cap.registry.auth.config.RefreshTokenConfig;
import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService.SessionWindow;
import com.cvshealth.cap.registry.auth.metric.ClientScopeStats;
import com.cvshealth.cap.registry.auth.model.AuthorizeUrlCustomParameter;
import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.model.CustomClaim;
import com.cvshealth.cap.registry.auth.model.CustomScope;
import com.cvshealth.cap.registry.auth.model.CustomScopeType;
import com.cvshealth.cap.registry.auth.model.TokenCustomizationFailureEvent;
import com.cvshealth.cap.registry.auth.service.RegistryService;
import com.cvshealth.cap.registry.auth.service.AuditService;
import com.cvshealth.cap.registry.auth.service.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link OAuth2TokenCustomizer} to map claims from a federated identity to
 * the {@code id_token} produced by this authorization server.
 */
@Slf4j
public final class FederatedIdentityIdTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final RegistryService registryService;
    private final ClientService clientService;
    private final RegisteredClientConfig registeredClientConfig;
    private final List<CustomScope> customAuthorizationScopes;
    private final List<CustomClaim> customIdentityClaims;
    private final List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters;
    private final ClientScopeStats clientScopeStats;
    private final AuditService auditService;
    private final Map<CustomScopeType, Long> scopeExpireMinutes;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenConfig refreshTokenConfig;
    private final long defaultAccessTokenExpireMinutes;
    private final CustomJdbcOauth2AuthorizationService authorizationService;

    public FederatedIdentityIdTokenCustomizer(RegistryService registryService,
                                              ClientService clientService, RegisteredClientConfig registeredClientConfig,
                                              List<CustomScope> customAuthorizationScopes, List<CustomClaim> customIdentityClaims,
                                              List<AuthorizeUrlCustomParameter> authorizeUrlCustomParameters, ClientScopeStats clientScopeStats,
                                              AuditService auditService, Map<CustomScopeType, Long> scopeExpireMinutes,
                                              ApplicationEventPublisher eventPublisher, RefreshTokenConfig refreshTokenConfig,
                                              long defaultAccessTokenExpireMinutes, CustomJdbcOauth2AuthorizationService authorizationService) {
        this.registryService = registryService;
        this.clientService = clientService;
        this.registeredClientConfig = registeredClientConfig;
        this.customAuthorizationScopes = customAuthorizationScopes;
        this.customIdentityClaims = customIdentityClaims == null ? null : customIdentityClaims.stream().filter(CustomClaim::isSourceAuthParameter).toList();
        this.authorizeUrlCustomParameters = authorizeUrlCustomParameters;
        this.clientScopeStats = clientScopeStats;
        this.auditService = auditService;
        this.scopeExpireMinutes = scopeExpireMinutes != null ? scopeExpireMinutes : Collections.emptyMap();
        this.eventPublisher = eventPublisher;
        this.refreshTokenConfig = refreshTokenConfig;
        this.defaultAccessTokenExpireMinutes = defaultAccessTokenExpireMinutes;
        this.authorizationService = authorizationService;
    }

    private static final Set<String> ID_TOKEN_CLAIMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            IdTokenClaimNames.ISS,
            IdTokenClaimNames.SUB,
            IdTokenClaimNames.AUD,
            IdTokenClaimNames.EXP,
            IdTokenClaimNames.IAT,
            IdTokenClaimNames.AUTH_TIME,
            IdTokenClaimNames.NONCE,
            IdTokenClaimNames.ACR,
            IdTokenClaimNames.AMR,
            IdTokenClaimNames.AZP,
            IdTokenClaimNames.AT_HASH,
            IdTokenClaimNames.C_HASH
    )));

    private static final Set<String> REMOVE_CLAIMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // nbf claim from Azure needs to be of type Instant for Spring OIDC logout functionality
            // it isn't required, so remove it
            TokenClaim.NBF
    )));

    @Override
    public void customize(JwtEncodingContext context) {
        Map<String, Object> thirdPartyClaims = extractClaims(context.getPrincipal());
        context.getClaims().claims(existingClaims -> {
            // Remove conflicting claims set by this authorization server
            existingClaims.keySet().forEach(thirdPartyClaims::remove);

            // Remove standard id_token claims that could cause problems with clients
            ID_TOKEN_CLAIMS.forEach(thirdPartyClaims::remove);

            // Add all other claims directly to id_token
            existingClaims.putAll(thirdPartyClaims);

            // remove unwanted claims
            REMOVE_CLAIMS.forEach(existingClaims::remove);

            CustomScope customAuthorizationScope = getCustomAuthorizationScope(context.getAuthorizedScopes());
            CustomScopeType scopeType = customAuthorizationScope == null ? null : customAuthorizationScope.getScopeType();
            RegisteredClient client = context.getRegisteredClient();
            existingClaims.put(IdTokenClaimNames.AUD, getAudiences(client));
            if (context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
                logLegacyScopeUse(client.getClientId(), scopeType, context.getAuthorizedScopes());
                existingClaims.putAll(populateAccessTokenClaims(context, scopeType));
                applyScopeExpiry(existingClaims, scopeType);
                if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())) {
                    // Prefer the DB column (canonical source); fall back to iat-based computation
                    // when the DB row hasn't been persisted yet (initial issuance, save() runs after this customizer).
                    Instant sessionExp = null;
                    OAuth2Authorization authCodeAuthorization = context.get(OAuth2Authorization.class);
                    if (authCodeAuthorization != null && authorizationService != null) {
                        sessionExp = authorizationService.findSessionExpiresAt(authCodeAuthorization.getId());
                    }
                    if (sessionExp == null) {
                        Instant iat = (Instant) existingClaims.get("iat");
                        if (iat != null) {
                            sessionExp = iat.plusSeconds(resolveSessionExpireMinutes(authCodeAuthorization) * 60L);
                        }
                    }
                    if (sessionExp != null) {
                        existingClaims.put("session_expires_at", (int) sessionExp.getEpochSecond());
                        capExpAtSessionBoundary(existingClaims, sessionExp);
                    }
                } else if (AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())
                        && authorizationService != null) {
                    OAuth2Authorization authorization = context.get(OAuth2Authorization.class);
                    if (authorization != null) {
                        SessionWindow sessionWindow = authorizationService.findSessionWindowById(authorization.getId())
                                // Row deleted mid-request: fail closed so save() can't re-insert it with a fresh session window.
                                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT)));
                        Instant sessionExp = sessionWindow.getSessionExpiresAt();
                        if (sessionExp == null
                                && AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorization.getAuthorizationGrantType())) {
                            // Pre-migration fallback: session_expires_at column not yet set for this row.
                            // Mirror the boundary used by SessionExpiryOAuth2RefreshTokenAuthenticationProvider
                            // so scope-extended exp claims are still capped consistently.
                            // session_expires_at is only ever set for authorization_code rows, so other grants are not session-limited.
                            Instant firstIssuedAt = sessionWindow.getFirstAccessTokenIssuedAt();
                            if (firstIssuedAt != null) {
                                sessionExp = firstIssuedAt.plusSeconds(resolveSessionExpireMinutes(authorization) * 60L);
                            }
                        }
                        if (sessionExp != null) {
                            existingClaims.put("session_expires_at", (int) sessionExp.getEpochSecond());
                            capExpAtSessionBoundary(existingClaims, sessionExp);
                        }
                    }
                }
                CompletableFuture.runAsync(() -> recordClientScopeStats(client, scopeType));
            } else if (context.getTokenType().getValue().equals("id_token")) {
                existingClaims.putAll(populateIdTokenClaims(context, scopeType));
                applyScopeExpiry(existingClaims, scopeType);
            }
        });
    }

    // Tracks clients still requesting a legacy scope name, so it can be removed from CustomScopeType once unused
    void logLegacyScopeUse(String clientId, CustomScopeType scopeType, Set<String> authorizedScopes) {
        if (scopeType == null || CollectionUtils.isEmpty(authorizedScopes) || authorizedScopes.contains(scopeType.getScope())) {
            return;
        }
        scopeType.getScopes().stream().filter(authorizedScopes::contains).findFirst().ifPresent(legacyScope ->
                log.info("client {} requested legacy scope {}, use {} instead", clientId, legacyScope, scopeType.getScope()));
    }

    private void recordClientScopeStats(RegisteredClient client, CustomScopeType scopeType) {
        if (clientScopeStats != null) {
            clientScopeStats.recordClientScopeStats(client.getClientName(), client.getClientId(), scopeType);
        }
    }

    private long resolveSessionExpireMinutes(OAuth2Authorization authorization) {
        if (authorization == null || authorizationService == null) {
            return refreshTokenConfig.getDefaultSessionExpireMinutes();
        }
        return authorizationService.resolveSessionExpireMinutes(authorization);
    }

    /**
     * Caps the {@code exp} claim to {@code sessionExp} plus the default access token TTL.
     * <p>
     * This allows a token refreshed just before session expiry to remain valid for a reasonable
     * window (one default access token lifetime) rather than inheriting a potentially much longer
     * scope-specific TTL. Without this cap, a scope override via {@link #applyScopeExpiry} could
     * produce a token that outlives the session by a significant margin.
     * <p>
     * The grace period past {@code sessionExp} is intentional: the session window bounds when a new
     * token can be obtained, not the lifetime of the last token issued within it.
     */
    private void capExpAtSessionBoundary(Map<String, Object> existingClaims, Instant sessionExp) {
        Instant expCap = sessionExp.plusSeconds(defaultAccessTokenExpireMinutes * 60L);
        Instant currentExp = (Instant) existingClaims.get("exp");
        if (currentExp != null && currentExp.isAfter(expCap)) {
            existingClaims.put("exp", expCap);
        }
    }

    void applyScopeExpiry(Map<String, Object> existingClaims, CustomScopeType scopeType) {
        if (scopeType == null || scopeExpireMinutes.isEmpty()) {
            return;
        }
        Long expireMinutes = scopeExpireMinutes.get(scopeType);
        if (expireMinutes != null && expireMinutes > 0) {
            log.debug("overriding default access_token TTL with {} minutes for scope {}", expireMinutes, scopeType.getScope());
            Instant issuedAt = (Instant) existingClaims.get("iat");
            existingClaims.put("exp", issuedAt.plusSeconds(expireMinutes * 60));
        }
    }

    Map<String, Object> populateIdTokenClaims(JwtEncodingContext context, CustomScopeType scopeType) {
        Map<String, Object> claims = new HashMap<>();
        String azpClaim = getAzpClaim(context);
        claims.put(IdTokenClaimNames.AZP, azpClaim != null ? azpClaim : context.getRegisteredClient().getClientId());
        claims.putAll(getAuthParameterIdentityClaims(context));
        if (context.getPrincipal() != null && context.getPrincipal().getPrincipal() instanceof DefaultOidcUser user) {
            claims.putAll(getRegistryIdentityClaims(user, context, scopeType));
        }
        return claims;
    }

    Map<String, Object> getAuthParameterIdentityClaims(JwtEncodingContext context) {
        if (CollectionUtils.isEmpty(customIdentityClaims)) {
            return Collections.emptyMap();
        }
        Map<String, Object> claims = new HashMap<>();
        for (CustomClaim customIdentityClaim : customIdentityClaims) {
            if (CustomClaim.TYPE_CUSTOM.equals(customIdentityClaim.getType()) && CustomClaim.SOURCE_AUTH_PARAMETER.equals(customIdentityClaim.getSource())) {
                String customValue = getAdditionalParameter(context, customIdentityClaim.getAttribute());
                if (isAuthorizeUrlParameterValid(customIdentityClaim.getAttribute(), customValue)) {
                    claims.put(customIdentityClaim.getName(), customValue);
                    if (context.getPrincipal() != null && context.getPrincipal().getPrincipal() instanceof DefaultOidcUser user) {
                        auditService.publish("AUTHORIZE_URL_PARAMETER", "ADDED", Map.of(
                                customIdentityClaim.getName(), customValue, "clientId", context.getRegisteredClient().getClientId(),
                                "user", Objects.toString(user.getIdToken().getClaim("oid"), "unknown")
                        ));
                    }
                }
            }
        }
        return claims;
    }

    private List<String> getValues(String parameterName) {
        return authorizeUrlCustomParameters.stream()
                .filter(p -> parameterName.equals(p.getName()))
                .flatMap(p -> p.getValues() != null ? p.getValues().stream() : java.util.stream.Stream.empty())
                .distinct()
                .toList();
    }

    boolean isAuthorizeUrlParameterValid(String parameterName, String value) {
        if (StringUtils.isBlank(parameterName) || StringUtils.isBlank(value)) {
            return false;
        }
        return getValues(parameterName).contains(value);
    }

    Map<String, Object> populateAccessTokenClaims(JwtEncodingContext context, CustomScopeType scopeType) {
        if (context.getPrincipal() == null || context.getPrincipal().getPrincipal() == null || "UNKNOWN".equals(context.getPrincipal().getPrincipal())) {
            log.warn("Unable to get user principal for Registry claims");
            return Collections.emptyMap();
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("clientId", context.getRegisteredClient().getClientId());
        claims.putAll(getAuthParameterIdentityClaims(context));
        claims.put("clientType", ClientType.from(context.getRegisteredClient().getAuthorizationGrantTypes(),
                context.getRegisteredClient().getClientSettings()).name());
        if (scopeType == null) {
            return claims;
        }

        Object principal = context.getPrincipal().getPrincipal();
        boolean hasUser = principal instanceof DefaultOidcUser;
        boolean isClientCredentialsFlow = principal instanceof String clientId &&
                context.getRegisteredClient().getClientId().equals(clientId);

        switch (scopeType) {
            case REGISTRY -> {
                // REGISTRY: Single-principal model
                // Include claims for the authenticated principal only (user XOR client, never both)
                if (hasUser) {
                    claims.putAll(getRegistryClaims((DefaultOidcUser) principal, context, scopeType));
                } else if (isClientCredentialsFlow) {
                    claims.putAll(getRegistryClaims((String) principal, context, scopeType));
                }
            }
            case OPEN_POLICY_AGENT -> {
                // OPEN_POLICY_AGENT: Dual-context model for policy evaluation
                // Always include client claims, then overlay user claims if present
                // User claims take precedence when keys conflict
                claims.putAll(getClientClaims(context, scopeType));
                if (hasUser) {
                    claims.putAll(getUserClaims((DefaultOidcUser) principal, context, scopeType));
                }
            }
        }
        return claims;
    }

    // Extract helper methods for clarity:
    private Map<String, Object> getClientClaims(JwtEncodingContext context, CustomScopeType scopeType) {
        return getRegistryClaims(context.getRegisteredClient().getClientId(), context, scopeType);
    }

    private Map<String, Object> getUserClaims(DefaultOidcUser user, JwtEncodingContext context, CustomScopeType scopeType) {
        return getRegistryClaims(user, context, scopeType);
    }

    public CustomScope getCustomAuthorizationScope(Set<String> authorizedScopes) {
        if (CollectionUtils.isEmpty(customAuthorizationScopes)) {
            return null;
        }
        // Find matching custom scope from authorized scopes
        if (!CollectionUtils.isEmpty(authorizedScopes)) {
            for (CustomScope customScope : customAuthorizationScopes) {
                if (customScope.getScopeType() != null &&
                        customScope.getScopeType().getScopes().stream().anyMatch(authorizedScopes::contains)) {
                    return customScope;
                }
            }
        }
        // Fall back to default scope if configured
        return customAuthorizationScopes.stream()
                .filter(CustomScope::isDefaultScope)
                .findFirst()
                .orElse(null);
    }

    String getAzpClaim(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.get(OAuth2Authorization.class);
        if (authorization == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (authorizationRequest == null) {
            return null;
        }
        if (CollectionUtils.isEmpty(authorizationRequest.getAdditionalParameters())) {
            return null;
        }
        return (String) authorizationRequest.getAdditionalParameters().getOrDefault("origin_client_id", null);
    }

    String getAdditionalParameter(JwtEncodingContext context, String tokenClaim) {
        OAuth2Authorization authorization = context.get(OAuth2Authorization.class);
        if (authorization == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (authorizationRequest == null) {
            return null;
        }
        Map<String, Object> additionalParameters = authorizationRequest.getAdditionalParameters();
        if (CollectionUtils.isEmpty(additionalParameters)) {
            return null;
        }
        return (String) additionalParameters.getOrDefault(tokenClaim, null);
    }

    static List<String> getAudiences(RegisteredClient registeredClient) {
        List<String> audiences = new ArrayList<>(List.of(registeredClient.getClientId()));
        if (registeredClient.getClientId().startsWith(registeredClient.getClientName()) &&
                !registeredClient.getClientId().equals(registeredClient.getClientName())) {
            audiences.add(registeredClient.getClientName());
        }
        return audiences;
    }

    Map<String, Object> getRegistryClaims(DefaultOidcUser user, JwtEncodingContext context, CustomScopeType scopeType) {
        String externalId = user.getAttribute(TokenClaim.OID);
        if (externalId == null) {
            return new HashMap<>();
        }
        try {
            return registryService.getClaims(externalId, scopeType);
        } catch (Exception e) {
            log.error("Failed to fetch Registry claims for user externalId={}, scope={}", externalId, scopeType, e);
            publishTokenCustomizationFailureEvent(context, scopeType, e);
            throw e;
        }
    }

    Map<String, Object> getRegistryClaims(String clientId, JwtEncodingContext context, CustomScopeType scopeType) {
        Client client = getClient(clientId);
        if (client == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> claims = new HashMap<>();
            if (registeredClientConfig.isAllowMultipleSecretsPerClient()) {
                claims.put("clientName", client.getClientName());
            }
            if (client.getClientSecretExpiresAt() != null) {
                claims.put("clientSecretExpiresAt", client.getClientSecretExpiresAt().toString());
            }
            Map<String, Object> claimsFromRegistry = registryService.getClaimsForClientId(
                    clientService.getBaseClientId(client), clientService.getClientVersion(client), scopeType);
            if (claimsFromRegistry != null) {
                claims.putAll(claimsFromRegistry);
            }
            return claims;
        } catch (Exception e) {
            log.error("Failed to fetch Registry claims for clientId={}, scope={}", clientId, scopeType, e);
            publishTokenCustomizationFailureEvent(context, scopeType, e);
            throw e;
        }
    }

    private void publishTokenCustomizationFailureEvent(JwtEncodingContext context, CustomScopeType scopeType, Exception e) {
        try {
            String agent = getAdditionalParameter(context, "agent");
            AtomicReference<String> principalOid = new AtomicReference<>();
            AtomicReference<String> principalName = new AtomicReference<>();
            context.getClaims().claims(existingClaims -> {
                principalOid.set((String) existingClaims.get(TokenClaim.OID));
                principalName.set((String) existingClaims.get("sub"));
            });
            eventPublisher.publishEvent(new TokenCustomizationFailureEvent(this, context.getRegisteredClient(), context.getAuthorizationGrantType(),
                    principalName.get(), principalOid.get(), agent, scopeType, e));
        } catch (Exception ex) {
            String clientId = context.getRegisteredClient() == null ? "UNKNOWN" : context.getRegisteredClient().getClientId();
            log.error("Failed to publish TokenCustomizationFailureEvent for clientId={}, scope={}", clientId, scopeType, ex);
        }
    }

    private Client getClient(String clientId) {
        if (clientId == null) {
            return null;
        }
        try {
            return clientService.getClient(clientId);
        } catch (Exception e) {
            log.error("Failed to resolve client for clientId={}", clientId, e);
            throw e;
        }
    }

    Map<String, Object> getRegistryIdentityClaims(DefaultOidcUser user, JwtEncodingContext context, CustomScopeType scopeType) {
        String externalId = user.getAttribute(TokenClaim.OID);
        if (externalId == null) {
            return new HashMap<>();
        }
        try {
            return registryService.getIdentityClaims(externalId, scopeType);
        } catch (Exception e) {
            log.error("Failed to fetch Registry identity claims for user externalId={}, scope={}", externalId, scopeType, e);
            publishTokenCustomizationFailureEvent(context, scopeType, e);
            throw e;
        }
    }

    private Map<String, Object> extractClaims(Authentication principal) {
        Map<String, Object> claims;
        if (principal.getPrincipal() instanceof OidcUser oidcUser) {
            OidcIdToken idToken = oidcUser.getIdToken();
            claims = idToken.getClaims();
        } else if (principal.getPrincipal() instanceof OAuth2User oauth2User) {
            claims = oauth2User.getAttributes();
        } else {
            claims = Collections.emptyMap();
        }
        return new HashMap<>(claims);
    }
}
