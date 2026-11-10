// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
public class Member implements IdentifiableUser, Serializable {
    private Long id;
    private Integer version;
    private String externalId;
    private String externalAlias;
    private String email;
    private String name;
    private String lastName;
    private String firstName;
    private String managerExternalId;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
    private String employeeId;
}
