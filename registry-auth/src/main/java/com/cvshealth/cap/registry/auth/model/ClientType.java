// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Set;

public enum ClientType {
    // auth code flow
    USER,
    // auth code flow with pkce
    USER_PKCE,
    // client credentials flow
    NON_USER;

    /**
     * Derives the client type from the registered client's grant types and PKCE setting.
     * Shared by {@code ClientService} (for {@code CustomRegisteredClient}) and the token customizer
     * (for Spring's {@code RegisteredClient}), which expose the same two values but do not share a type.
     */
    public static ClientType from(Set<AuthorizationGrantType> authorizationGrantTypes, ClientSettings clientSettings) {
        if (authorizationGrantTypes != null && authorizationGrantTypes.contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            return clientSettings != null && clientSettings.isRequireProofKey() ? USER_PKCE : USER;
        }
        return NON_USER;
    }
}
