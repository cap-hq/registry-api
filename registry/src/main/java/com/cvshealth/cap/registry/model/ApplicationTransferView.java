// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApplicationTransferView implements Serializable {
    private Long id;
    private ApplicationTransferViewApplication application;
    private ApplicationTransferViewTeam sourceTeam;
    private ApplicationTransferViewTeam receiveTeam;
    private ApplicationTransferStatus status;
    private String comments;
    private Integer version;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
}
