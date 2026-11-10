// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link JWKSource} backed by {@link JwkSetHolder}.
 * <p>
 * Because the holder keeps keys newest-first, {@code NimbusJwtEncoder} (which picks the first
 * algorithm-matched result) always signs with the current key. {@code NimbusJwtDecoder} resolves
 * the exact key via the {@code kid} header, so pre-rotation tokens remain verifiable as long as
 * the old key is still present in the holder.
 */
@Component
@RequiredArgsConstructor
public class RotatingJwkSource implements JWKSource<SecurityContext> {

    private final JwkSetHolder jwkSetHolder;

    @Override
    public List<JWK> get(com.nimbusds.jose.jwk.JWKSelector selector, SecurityContext context) {
        return selector.select(jwkSetHolder.getAll());
    }
}
