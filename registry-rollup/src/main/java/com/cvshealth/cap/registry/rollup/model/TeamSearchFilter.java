// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamSearchFilter extends NameSearchFilter {
    private Boolean includePersonalTeams;
    private TeamType teamType;

    @AssertTrue(message = "teamType and includePersonalTeams cannot both be specified")
    public boolean isTeamTypeAndIncludePersonalTeamsValid() {
        return teamType == null || !Boolean.TRUE.equals(includePersonalTeams);
    }
}
