// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OAuthClientExpiration implements Serializable {
    private String clientId;
    private String clientSecret;
    private Instant clientIdIssuedAt;
    private Instant clientSecretExpiresAt;
    private String clientName;
    private Integer ageInDays;
    private Integer expirationInDays;
    private Instant lastAccessTokenIssuedAt;
}
