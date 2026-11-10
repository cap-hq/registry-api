// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthClientRedirectUriComplianceResult {
    private String clientId;
    private Integer clientVersion;
    private AuthClientType type;
    private List<String> teamNames;
    private List<AuthClientRedirectUriViolation> violations;
}
