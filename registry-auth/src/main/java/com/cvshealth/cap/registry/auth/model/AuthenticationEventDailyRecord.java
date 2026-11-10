// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationEventDailyRecord {
    private LocalDate eventDate;
    private String clientId;
    private String clientName;
    private String grantType;
    private String principalName;
    private String principalOid;
    private String agent;
    private String externalAlias;
    private Boolean success;
    private Long eventCount;
    private Instant firstAuthentication;
    private Instant lastAuthentication;
    private String eventDetails;
}
