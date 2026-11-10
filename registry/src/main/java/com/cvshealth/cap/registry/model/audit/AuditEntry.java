// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditEntry {
    private AuditActor actor;
    private AuditAction actionType;
    private AuditEntity subjectEntity;
    private AuditEntity sourceEntity;
    private AuditEntity targetEntity;
}
