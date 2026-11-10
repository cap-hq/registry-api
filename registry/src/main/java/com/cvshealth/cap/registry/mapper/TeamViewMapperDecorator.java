// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.config.AuthClientConfig;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.model.TeamView;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
public abstract class TeamViewMapperDecorator implements TeamViewMapper {

    static final String SLACK_CHANNEL_NAME_ATTRIBUTE = "TeamView.slackChannelName";
    static final String TEAM_ROLLUP_ATTRIBUTE = "TeamView.teamRollup";
    static final String PAGINATED_TEAM_ROLLUP_ATTRIBUTE = "PaginatedTeamResponse.teams/TeamView.teamRollup";
    static final String TEAM_AVAILABLE_CLOUDS_ATTRIBUTE = "TeamView.availableClouds";
    static final String PAGINATED_TEAM_AVAILABLE_CLOUDS_ATTRIBUTE = "PaginatedTeamResponse.teams/TeamView.availableClouds";
    public static final Set<String> DECORATOR_ATTRIBUTES = Set.of(
            SLACK_CHANNEL_NAME_ATTRIBUTE, TEAM_ROLLUP_ATTRIBUTE, PAGINATED_TEAM_ROLLUP_ATTRIBUTE,
            TEAM_AVAILABLE_CLOUDS_ATTRIBUTE, PAGINATED_TEAM_AVAILABLE_CLOUDS_ATTRIBUTE
    );

    @Autowired
    @Qualifier("delegate")
    private TeamViewMapper delegate;

    @Autowired
    private SlackChannelNameMapper slackChannelNameMapper;

    @Autowired
    private TeamRollupMapperDecorator teamRollupMapperDecorator;

    @Autowired
    private TeamCloudsMapperDecorator teamCloudsMapperDecorator;

    @Autowired
    private AuthClientConfig authClientConfig;

    @Override
    public TeamView toDto(TeamViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes, @Context CyclicGraphContext context) {
        TeamViewEntityUtils.sortChildren(entity);
        TeamView team = delegate.toDto(entity, oAuthClients, decoratorAttributes, context);
        if (team.getAuthClients() != null) {
            team.getAuthClients().forEach(ac -> ac.setRole(authClientConfig.getTeamRole()));
        }
        if (decoratorAttributes != null && StringUtils.isNotEmpty(team.getSlackChannel()) && decoratorAttributes.contains(SLACK_CHANNEL_NAME_ATTRIBUTE)) {
            team.setSlackChannelName(slackChannelNameMapper.mapChannelName(team.getSlackChannel()));
        }
        if (decoratorAttributes != null && (decoratorAttributes.contains(TEAM_ROLLUP_ATTRIBUTE) || decoratorAttributes.contains(PAGINATED_TEAM_ROLLUP_ATTRIBUTE))) {
            if (teamRollupMapperDecorator.supports(TeamView.class)) {
                teamRollupMapperDecorator.map(team);
            }
        }
        if (decoratorAttributes != null && (decoratorAttributes.contains(TEAM_AVAILABLE_CLOUDS_ATTRIBUTE) || decoratorAttributes.contains(PAGINATED_TEAM_AVAILABLE_CLOUDS_ATTRIBUTE))) {
            if (teamCloudsMapperDecorator.supports(TeamView.class)) {
                teamCloudsMapperDecorator.map(team);
            }
        }
        return team;
    }
}
