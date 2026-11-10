// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.model.TeamView;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import org.mapstruct.Context;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {TeamViewMemberMapper.class, TeamViewAuthClientMapper.class})
@DecoratedWith(TeamViewMapperDecorator.class)
public interface TeamViewMapper {
    @Mapping(source = "teamMembers", target = "members")
    @Mapping(source = "teamAuthClients", target = "authClients")
    @Mapping(target = "slackChannelName", ignore = true)
    @Mapping(target = "teamRollup", ignore = true)
    @Mapping(target = "availableClouds", ignore = true)
    TeamView toDto(TeamViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes, @Context CyclicGraphContext context);

    @MappingIgnore
    default TeamView toDto(TeamViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes) {
        return toDto(entity, oAuthClients, decoratorAttributes, new CyclicGraphContext());
    }
}
