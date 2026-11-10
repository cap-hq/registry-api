// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.User;

public final class MemberSyncDecisionEngine {

    private MemberSyncDecisionEngine() {
    }

    /**
     * Decides the sync action for a member based on Azure user state and cleanup policy.
     *
     * @param user Azure user (null if not found)
     * @param cleanupEnabled whether to remove members no longer in Azure
     * @return UPDATE if user exists, enabled, and has employeeId; REMOVE if cleanup enabled and user absent/disabled; SKIP otherwise
     */
    public static MemberSyncAction decide(User user, boolean cleanupEnabled) {
        if (user != null && user.isAccountEnabled() && user.getEmployeeId() != null) {
            return MemberSyncAction.UPDATE;
        }
        if (cleanupEnabled) {
            return MemberSyncAction.REMOVE;
        }
        return MemberSyncAction.SKIP;
    }
}
