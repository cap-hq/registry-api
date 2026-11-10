// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

/**
 * Client privilege levels in ascending order of access.
 * TEAM < ORGANIZATION < ENTERPRISE
 */
public enum ClientPrivilegeType {
    TEAM, ORGANIZATION, ENTERPRISE;

    /**
     * Returns the higher privilege between this and another.
     */
    public ClientPrivilegeType max(ClientPrivilegeType other) {
        if (other == null) {
            return this;
        }
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
