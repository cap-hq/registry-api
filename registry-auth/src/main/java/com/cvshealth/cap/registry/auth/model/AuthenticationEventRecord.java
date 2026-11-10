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
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationEventRecord {
    private Long id;
    private String clientId;
    private String clientName;
    private String grantType;
    private String principalName;
    private String principalOid;
    private String agent;
    private String externalAlias;
    private Instant authenticatedAt;
    private Boolean success;
    private String eventAttributes;
}
