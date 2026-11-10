// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.MemberViewTeam;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.TeamMemberViewEntity;
import org.mapstruct.Context;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = MemberViewAuthClientMapper.class)
@DecoratedWith(MemberViewTeamMapperDecorator.class)
public interface MemberViewTeamMapper {
    @Mapping(source = "id.team", target = ".")
    @Mapping(source = "role", target = "role")
    @Mapping(target = "memberCount", ignore = true) // custom optional mapping handled in service
    @Mapping(source = "id.team.teamAuthClients", target = "authClients")
    @Mapping(target = "slackChannelName", ignore = true)
    @Mapping(target = "teamRollup", ignore = true)
    @Mapping(target = "availableClouds", ignore = true)
    MemberViewTeam toDto(TeamMemberViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes, @Context CyclicGraphContext context);
}
