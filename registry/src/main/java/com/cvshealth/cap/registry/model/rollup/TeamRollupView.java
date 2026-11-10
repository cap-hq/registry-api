// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.rollup;

import com.cvshealth.cap.registry.annotation.FeatureFlag;
import com.cvshealth.cap.registry.model.TeamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamRollupView {
    private Long id;
    private String name;
    private TeamType type;
    private List<UserView> managers;
    @FeatureFlag("app.admin.line-business.enabled")
    private LineOfBusinessView lineOfBusiness;
}
