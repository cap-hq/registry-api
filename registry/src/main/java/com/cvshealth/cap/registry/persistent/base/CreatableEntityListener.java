// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent.base;

import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CreatableEntityListener {
    @PrePersist
    private void applyCreateValues(CreatableEntity creatableEntity) {
        if (creatableEntity.getCreateUser() == null) {
            creatableEntity.setCreateUser(SecurityHelper.getCurrentUserExternalAlias());
        }

        if (creatableEntity.getCreateTimestamp() == null) {
            creatableEntity.setCreateTimestamp(Instant.now());
        }
    }
}
