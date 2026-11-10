// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.model.JwkPublicKey;

import com.nimbusds.jose.jwk.JWK;

import java.util.List;
import java.util.Optional;

public interface JwksService {

    List<JWK> getPublicKeys();

    List<JWK> getJwksUrlKeys();

    Optional<JwkPublicKey> findByKid(String kid);

    void save(JwkPublicKey key);

    void deleteByKid(String kid);
}

