// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * View of the registered-client store for the OAuth2 authorization server filter chain in which
 * {@link #findByClientId(String)} also accepts a {@code client_name}.
 * <p>
 * With {@code app.registered-client.allow-multiple-secrets-per-client} enabled, rotating a secret creates a
 * new row whose {@code client_id} carries a version suffix ({@code MyClient-v2}) while {@code client_name}
 * keeps the original value ({@code MyClient}). Callers keep presenting the original value, and the custom
 * authentication providers resolve it through {@link CustomJdbcRegisteredClientRepository#findAllByClientIdOrName}.
 * <p>
 * Spring Authorization Server 7 validates {@code client_id} at the authorization endpoint in a servlet
 * filter that runs <em>before</em> any {@code AuthenticationProvider}, using the plain
 * {@code RegisteredClientRepository.findByClientId} contract. Once the unversioned row is retired, that
 * lookup returns {@code null} and every authorization request is rejected with
 * {@code invalid_request: client_id} before the custom providers are consulted. Registering this decorator
 * as the {@code HttpSecurity} shared object hands the authorization server the name-aware lookup, while the
 * {@code RegisteredClientRepository} bean used for client administration keeps exact-match semantics.
 */
@RequiredArgsConstructor
public class ClientNameAwareRegisteredClientRepository implements RegisteredClientRepository {

    private final CustomJdbcRegisteredClientRepository delegate;

    @Override
    public void save(RegisteredClient registeredClient) {
        delegate.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return delegate.findByClientIdOrName(clientId);
    }
}
