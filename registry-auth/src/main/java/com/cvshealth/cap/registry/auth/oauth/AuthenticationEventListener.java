// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.AuthenticationErrorMessage;
import com.cvshealth.cap.registry.auth.model.AuthenticationEvent;
import com.cvshealth.cap.registry.auth.model.ClientAuthenticatedEvent;
import com.cvshealth.cap.registry.auth.model.TokenCustomizationFailureEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Slf4j
public class AuthenticationEventListener {

    private final CustomJdbcRegisteredClientRepository registeredClientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtDecoder jwtDecode;
    private final CustomJdbcOauth2AuthorizationService authorizationService;

    @Value("${app.authentication.log-success:false}")
    boolean logAuthenticationSuccess;

    @Value("${app.authentication.event-claims-to-log:scope,iss,sid}")
    List<String> eventClaimsToLog;

    private static final String REFRESH_TOKEN_INVALID_GRANT = "refresh_token 'invalid_grant' error typically indicates that the refresh_token that has already been used, expired or is not owned by the client_id.  Refresh token[%s]";
    private static final String UNKNOWN = "UNKNOWN";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public AuthenticationEventListener(CustomJdbcRegisteredClientRepository registeredClientRepository,
                                       ApplicationEventPublisher eventPublisher,
                                       JwtDecoder jwtDecode,
                                       CustomJdbcOauth2AuthorizationService authorizationService) {
        this.registeredClientRepository = registeredClientRepository;
        this.eventPublisher = eventPublisher;
        this.jwtDecode = jwtDecode;
        this.authorizationService = authorizationService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getSource() instanceof AnonymousAuthenticationToken) {
            return;
        } else if (event.getSource() instanceof OAuth2ClientAuthenticationToken clientAuthToken) {
            // record the auth token creation success on the registered client
            registeredClientRepository.updateLastAccessTokenIssuedAt(getClientId(clientAuthToken));
            String grantType = getGrantType(clientAuthToken.getRegisteredClient(), event);
            // avoid double event write: the access token event is the authoritative write for these grant types
            if (!AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)
                    && !AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)
                    && !AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
                eventPublisher.publishEvent(ClientAuthenticatedEvent.success(
                        this,
                        getClientId(clientAuthToken),
                        clientAuthToken.getRegisteredClient() != null ? clientAuthToken.getRegisteredClient().getClientName() : null,
                        grantType
                ));
            }
        } else if (event.getSource() instanceof OAuth2AccessTokenAuthenticationToken accessTokenAuthenticationToken) {
            RegisteredClient registeredClient = accessTokenAuthenticationToken.getRegisteredClient();
            if (registeredClient == null) {
                log.warn("No registered client for access token authentication event: {}", accessTokenAuthenticationToken.getPrincipal());
                return;
            }
            String grantType = getGrantType(registeredClient);
            Map<String, Object> claims = getClaims(accessTokenAuthenticationToken);
            Map<String, Object> eventAttributes = new HashMap<>(getEventsClaimsToLog(claims));
            if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
                OAuth2Authorization authorization = findAuthorization(accessTokenAuthenticationToken.getAccessToken().getTokenValue());
                if (authorization != null) {
                    Instant firstIssuedAt = getFirstAccessTokenIssuedAt(authorization.getId());
                    Instant currentIssuedAt = accessTokenAuthenticationToken.getAccessToken().getIssuedAt();
                    boolean isRefreshed = firstIssuedAt != null && currentIssuedAt != null
                            && !roundToMicros(currentIssuedAt).equals(roundToMicros(firstIssuedAt));
                    if (isRefreshed) {
                        grantType = AuthorizationGrantType.REFRESH_TOKEN.getValue();
                        eventAttributes.put("first_access_token_issued_at", firstIssuedAt);
                    } else {
                        OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
                        if (authorizationRequest != null) {
                            eventAttributes.put("redirect_uri", authorizationRequest.getRedirectUri());
                        }
                    }
                }
            }
            eventPublisher.publishEvent(ClientAuthenticatedEvent.success(
                    this,
                    registeredClient.getClientId(),
                    registeredClient.getClientName(),
                    grantType,
                    (String) claims.get("sub"),
                    (String) claims.get("oid"),
                    (String) claims.get("agent"),
                    (String) claims.get("externalAlias"),
                    CollectionUtils.isEmpty(eventAttributes) ? null : toJson(eventAttributes)
            ));
        }

        if (logAuthenticationSuccess) {
            logAuthenticationSuccess(event);
        }
    }

    private static Instant roundToMicros(Instant instant) {
        return instant.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    }

    private Map<String, Object> getEventsClaimsToLog(Map<String, Object> claims) {
        if (CollectionUtils.isEmpty(claims)) {
            return Collections.emptyMap();
        }
        Map<String, Object> eventsToLog = new HashMap<>();
        for (String claim : eventClaimsToLog) {
            if (claims.containsKey(claim)) {
                eventsToLog.put(claim, claims.get(claim));
            }
        }
        return eventsToLog;
    }

    private String getErrorToLog(AuthenticationException authenticationException) {
        return getErrorToLog(authenticationException, Collections.emptyMap());
    }

    private String getErrorToLog(AuthenticationException authenticationException, Map<String, Object> additionalInfo) {
        Map<String, Object> errorMap = new HashMap<>(additionalInfo);
        if (authenticationException instanceof OAuth2AuthenticationException oauth2AuthenticationException) {
            OAuth2Error error = oauth2AuthenticationException.getError();
            errorMap.put("error", error.getErrorCode());
            errorMap.put("error_description", error.getDescription());
        } else {
            errorMap.put("error", authenticationException.getClass().getSimpleName());
            errorMap.put("error_description", authenticationException.getMessage());
        }
        return toJson(errorMap);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize claims for logging", e);
            return null;
        }
    }

    private Map<String, Object> getClaims(OAuth2AccessTokenAuthenticationToken accessTokenAuthenticationToken) {
        try {
            Jwt jwt = jwtDecode.decode(accessTokenAuthenticationToken.getAccessToken().getTokenValue());
            Map<String, Object> claims = new HashMap<>(jwt.getClaims());
            Object scope = claims.get("scope");
            if (scope instanceof String scopeString && !scopeString.isBlank()) {
                claims.put("scope", List.of(scopeString.split("[,\\s]+")));
            }
            return claims;
        } catch (JwtException e) {
            log.warn("Failed to decode access token claims", e);
            return Collections.emptyMap();
        }
    }

    private OAuth2Authorization findAuthorization(String accessTokenValue) {
        try {
            return authorizationService.findByToken(accessTokenValue, OAuth2TokenType.ACCESS_TOKEN);
        } catch (Exception e) {
            log.debug("Could not find authorization for access token event", e);
            return null;
        }
    }

    private Instant getFirstAccessTokenIssuedAt(String authorizationId) {
        try {
            return authorizationService.findFirstAccessTokenIssuedAt(authorizationId);
        } catch (Exception e) {
            log.debug("Could not determine first access token issued at for authorization: {}", authorizationId, e);
            return null;
        }
    }

    // registeredClient will not be authorized for both AUTHORIZATION_CODE and CLIENT_CREDENTIALS grant types, so we can just return the first one found
    private String getGrantType(RegisteredClient registeredClient) {
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE.getValue();
        } else if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS.getValue();
        }
        return UNKNOWN;
    }

    private String getGrantType(RegisteredClient registeredClient, AuthenticationSuccessEvent event) {
        if (event.getSource() instanceof PublicClientRefreshTokenAuthenticationToken) { // USER_PKCE
            return AuthorizationGrantType.REFRESH_TOKEN.getValue();
        } else if (event.getSource() instanceof OAuth2ClientAuthenticationToken) {  // original grant type lost; USER client auth success for authorization_code and refresh_token grant types are the same
            return registeredClient != null ? getGrantType(registeredClient) : UNKNOWN;
        }
        return UNKNOWN;
    }

    @EventListener
    public void onTokenCustomizationFailure(TokenCustomizationFailureEvent event) {
        RegisteredClient registeredClient = event.getRegisteredClient();
        String clientId = registeredClient != null ? registeredClient.getClientId() : UNKNOWN;
        String clientName = registeredClient != null ? registeredClient.getClientName() : null;
        String grantType = event.getGrantType() != null ? event.getGrantType().getValue() : UNKNOWN;
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("error", event.getCause().getClass().getSimpleName());
        errorMap.put("error_description", Objects.toString(event.getCause().getMessage(), ""));
        if (event.getScopeType() != null) {
            errorMap.put("scope", event.getScopeType().getScope());
        }
        eventPublisher.publishEvent(ClientAuthenticatedEvent.failure(this, clientId, clientName, grantType, event.getPrincipalName(),
                event.getPrincipalOid(), event.getAgent(), toJson(errorMap)));
    }

    // publishes on failures during the authentication phase — validating credentials, client secrets, etc.
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        AuthenticationEvent.AuthenticationEventBuilder eventBuilder = AuthenticationEvent.builder()
                .result("failure")
                .sourceClass(event.getSource().getClass().getSimpleName())
                .exception(event.getException());
        ClientAuthenticatedEvent clientAuthenticatedEvent = null;

        if (event instanceof AuthorizationCodeRequestAuthenticationFailureEvent failureEvent) {
            OAuth2AuthorizationCodeRequestAuthenticationException exception = (OAuth2AuthorizationCodeRequestAuthenticationException) failureEvent.getException();
            buildErrorMessage(exception.getError(), eventBuilder);

            if (event.getSource() instanceof OAuth2AuthorizationCodeRequestAuthenticationToken codeRequestToken) {
                withAuthCodeRequestToken(eventBuilder, codeRequestToken);
                String grantType = AuthorizationGrantType.AUTHORIZATION_CODE.getValue();
                Map<String, Object> additionalInfo = codeRequestToken.getRedirectUri() == null ? Collections.emptyMap() : Map.of("redirect_uri", codeRequestToken.getRedirectUri());
                clientAuthenticatedEvent = ClientAuthenticatedEvent.failure(this, codeRequestToken.getClientId(), null, grantType,
                        getErrorToLog(failureEvent.getException(), additionalInfo));
            }
        } else if (event instanceof ClientSecretAuthenticationFailureEvent failureEvent) {
            OAuth2AuthenticationException exception = (OAuth2AuthenticationException) failureEvent.getException();
            buildErrorMessage(exception.getError(), eventBuilder);

            if (event.getSource() instanceof OAuth2ClientAuthenticationToken clientAuthToken) {
                withClientAuthToken(eventBuilder, clientAuthToken);
                clientAuthenticatedEvent = buildClientAuthenticatedEvent(failureEvent, clientAuthToken);
            } else if (event.getSource() instanceof OAuth2RefreshTokenAuthenticationToken refreshAuthToken) {
                withRefreshAuthToken(eventBuilder, refreshAuthToken, exception.getError());
                clientAuthenticatedEvent = buildClientAuthenticatedEvent(failureEvent, refreshAuthToken);
            } else if (event.getSource() instanceof OidcUserInfoAuthenticationToken userInfoAuthToken) {
                withUserInfoAuthToken(eventBuilder, userInfoAuthToken);
            }
        }
        AuthenticationEvent authEvent = eventBuilder.build();
        String message = buildLogMessage(authEvent);
        if (authEvent.getErrorMessage() != null) {
            message += String.format(" errorCode[%s] errorMessage[%s]", authEvent.getErrorMessage().getCode(), authEvent.getErrorMessage().getMessage());
        }
        log.atLevel(resolveLogLevel(authEvent)).log(message, event.getException());
        if (clientAuthenticatedEvent != null) {
            eventPublisher.publishEvent(clientAuthenticatedEvent);
        }
    }

    private ClientAuthenticatedEvent buildClientAuthenticatedEvent(ClientSecretAuthenticationFailureEvent failureEvent, OAuth2ClientAuthenticationToken clientAuthToken) {
        String error = getErrorToLog(failureEvent.getException());
        RegisteredClient registeredClient = clientAuthToken.getRegisteredClient();
        if (registeredClient == null) {
            String clientId = (String) clientAuthToken.getPrincipal();
            String grantType = clientAuthToken.getAdditionalParameters().containsKey("grant_type") ?
                    (String) clientAuthToken.getAdditionalParameters().get("grant_type") : AuthorizationGrantType.CLIENT_CREDENTIALS.getValue();
            return ClientAuthenticatedEvent.failure(this, clientId, null, grantType, error);
        }
        String grantType = getGrantType(clientAuthToken.getRegisteredClient());
        return ClientAuthenticatedEvent.failure(this, registeredClient.getClientId(), registeredClient.getClientName(), grantType, error);
    }

    private ClientAuthenticatedEvent buildClientAuthenticatedEvent(ClientSecretAuthenticationFailureEvent failureEvent, OAuth2RefreshTokenAuthenticationToken refreshAuthToken) {
        String error = getErrorToLog(failureEvent.getException());
        String grantType = refreshAuthToken.getGrantType().getValue();
        if (refreshAuthToken.getPrincipal() instanceof OAuth2ClientAuthenticationToken authToken) {
            RegisteredClient registeredClient = authToken.getRegisteredClient();
            if (registeredClient != null) {
                return ClientAuthenticatedEvent.failure(this, registeredClient.getClientId(), registeredClient.getClientName(), grantType, error);
            }
            return ClientAuthenticatedEvent.failure(this, (String) authToken.getPrincipal(), null, grantType, error);
        }
        return ClientAuthenticatedEvent.failure(this, UNKNOWN, null, grantType, error);
    }

    private static void buildErrorMessage(OAuth2Error error, AuthenticationEvent.AuthenticationEventBuilder eventBuilder) {
        eventBuilder.errorMessage(AuthenticationErrorMessage.builder().code(error.getErrorCode()).message(error.getDescription()).build());
    }

    void logAuthenticationSuccess(AuthenticationSuccessEvent event) {
        AuthenticationEvent.AuthenticationEventBuilder eventBuilder = AuthenticationEvent.builder()
                .result("success")
                .sourceClass(event.getSource().getClass().getSimpleName());

        // when project is updated to use Java 21, could refactor to use switch pattern matching
        if (event.getSource() instanceof OAuth2LoginAuthenticationToken loginAuthenticationToken) {
            withLoginAuthenticationToken(eventBuilder, loginAuthenticationToken);
        } else if (event.getSource() instanceof OAuth2AuthorizationCodeRequestAuthenticationToken codeRequestToken) {
            if (codeRequestToken.getPrincipal() instanceof AnonymousAuthenticationToken) {
                return;
            }
            withAuthCodeRequestToken(eventBuilder, codeRequestToken);
        } else if (event.getSource() instanceof OAuth2ClientAuthenticationToken clientAuthToken) {
            withClientAuthToken(eventBuilder, clientAuthToken);
        } else if (event.getSource() instanceof OAuth2AccessTokenAuthenticationToken accessToken) {
            withAccessAuthToken(eventBuilder, accessToken);
        }
        AuthenticationEvent authEvent = eventBuilder.build();
        log.atLevel(resolveLogLevel(authEvent)).log(buildLogMessage(authEvent));
    }

    String buildLogMessage(AuthenticationEvent event) {
        String user = event.getUser() == null ? "NA" : event.getUser();
        String actor = event.getUser() != null ? "user" : event.getClientId() != null ? event.getClientId() : "UNKNOWN";
        String message = String.format("Authentication %s: actor[%s] source[%s] clientId[%s] user[%s]", event.getResult(), actor, event.getSourceClass(), event.getClientId(), user);
        if (event.getJwt() != null) {
            message += String.format(" jwt[%s]", event.getJwt());
        }
        if (event.getRedirectUri() != null) {
            message += String.format(" redirectUri[%s]", event.getRedirectUri());
        }
        return message;
    }

    void withLoginAuthenticationToken(AuthenticationEvent.AuthenticationEventBuilder eventBuilder, OAuth2LoginAuthenticationToken loginAuthToken) {
        eventBuilder.clientId(loginAuthToken.getClientRegistration().getClientId());
        if (loginAuthToken.getPrincipal() instanceof DefaultOidcUser oidcUser) {
            eventBuilder.user(oidcUser.getName());
        }
    }

    void withAuthCodeRequestToken(AuthenticationEvent.AuthenticationEventBuilder eventBuilder, OAuth2AuthorizationCodeRequestAuthenticationToken codeRequestToken) {
        if (codeRequestToken.getPrincipal() instanceof OAuth2AuthenticationToken authenticationToken) {
            eventBuilder.user(authenticationToken.getName());
        }
        eventBuilder.clientId(codeRequestToken.getClientId());
        eventBuilder.redirectUri(codeRequestToken.getRedirectUri());
    }

    void withClientAuthToken(AuthenticationEvent.AuthenticationEventBuilder eventBuilder, OAuth2ClientAuthenticationToken clientAuthToken) {
        if (clientAuthToken.getRegisteredClient() == null) {
            eventBuilder.clientId((String) clientAuthToken.getPrincipal());
        } else {
            eventBuilder.clientId(clientAuthToken.getRegisteredClient().getClientId());
        }
        if (clientAuthToken.getCredentials() instanceof Jwt jwt) {
            eventBuilder.jwt(jwt.getTokenValue());
        }
    }

    void withUserInfoAuthToken(AuthenticationEvent.AuthenticationEventBuilder eventBuilder, OidcUserInfoAuthenticationToken userInfoAuthToken) {
        if (userInfoAuthToken.getPrincipal() instanceof JwtAuthenticationToken jwtAuthToken) {
            Jwt jwt = jwtAuthToken.getToken();
            if (jwt != null) {
                eventBuilder.user(jwt.getClaimAsString("oid"));
                eventBuilder.clientId(jwt.getClaimAsString("clientId"));
                eventBuilder.jwt(jwt.getTokenValue());
            } else {
                eventBuilder.user(jwtAuthToken.getName());
            }
        }
    }

    void withRefreshAuthToken(AuthenticationEvent.AuthenticationEventBuilder eventBuilder, OAuth2RefreshTokenAuthenticationToken refreshAuthToken, OAuth2Error error) {
        withClientAuthToken(eventBuilder, (OAuth2ClientAuthenticationToken) refreshAuthToken.getPrincipal());
        if (OAuth2ErrorCodes.INVALID_GRANT.equals(error.getErrorCode()) && StringUtils.isEmpty(error.getDescription())) {
            eventBuilder.errorMessage(AuthenticationErrorMessage.builder()
                    .code(error.getErrorCode())
                    .message(String.format(REFRESH_TOKEN_INVALID_GRANT, truncateRefreshToken(refreshAuthToken.getRefreshToken())))
                    .build());
        }
    }

    void withAccessAuthToken(AuthenticationEvent.AuthenticationEventBuilder eventBuilder, OAuth2AccessTokenAuthenticationToken accessToken) {
        if (accessToken.getRegisteredClient() != null) {
            eventBuilder.clientId(accessToken.getRegisteredClient().getClientId());
        }
        OAuth2Authorization authorization = findAuthorization(accessToken.getAccessToken().getTokenValue());
        if (authorization != null) {
            OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
            if (authorizationRequest != null) {
                eventBuilder.redirectUri(authorizationRequest.getRedirectUri());
            }
        }
    }

    String getClientId(OAuth2ClientAuthenticationToken clientAuthToken) {
        return clientAuthToken.getRegisteredClient() == null ? (String) clientAuthToken.getPrincipal() : clientAuthToken.getRegisteredClient().getClientId();
    }

    private static final Set<String> WARN_ERROR_CODES = Set.of(
            OAuth2ErrorCodes.INVALID_GRANT,
            OAuth2ErrorCodes.INVALID_TOKEN,
            OAuth2ErrorCodes.ACCESS_DENIED,
            OAuth2ErrorCodes.INVALID_SCOPE,
            OAuth2ErrorCodes.INVALID_REQUEST,
            OAuth2ErrorCodes.UNAUTHORIZED_CLIENT
    );

    private Level resolveLogLevel(AuthenticationEvent event) {
        if (event.getErrorMessage() != null &&
                WARN_ERROR_CODES.contains(event.getErrorMessage().getCode())) {
            return Level.WARN;
        }
        return "failure".equals(event.getResult()) ? Level.ERROR : Level.INFO;
    }

    private String truncateRefreshToken(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            return "UNKNOWN";
        }
        return refreshToken.substring(0, 4) + "..." + refreshToken.substring(refreshToken.length() - 4);
    }
}
