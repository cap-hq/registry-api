// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.Getter;

@Getter
public enum AuditAction {
    ADD_TO("added"),
    CREATE("created"),
    CREATE_TRANSFER("created transfer"),
    DELETE("deleted"),
    REMOVE_FROM("removed"),
    RESET("reset"),
    UPDATE("updated"),
    UPDATE_ON("updated"),
    UPDATE_TRANSFER("updated transfer");

    private final String completedAction;

    AuditAction(String completedAction) {
        this.completedAction = completedAction;
    }
}
