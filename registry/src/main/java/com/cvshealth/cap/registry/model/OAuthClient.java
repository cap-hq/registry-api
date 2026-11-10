// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OAuthClient implements Serializable {
    private String id;
    private String clientId;
    private String clientSecret;
    private Instant clientSecretExpiresAt;
    private String clientName;
    private String description;
    private AuthClientType type;
    private List<String> redirectUrls;
    private Long accessTokenExpireMinutes;
    private Long refreshTokenExpireMinutes;
    private Instant lastAccessTokenIssuedAt;
}
