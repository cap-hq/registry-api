// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwkPublicKeyTest {

    @Test
    void builder_setsAllFields() {
        Instant issuedAt  = Instant.parse("2025-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant createdAt = Instant.parse("2025-01-01T00:00:01Z");

        JwkPublicKey key = JwkPublicKey.builder()
                .kid("test-kid")
                .kty("RSA")
                .keyUse("sig")
                .alg("RS256")
                .n("sampleModulus")
                .e("AQAB")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();

        assertThat(key.getKid()).isEqualTo("test-kid");
        assertThat(key.getKty()).isEqualTo("RSA");
        assertThat(key.getKeyUse()).isEqualTo("sig");
        assertThat(key.getAlg()).isEqualTo("RS256");
        assertThat(key.getN()).isEqualTo("sampleModulus");
        assertThat(key.getE()).isEqualTo("AQAB");
        assertThat(key.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(key.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(key.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void builder_allowsNullOptionalFields() {
        JwkPublicKey key = JwkPublicKey.builder()
                .kid("test-kid")
                .kty("RSA")
                .n("sampleModulus")
                .e("AQAB")
                .issuedAt(Instant.now())
                .build();

        assertThat(key.getKeyUse()).isNull();
        assertThat(key.getAlg()).isNull();
        assertThat(key.getExpiresAt()).isNull();
        assertThat(key.getCreatedAt()).isNull();
    }
}
