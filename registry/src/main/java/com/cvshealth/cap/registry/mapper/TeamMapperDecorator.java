// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.Team;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public abstract class TeamMapperDecorator implements TeamMapper {

    @Autowired
    @Qualifier("delegate")
    private TeamMapper delegate;

    @Autowired
    private SlackChannelNameMapper slackChannelNameMapper;

    @Override
    public Team toDto(TeamEntity entity) {
        Team team = delegate.toDto(entity);
        if (StringUtils.isNotEmpty(team.getSlackChannel())) {
            team.setSlackChannelName(slackChannelNameMapper.mapChannelName(team.getSlackChannel()));
        }
        return team;
    }
}
