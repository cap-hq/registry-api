// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.cvshealth.cap.registry.auth.jose.JwksService;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwksRegistrationServiceTests {

    @Mock
    private JwksService jwksService;

    @Mock
    private JwkSetHolder jwkSetHolder;

    private RSAKey rsaKey;
    private JwksRegistrationService service;

    @BeforeEach
    void setUp() {
        rsaKey = Jwks.generateRsa();
        service = new JwksRegistrationService(jwksService, jwkSetHolder);
    }

    @Test
    void unregisterKey_deletesKeyByKid() {
        service.unregisterKey(rsaKey);

        verify(jwksService).deleteByKid(rsaKey.getKeyID());
    }

    @Test
    void unregisterKey_doesNotThrow_whenDeleteFails() {
        doThrow(new QueryTimeoutException("db timeout")).when(jwksService).deleteByKid(rsaKey.getKeyID());

        assertThatNoException().isThrownBy(() -> service.unregisterKey(rsaKey));
    }

    @Test
    void registerKeys_whenKeyNotPresent_savesKey() {
        when(jwkSetHolder.getAll()).thenReturn(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
        when(jwksService.findByKid(rsaKey.getKeyID())).thenReturn(Optional.empty());

        service.registerKeys();

        ArgumentCaptor<JwkPublicKey> captor = ArgumentCaptor.forClass(JwkPublicKey.class);
        verify(jwksService).save(captor.capture());

        JwkPublicKey saved = captor.getValue();
        assertThat(saved.getKid()).isEqualTo(rsaKey.getKeyID());
        assertThat(saved.getKty()).isEqualTo("RSA");
        assertThat(saved.getN()).isEqualTo(rsaKey.getModulus().toString());
        assertThat(saved.getE()).isEqualTo(rsaKey.getPublicExponent().toString());
        assertThat(saved.getIssuedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNull(); // random key has no expiration
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void registerKeys_whenKeyAlreadyPresent_skipsInsert() {
        when(jwkSetHolder.getAll()).thenReturn(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
        JwkPublicKey existing = JwkPublicKey.builder().kid(rsaKey.getKeyID()).kty("RSA")
                .n("x").e("y").issuedAt(java.time.Instant.now()).build();
        when(jwksService.findByKid(rsaKey.getKeyID())).thenReturn(Optional.of(existing));

        service.registerKeys();

        verify(jwksService, never()).save(any());
    }

    @Test
    void registerKey_whenKeyNotPresent_savesKey() {
        when(jwksService.findByKid(rsaKey.getKeyID())).thenReturn(Optional.empty());

        service.registerKey(rsaKey);

        verify(jwksService).save(any(JwkPublicKey.class));
    }

    @Test
    void registerKey_whenKeyAlreadyPresent_skipsInsert() {
        JwkPublicKey existing = JwkPublicKey.builder().kid(rsaKey.getKeyID()).kty("RSA")
                .n("x").e("y").issuedAt(java.time.Instant.now()).build();
        when(jwksService.findByKid(rsaKey.getKeyID())).thenReturn(Optional.of(existing));

        service.registerKey(rsaKey);

        verify(jwksService, never()).save(any());
    }
}
