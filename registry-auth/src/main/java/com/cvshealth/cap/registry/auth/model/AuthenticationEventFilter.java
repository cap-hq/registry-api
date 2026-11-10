// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationEventFilter {
    private String clientId;
    private String clientName;
    private String grantType;
    private Boolean success;
    private String principalOid;
    private String externalAlias;
    @NotNull
    private OffsetDateTime fromDateTime;
    @NotNull
    private OffsetDateTime toDateTime;
}
