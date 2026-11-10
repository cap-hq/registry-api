// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.persistent.base.IDUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "auth_client")
public class AuthClientEntity extends IDUpdatableEntity implements IdentifiableEntity {

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_version", nullable = false)
    private Integer clientVersion;

    @Column(name = "description")
    private String description;

    @Override
    public String getName() {
        return clientId;
    }
}
