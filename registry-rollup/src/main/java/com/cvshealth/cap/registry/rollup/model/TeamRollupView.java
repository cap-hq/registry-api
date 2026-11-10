// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model;

import com.cvshealth.cap.registry.rollup.annotation.FeatureFlag;
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
public class TeamRollupView implements LineOfBusinessAwareRollupView, Serializable {
    private Long id;
    private Integer version;
    private String name;
    private TeamType type;
    private String description;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
    private List<UserView> managers;
    @FeatureFlag("app.line-business.enabled")
    private LineOfBusinessView lineOfBusiness;
}
