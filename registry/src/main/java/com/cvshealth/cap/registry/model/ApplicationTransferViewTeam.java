// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationTransferViewTeam implements IdentifiableEntity, Serializable {
    private Long id;
    private Integer version;
    private String name;
    private TeamType type;
    private String description;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
    private String emailList;
    private String slackChannel;
    private String externalName;
}
