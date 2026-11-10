// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.rollup.TeamRollupView;

public interface TeamRollupAwareTeamView {
    String getName();
    TeamRollupView getTeamRollup();
    void setTeamRollup(TeamRollupView teamRollup);
}
