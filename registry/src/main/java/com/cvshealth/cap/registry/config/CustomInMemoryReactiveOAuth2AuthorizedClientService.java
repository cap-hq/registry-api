// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
public class CustomInMemoryReactiveOAuth2AuthorizedClientService implements ReactiveOAuth2AuthorizedClientService {

    private final ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;
    private final int tokenExpirationBufferInMinutes;

    public CustomInMemoryReactiveOAuth2AuthorizedClientService(ReactiveClientRegistrationRepository registrations,
                                                               int tokenExpirationBufferInMinutes) {
        this.reactiveOAuth2AuthorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(registrations);
        this.tokenExpirationBufferInMinutes = tokenExpirationBufferInMinutes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> Mono<T> loadAuthorizedClient(String clientRegistrationId,
                                                                           String principalName) {
        Mono<OAuth2AuthorizedClient> authorizedClient =
                reactiveOAuth2AuthorizedClientService.loadAuthorizedClient(clientRegistrationId, principalName);
        if (authorizedClient == null) {
            return Mono.empty();
        }
        return (Mono<T>) authorizedClient.flatMap(client -> {
            if (client != null && forceExpireToken(client.getAccessToken())) {
                return removeAuthorizedClient(clientRegistrationId, principalName)
                        .then(Mono.<OAuth2AuthorizedClient>empty());
            }
            return Mono.just(client);
        });
    }

    @Override
    public Mono<Void> saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        log.debug("Saving authorized client {}, expiresAt: {}",
                authorizedClient.getClientRegistration().getRegistrationId(), authorizedClient.getAccessToken().getExpiresAt());
        return reactiveOAuth2AuthorizedClientService.saveAuthorizedClient(authorizedClient, principal);
    }

    @Override
    public Mono<Void> removeAuthorizedClient(String clientRegistrationId, String principalName) {
        log.debug("Removing authorized client {}", clientRegistrationId);
        return reactiveOAuth2AuthorizedClientService.removeAuthorizedClient(clientRegistrationId, principalName);
    }

    boolean forceExpireToken(OAuth2AccessToken accessToken) {
        if  (accessToken == null || accessToken.getExpiresAt() == null) {
            return false;   // for date comparison completeness, not a valid state of the OAuth2AuthorizedClient
        }
        return Instant.now().isAfter(accessToken.getExpiresAt().minus(tokenExpirationBufferInMinutes, ChronoUnit.MINUTES));
    }
}
