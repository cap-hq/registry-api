// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.Getter;

@Getter
public enum AuditEntityType {

    APPLICATION("application"),
    API("api"),
    API_CONSUMER("api consumer"),
    AUTH_CLIENT("auth client"),
    MEMBER("member"),
    TEAM("team"),
    BUSINESS_LINE("line of business");

    private final String displayName;

    AuditEntityType(String displayName) {
        this.displayName = displayName;
    }
}
