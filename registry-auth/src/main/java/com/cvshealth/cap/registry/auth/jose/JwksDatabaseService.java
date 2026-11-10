// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.config.CacheConfig;
import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.cvshealth.cap.registry.auth.oauth.JwksRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class JwksDatabaseService implements JwksService {

    private final JwksRepository jwksRepository;

    @Value("${app.ssl.rotation.key-grace-period:PT12H}")
    private Duration keyGracePeriod = Duration.ofHours(12);

    @Override
    public List<JWK> getPublicKeys() {
        log.debug("Loading public keys from database");
        return jwksRepository.findAll().stream()
                .map(JwksDatabaseService::toPublicJwk)
                .toList();
    }

    /**
     * Returns the reads all rows from oauth2_jwks, filters out keys past expiresAt + grace-period, and converts to JWKs.
     * The grace period allows keys that are technically expired but may still be in use by valid tokens to be retained until they are no longer needed for token verification.
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.JWKS_CACHE, key = "'jwksUrlKeys'")
    public List<JWK> getJwksUrlKeys() {
        log.debug("Loading JWKS URL keys from database");
        Instant graceCutoff = Instant.now().minus(keyGracePeriod);
        return jwksRepository.findAll().stream()
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(graceCutoff))
                .map(JwksDatabaseService::toPublicJwk)
                .toList();
    }

    @Override
    public Optional<JwkPublicKey> findByKid(String kid) {
        return jwksRepository.findByKid(kid);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.JWKS_CACHE, allEntries = true)
    public void save(JwkPublicKey key) {
        jwksRepository.save(key);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.JWKS_CACHE, allEntries = true)
    public void deleteByKid(String kid) {
        jwksRepository.deleteByKid(kid);
    }

    private static JWK toPublicJwk(JwkPublicKey key) {
        RSAKey.Builder builder = new RSAKey.Builder(new Base64URL(key.getN()), new Base64URL(key.getE()))
                .keyID(key.getKid());
        if (key.getKeyUse() != null) {
            builder.keyUse(new KeyUse(key.getKeyUse()));
        }
        if (key.getAlg() != null) {
            builder.algorithm(JWSAlgorithm.parse(key.getAlg()));
        }
        return builder.build();
    }
}
