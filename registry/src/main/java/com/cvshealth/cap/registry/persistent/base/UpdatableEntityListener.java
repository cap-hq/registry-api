// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent.base;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UpdatableEntityListener {
    @PrePersist
    @PreUpdate
    private void applyUpdateValues(UpdatableEntity updatableEntity) {
        updatableEntity.setUpdateUser(SecurityHelper.getCurrentUserExternalAlias());
        updatableEntity.setUpdateTimestamp(Instant.now());
    }
}
