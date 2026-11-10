// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.model.ApplicationTransferStatus;
import com.cvshealth.cap.registry.persistent.base.IDUpdatableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Table(name = "application_transfer")
public class ApplicationTransferEntity extends IDUpdatableEntity {
    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "source_team_id", nullable = false)
    private Long sourceTeamId;

    @Column(name = "receive_team_id", nullable = false)
    private Long receiveTeamId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ApplicationTransferStatus status;

    @Column(name = "comments")
    private String comments;
}
