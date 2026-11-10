// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import lombok.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.*;

@AllArgsConstructor
@Setter
public class CustomOAuth2AuthorizationCodeAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;

    static final OAuth2TokenType AUTHORIZATION_CODE_TOKEN_TYPE = new OAuth2TokenType("code");

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2AuthorizationCodeAuthenticationToken authorizationCodeAuthentication = (OAuth2AuthorizationCodeAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = getOAuth2ClientAuthenticationToken(authorizationCodeAuthentication);
        if (clientPrincipal == null) {
            return null;
        }
        RegisteredClient clientPrincipalRegisteredClient = clientPrincipal.getRegisteredClient();
        if (clientPrincipalRegisteredClient == null) {
            return null;
        }

        OAuth2Authorization authorization = authorizationService.findByToken(authorizationCodeAuthentication.getCode(), AUTHORIZATION_CODE_TOKEN_TYPE);
        if (authorization == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (authorizationRequest == null) {
            return null;
        }

        // when the client ID on initial authentication token isn't version specific (missing -vX suffix), sync token with actual registered client version being used
        if (!clientPrincipalRegisteredClient.getClientId().equals(authorizationRequest.getClientId())) {
            OAuth2AuthorizationRequest matchingClientIdAuthorizationRequest = OAuth2AuthorizationRequest.from(authorizationRequest)
                    .clientId(clientPrincipalRegisteredClient.getClientId()).build();

            OAuth2Authorization.Builder newOAuth2Authorization = OAuth2Authorization.withRegisteredClient(clientPrincipalRegisteredClient)
                    .id(authorization.getId())
                    .principalName(authorization.getPrincipalName())
                    .authorizationGrantType(authorization.getAuthorizationGrantType())
                    .authorizedScopes(authorization.getAuthorizedScopes())
                    .attributes((attrs) -> attrs.putAll(authorization.getAttributes()))
                    .attribute(OAuth2AuthorizationRequest.class.getName(), matchingClientIdAuthorizationRequest);
            OAuth2Authorization.Token<OAuth2AuthorizationCode> codeToken = authorization.getToken(OAuth2AuthorizationCode.class);
            if (codeToken != null) {
                newOAuth2Authorization.token(codeToken.getToken());
                authorizationService.save(newOAuth2Authorization.build());
            }
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }

    OAuth2ClientAuthenticationToken getOAuth2ClientAuthenticationToken(OAuth2AuthorizationCodeAuthenticationToken authentication) {
        OAuth2ClientAuthenticationToken clientAuthenticationToken = null;
        if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal().getClass())) {
            clientAuthenticationToken = (OAuth2ClientAuthenticationToken) authentication.getPrincipal();
        }
        if (clientAuthenticationToken != null && clientAuthenticationToken.isAuthenticated()) {
            return clientAuthenticationToken;
        }
        return null;
    }
}
