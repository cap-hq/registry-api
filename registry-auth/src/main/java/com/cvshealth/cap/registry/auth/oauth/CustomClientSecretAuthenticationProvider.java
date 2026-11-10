// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.CollectionUtils;

import java.util.List;

public final class CustomClientSecretAuthenticationProvider implements AuthenticationProvider {
    private final RegisteredClientRepository registeredClientRepository;
    private final ClientSecretAuthenticationProvider clientSecretAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;

    public CustomClientSecretAuthenticationProvider(RegisteredClientRepository registeredClientRepository, OAuth2AuthorizationService authorizationService) {
        this.registeredClientRepository = registeredClientRepository;
        this.clientSecretAuthenticationProvider = new ClientSecretAuthenticationProvider(registeredClientRepository, authorizationService);
        this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        clientSecretAuthenticationProvider.setPasswordEncoder(passwordEncoder);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken originClientAuthentication = (OAuth2ClientAuthenticationToken)authentication;
        if (!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(originClientAuthentication.getClientAuthenticationMethod())
                && !ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(originClientAuthentication.getClientAuthenticationMethod())) {
            return null;
        } else if (!(registeredClientRepository instanceof CustomJdbcRegisteredClientRepository)) {
            return null;
        } else if (originClientAuthentication.getCredentials() == null) {
            return null;
        }

        // Retrieve underlying client_id for client_name
        String clientIdOrName = (String)originClientAuthentication.getPrincipal();
        String clientSecret = originClientAuthentication.getCredentials().toString();

        CustomJdbcRegisteredClientRepository jdbcRegisteredClientRepository = (CustomJdbcRegisteredClientRepository)registeredClientRepository;
        List<RegisteredClient> registeredClients = jdbcRegisteredClientRepository.findAllByClientIdOrName(clientIdOrName);
        if (!CollectionUtils.isEmpty(registeredClients)) {
            for (RegisteredClient registeredClient : registeredClients) {
                if (this.passwordEncoder.matches(clientSecret, registeredClient.getClientSecret())) {
                    Authentication oAuth2ClientAuthenticationToken = new OAuth2ClientAuthenticationToken(
                            registeredClient.getClientId(),
                            originClientAuthentication.getClientAuthenticationMethod(),
                            originClientAuthentication.getCredentials(),
                            originClientAuthentication.getAdditionalParameters());
                    return clientSecretAuthenticationProvider.authenticate(oAuth2ClientAuthenticationToken);
                }
            }
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
