// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationViewAuthClient implements Serializable {
    private Long id;
    private String clientId;
    private Integer clientVersion;
    private String clientSecret;
    private Instant clientSecretExpiresAt;
    private String description;
    private AuthClientType type;
    private TeamMemberRole role;
    private List<String> redirectUrls;
    private Long accessTokenExpireMinutes;
    private Long refreshTokenExpireMinutes;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
    private Instant lastAccessTokenIssuedAt;
}
