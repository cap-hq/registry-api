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
public class ApplicationTransferViewEntity extends IDUpdatableEntity {

    @ManyToOne
    @JoinColumn(name = "application_id", referencedColumnName = "id", nullable = false)
    private ApplicationEntity application;

    @ManyToOne
    @JoinColumn(name = "source_team_id", referencedColumnName = "id", nullable = false)
    private TeamEntity sourceTeam;

    @ManyToOne
    @JoinColumn(name = "receive_team_id", referencedColumnName = "id", nullable = false)
    private TeamEntity receiveTeam;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ApplicationTransferStatus status;

    @Column(name = "comments")
    private String comments;
}
