// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

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
public class ChangeEventView implements Serializable {

    private Long eventId;
    private Integer version;
    private ChangeEventType eventType;
    private ChangeEventAction eventAction;
    private Long entityId;
    private ChangeEventStatus syncStatus;
    private ChangeEventDetails changes;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
}
