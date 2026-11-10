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
@Table(name = "member")
public class MemberViewEntity extends IDUpdatableEntity {

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "external_alias", nullable = false, unique = true)
    private String externalAlias;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "manager_external_id")
    private String managerExternalId;

    @Column(name = "employee_id")
    private String employeeId;

    @OneToMany(mappedBy = "id.member", cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    @BatchSize(size = BATCH_SIZE_ENTITY_QUERY)
    private List<TeamMemberViewEntity> teamMemberships;
}
