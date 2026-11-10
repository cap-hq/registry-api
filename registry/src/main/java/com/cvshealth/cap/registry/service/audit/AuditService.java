// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.model.audit.AuditEntry;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;

public interface AuditService {
    
    void publish(AuditEntry auditEntry, ChangeEventDetails changeEventDetails);

    default void publish(AuditEntry auditEntry) {
        publish(auditEntry, null);
    }
}
