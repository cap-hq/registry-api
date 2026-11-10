// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
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
public class AuthClient implements IdentifiableEntity, Serializable {
    private Long id;
    private String clientId;
    private Integer clientVersion;
    private String clientSecret;
    private Instant clientSecretExpiresAt;
    private String description;
    private AuthClientType type;
    private List<String> redirectUrls;
    private Long accessTokenExpireMinutes;
    private Long refreshTokenExpireMinutes;
    private Integer version;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
    private Instant lastAccessTokenIssuedAt;

    @Override
    public String getName() {
        return clientId;
    }
}
