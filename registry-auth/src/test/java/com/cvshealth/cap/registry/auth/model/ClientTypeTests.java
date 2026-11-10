// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTypeTests {

    @Test
    void from_authorizationCodeWithPkce_isUserPkce() {
        ClientType type = ClientType.from(
                Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN),
                ClientSettings.builder().requireProofKey(true).build());

        assertThat(type).isEqualTo(ClientType.USER_PKCE);
    }

    @Test
    void from_authorizationCodeWithoutPkce_isUser() {
        ClientType type = ClientType.from(
                Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN),
                ClientSettings.builder().requireProofKey(false).build());

        assertThat(type).isEqualTo(ClientType.USER);
    }

    @Test
    void from_clientCredentialsOnly_isNonUser() {
        ClientType type = ClientType.from(
                Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS),
                ClientSettings.builder().build());

        assertThat(type).isEqualTo(ClientType.NON_USER);
    }

    @Test
    void from_nullInputs_isNonUser() {
        assertThat(ClientType.from(null, null)).isEqualTo(ClientType.NON_USER);
    }

    @Test
    void from_authorizationCodeWithNullSettings_isUser() {
        ClientType type = ClientType.from(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), null);

        assertThat(type).isEqualTo(ClientType.USER);
    }
}
