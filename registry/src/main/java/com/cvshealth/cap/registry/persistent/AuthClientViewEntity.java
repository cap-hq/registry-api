// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.persistent.base.IDUpdatableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

import java.util.List;

import static com.cvshealth.cap.registry.persistent.base.PersistenceConstants.BATCH_SIZE_ENTITY_QUERY;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Table(name = "auth_client")
public class AuthClientViewEntity extends IDUpdatableEntity {

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_version", nullable = false)
    private Integer clientVersion;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "id.authClient", cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    @BatchSize(size = BATCH_SIZE_ENTITY_QUERY)
    private List<TeamAuthClientViewEntity> teamAuthClients;

    public String getTeamName() {
        if (teamAuthClients == null || teamAuthClients.size() != 1) { // currently only one auth client to team association is allowed
            throw new IllegalStateException("AuthClient Data integrity error; Missing associated TeamAuthClient");
        }
        return teamAuthClients.get(0).getId().getTeam().getName();
    }
}
