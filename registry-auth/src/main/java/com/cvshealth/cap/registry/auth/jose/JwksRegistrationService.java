// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwksRegistrationService {

    private final JwksService jwksService;
    private final JwkSetHolder jwkSetHolder;

    @PostConstruct
    public void registerKeys() {
        jwkSetHolder.getAll().getKeys().forEach(jwk -> {
            RSAKey rsaKey = (RSAKey) jwk;
            registerKey(rsaKey);
        });
    }

    /** Persists a new key to the JWKS database table (no-op if the kid is already registered). */
    public void registerKey(RSAKey rsaKey) {
        String kid = rsaKey.getKeyID();
        if (jwksService.findByKid(kid).isPresent()) {
            Instant issuedAt = rsaKey.getNotBeforeTime() != null ? rsaKey.getNotBeforeTime().toInstant() : null;
            Instant expiresAt = rsaKey.getExpirationTime() != null ? rsaKey.getExpirationTime().toInstant() : null;
            log.info("JWKS public key already registered: kid={}, issuedAt={}, expiresAt={}", kid, issuedAt, expiresAt);
            return;
        }
        saveJwksKey(kid, rsaKey);
    }

    /** Deletes a key from the JWKS database table by its kid. */
    public void unregisterKey(RSAKey rsaKey) {
        String kid = rsaKey.getKeyID();
        try {
            Instant issuedAt = rsaKey.getNotBeforeTime() != null ? rsaKey.getNotBeforeTime().toInstant() : null;
            Instant expiresAt = rsaKey.getExpirationTime() != null ? rsaKey.getExpirationTime().toInstant() : null;
            log.info("Unregistering JWKS public key: kid={}, issuedAt={}, expiresAt={}", kid, issuedAt, expiresAt);
            jwksService.deleteByKid(kid);
        } catch (DataAccessException e) {   // handle possible edge case with if another instance attempts to delete same kid
            log.warn("JWKS public key not able to be unregistered (potentially deleted by another instance): kid={}", kid, e);
        }
    }

    private void saveJwksKey(String kid, RSAKey rsaKey) {
        JwkPublicKey key = JwkPublicKey.builder()
                .kid(kid)
                .kty(rsaKey.getKeyType().getValue())
                .keyUse(rsaKey.getKeyUse() != null ? rsaKey.getKeyUse().getValue() : null)
                .alg(rsaKey.getAlgorithm() != null ? rsaKey.getAlgorithm().getName() : null)
                .n(rsaKey.getModulus().toString())
                .e(rsaKey.getPublicExponent().toString())
                .issuedAt(rsaKey.getNotBeforeTime() != null ? rsaKey.getNotBeforeTime().toInstant() : Instant.now())
                .expiresAt(rsaKey.getExpirationTime() != null ? rsaKey.getExpirationTime().toInstant() : null)
                .createdAt(Instant.now())
                .build();

        try {
            jwksService.save(key);
            log.info("Registered JWKS public key: kid={}, issuedAt={}, expiresAt={}", kid, key.getIssuedAt(), key.getExpiresAt());
        } catch (DataIntegrityViolationException e) {   // handle possible edge case with unique constraint violation if another instance has already registered the same kid
            log.warn("JWKS public key already exists (unique constraint): kid={}", kid);
        }
    }
}
