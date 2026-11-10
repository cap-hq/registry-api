// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ClientRequest {
    private ClientType type;
    private String clientId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant clientSecretExpiresAt;
    private List<String> redirectUrls;
    private Long accessTokenExpireMinutes;
    private Long refreshTokenExpireMinutes;
}

