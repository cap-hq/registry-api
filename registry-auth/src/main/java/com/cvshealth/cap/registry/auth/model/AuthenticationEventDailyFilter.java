// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@ValidDateRange
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationEventDailyFilter {
    private String clientId;
    private String clientName;
    private String grantType;
    private Boolean success;
    private String principalOid;
    private String externalAlias;
    private LocalDate eventDate;
    private LocalDate fromDate;
    private LocalDate toDate;
}
