// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwksRepositoryTests extends RegistryIntegrationTests {

    @Autowired
    private JwksRepository jwksRepository;

    @BeforeEach
    void cleanUp() {
        // ensure each test starts with an empty table
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                (org.springframework.jdbc.core.JdbcTemplate) org.springframework.test.util.ReflectionTestUtils
                        .getField(jwksRepository, "jdbcTemplate");
        jdbc.update("DELETE FROM oauth2_jwks");
    }

    @Test
    void findAll_whenTableIsEmpty_returnsEmptyList() {
        List<JwkPublicKey> keys = jwksRepository.findAll();
        assertThat(keys).isEmpty();
    }

    @Test
    void save_thenFindByKid_returnsKey() {
        JwkPublicKey key = buildKey("kid-1", Instant.now().truncatedTo(ChronoUnit.MILLIS), null);

        jwksRepository.save(key);

        Optional<JwkPublicKey> found = jwksRepository.findByKid("kid-1");
        assertThat(found).isPresent();
        assertThat(found.get().getKid()).isEqualTo("kid-1");
        assertThat(found.get().getKty()).isEqualTo("RSA");
        assertThat(found.get().getKeyUse()).isEqualTo("sig");
        assertThat(found.get().getAlg()).isEqualTo("RS256");
        assertThat(found.get().getN()).isEqualTo("sampleModulus");
        assertThat(found.get().getE()).isEqualTo("AQAB");
        assertThat(found.get().getIssuedAt()).isNotNull();
        assertThat(found.get().getExpiresAt()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void save_withExpiresAt_roundTrips() {
        Instant issuedAt  = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = issuedAt.plus(365, ChronoUnit.DAYS);
        JwkPublicKey key  = buildKey("kid-exp", issuedAt, expiresAt);

        jwksRepository.save(key);

        Optional<JwkPublicKey> found = jwksRepository.findByKid("kid-exp");
        assertThat(found).isPresent();
        assertThat(found.get().getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void findByKid_whenNotPresent_returnsEmpty() {
        Optional<JwkPublicKey> found = jwksRepository.findByKid("does-not-exist");
        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllSavedKeys() {
        jwksRepository.save(buildKey("kid-a", Instant.now().truncatedTo(ChronoUnit.MILLIS), null));
        jwksRepository.save(buildKey("kid-b", Instant.now().truncatedTo(ChronoUnit.MILLIS), null));

        List<JwkPublicKey> keys = jwksRepository.findAll();
        assertThat(keys).hasSize(2);
        assertThat(keys).extracting(JwkPublicKey::getKid).containsExactlyInAnyOrder("kid-a", "kid-b");
    }

    @Test
    void findAllActive_excludesExpiredKeys() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        jwksRepository.save(buildKey("kid-active", Instant.now().truncatedTo(ChronoUnit.MILLIS), future));
        jwksRepository.save(buildKey("kid-expired", Instant.now().truncatedTo(ChronoUnit.MILLIS), past));
        jwksRepository.save(buildKey("kid-no-expiry", Instant.now().truncatedTo(ChronoUnit.MILLIS), null));

        List<JwkPublicKey> active = jwksRepository.findAllActive();
        assertThat(active).extracting(JwkPublicKey::getKid).containsExactlyInAnyOrder("kid-active", "kid-no-expiry");
    }

    @Test
    void save_whenDuplicateKid_throwsException() {
        JwkPublicKey key = buildKey("kid-dup", Instant.now().truncatedTo(ChronoUnit.MILLIS), null);
        jwksRepository.save(key);

        assertThatThrownBy(() -> jwksRepository.save(key))
                .isInstanceOf(Exception.class);
    }

    private JwkPublicKey buildKey(String kid, Instant issuedAt, Instant expiresAt) {
        return JwkPublicKey.builder()
                .kid(kid)
                .kty("RSA")
                .keyUse("sig")
                .alg("RS256")
                .n("sampleModulus")
                .e("AQAB")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
    }
}
