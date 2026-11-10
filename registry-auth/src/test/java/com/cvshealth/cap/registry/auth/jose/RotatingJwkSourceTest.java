// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RotatingJwkSourceTest {

    @Mock
    private JwkSetHolder jwkSetHolder;

    private RotatingJwkSource rotatingJwkSource;

    @BeforeEach
    void setUp() {
        rotatingJwkSource = new RotatingJwkSource(jwkSetHolder);
    }

    @Test
    void get_encoderPath_returnsNewestKeyFirst() {
        RSAKey oldKey = Jwks.generateRsa();
        RSAKey newKey = Jwks.generateRsa();
        // newest-first ordering as maintained by JwkSetHolder
        when(jwkSetHolder.getAll()).thenReturn(new JWKSet(List.of(newKey, oldKey)));

        JWKSelector selectAll = new JWKSelector(new JWKMatcher.Builder().build());
        var results = rotatingJwkSource.get(selectAll, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getKeyID()).isEqualTo(newKey.getKeyID());
    }

    @Test
    void get_decoderPath_kidSelectorFindsOldKey() {
        RSAKey oldKey = Jwks.generateRsa();
        RSAKey newKey = Jwks.generateRsa();
        when(jwkSetHolder.getAll()).thenReturn(new JWKSet(List.of(newKey, oldKey)));

        // Decoder selects by kid, as NimbusJwtDecoder does
        JWKSelector kidSelector = new JWKSelector(
                new JWKMatcher.Builder().keyID(oldKey.getKeyID()).build());
        var results = rotatingJwkSource.get(kidSelector, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getKeyID()).isEqualTo(oldKey.getKeyID());
    }

    @Test
    void get_decoderPath_kidSelectorFindsNewKey() {
        RSAKey oldKey = Jwks.generateRsa();
        RSAKey newKey = Jwks.generateRsa();
        when(jwkSetHolder.getAll()).thenReturn(new JWKSet(List.of(newKey, oldKey)));

        JWKSelector kidSelector = new JWKSelector(
                new JWKMatcher.Builder().keyID(newKey.getKeyID()).build());
        var results = rotatingJwkSource.get(kidSelector, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getKeyID()).isEqualTo(newKey.getKeyID());
    }
}
