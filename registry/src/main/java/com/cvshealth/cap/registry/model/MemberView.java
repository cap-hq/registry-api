// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberView implements IdentifiableUser, Serializable {
    private Long id;
    private String employeeId;
    private Integer version;
    private String externalId;
    private String externalAlias;
    private String email;
    private String name;
    private String lastName;
    private String firstName;
    private String managerExternalId;
    private List<MemberViewTeam> teams;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
}
