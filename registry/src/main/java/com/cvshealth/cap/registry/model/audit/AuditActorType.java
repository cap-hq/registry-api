// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.Getter;

@Getter
public enum AuditActorType {

    USER(false),
    AUTH_CLIENT(false),
    APP_REGISTRY(true),
    UNKNOWN_USER(false);

    private final boolean systemUser;

    AuditActorType(boolean systemUser) {
        this.systemUser = systemUser;
    }

}