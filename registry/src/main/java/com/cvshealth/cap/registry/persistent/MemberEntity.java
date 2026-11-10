// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
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
@Table(name = "member")
public class MemberEntity extends IDUpdatableEntity implements IdentifiableUser {

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
}
