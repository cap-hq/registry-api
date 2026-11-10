// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class CustomOAuth2AuthorizationCodeRequestAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider;

    public static final String REGISTERED_CLIENT_IDS = "registered_clientIds";
    public static final String ORIGIN_CLIENT_ID = "origin_client_id";

    public CustomOAuth2AuthorizationCodeRequestAuthenticationProvider(RegisteredClientRepository registeredClientRepository,
                                                                      OAuth2AuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider) {
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationCodeRequestAuthenticationProvider = authorizationCodeRequestAuthenticationProvider;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2AuthorizationCodeRequestAuthenticationToken authorizationCodeRequestAuthentication = (OAuth2AuthorizationCodeRequestAuthenticationToken)authentication;
        List<RegisteredClient> registeredClients = findRegisteredClients(authorizationCodeRequestAuthentication);
        if (CollectionUtils.isEmpty(registeredClients)) {
            return null;
        }
        OAuth2AuthorizationCodeRequestAuthenticationToken resolvedClientIdAuthenticationToken = buildAuthenticationToken(authorizationCodeRequestAuthentication, registeredClients);
        return authorizationCodeRequestAuthenticationProvider.authenticate(resolvedClientIdAuthenticationToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationCodeRequestAuthenticationToken.class.isAssignableFrom(authentication);
    }

    List<RegisteredClient> findRegisteredClients(OAuth2AuthorizationCodeRequestAuthenticationToken authentication) {
        CustomJdbcRegisteredClientRepository jdbcRegisteredClientRepository = (CustomJdbcRegisteredClientRepository)registeredClientRepository;
        return jdbcRegisteredClientRepository.findAllByClientIdOrName(authentication.getClientId());
    }

    static OAuth2AuthorizationCodeRequestAuthenticationToken buildAuthenticationToken(OAuth2AuthorizationCodeRequestAuthenticationToken authentication, List<RegisteredClient> registeredClients) {
        OAuth2AuthorizationCodeRequestAuthenticationToken token = null;
        // Look for exact match of the client id with a registered client, but request is good to continue with existence of any valid client
        RegisteredClient registeredClient = registeredClients.stream().filter(c -> c.getClientId().equals(authentication.getClientId())).findFirst().orElse(registeredClients.get(0));
        if (authentication.getAuthorizationCode() == null) {
            Map<String, Object> additionalParameters = new HashMap<>(Map.of(
                    ORIGIN_CLIENT_ID, authentication.getClientId(),
                    REGISTERED_CLIENT_IDS, registeredClientIds(registeredClients)   // add to authentication token for client id version matching
            ));
            if (authentication.getAdditionalParameters() != null) {
                additionalParameters.putAll(authentication.getAdditionalParameters());
            }
            token = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                    authentication.getAuthorizationUri(),
                    registeredClient.getClientId(),
                    (Authentication) authentication.getPrincipal(),
                    authentication.getRedirectUri(),
                    authentication.getState(),
                    authentication.getScopes(),
                    additionalParameters);
        } else {
            token = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                    authentication.getAuthorizationUri(),
                    registeredClient.getClientId(),
                    (Authentication) authentication.getPrincipal(),
                    authentication.getAuthorizationCode(),
                    authentication.getRedirectUri(),
                    authentication.getState(),
                    authentication.getScopes());
        }
        token.setDetails(authentication.getDetails());
        return token;
    }

    // Extract all the client ids.  NOTE: Return list must be mutable
    static List<String> registeredClientIds(List<RegisteredClient> registeredClients) {
        return registeredClients.stream().map(RegisteredClient::getClientId).collect(Collectors.toCollection(ArrayList::new));
    }
}
