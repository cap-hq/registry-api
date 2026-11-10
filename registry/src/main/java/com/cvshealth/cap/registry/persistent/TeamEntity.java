// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.persistent.base.IDUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Table(name = "team")
public class TeamEntity extends IDUpdatableEntity implements IdentifiableEntity {
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "name_normalized", nullable = false, unique = true)
    private String nameNormalized;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TeamType type;

    @Column(name = "description")
    private String description;

    @Column(name = "email_list")
    private String emailList;

    @Column(name = "slack_channel")
    private String slackChannel;

    @Column(name = "external_name")
    private String externalName;
}
