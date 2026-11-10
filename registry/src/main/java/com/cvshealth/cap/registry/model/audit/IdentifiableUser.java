// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

public interface IdentifiableUser {

    Long getId();
    String getExternalId();
    String getEmployeeId();
    String getExternalAlias();
    String getEmail();
}
