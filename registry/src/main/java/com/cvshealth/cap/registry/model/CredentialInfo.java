// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public record CredentialInfo(String credentialName, OffsetDateTime startDateTime, OffsetDateTime endDateTime, String hint, boolean active) {

    public long ageInDays() {
        return ChronoUnit.DAYS.between(startDateTime, OffsetDateTime.now());
    }

    public long expirationInDays() {
        return ChronoUnit.DAYS.between(OffsetDateTime.now(), endDateTime);
    }
}
