// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@MappedSuperclass
@EntityListeners(UpdatableEntityListener.class)
public class UpdatableEntity extends CreatableEntity {
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "update_user")
    private String updateUser;

    @Column(name = "update_ts")
    private Instant updateTimestamp;
}
