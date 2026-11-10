// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwkPublicKey {
    private String  kid;
    private String  kty;
    private String  keyUse;
    private String  alg;
    private String  n;
    private String  e;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant createdAt;
}
