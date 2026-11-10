// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.annotation.FeatureFlag;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
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
public class TeamView implements IdentifiableEntity, TeamRollupAwareTeamView, TeamAvailableCloudsAwareTeamView, TeamMemberViewAware, Serializable {
    private Long id;
    private Integer version;
    private String name;
    private TeamType type;
    private String description;
    private List<TeamViewApplication> applications;
    private List<TeamViewMember> members;
    private List<TeamViewAuthClient> authClients;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
    private String emailList;
    private String slackChannel;
    private String slackChannelName;
    private String externalName;
    @FeatureFlag("app.team.rollup.enabled")
    private TeamRollupView teamRollup;
    @FeatureFlag("app.team.available-clouds.enabled")
    private List<CloudTenantView> availableClouds;
}
