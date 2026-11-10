// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.AuthClientViewTeam;
import com.cvshealth.cap.registry.persistent.TeamAuthClientViewEntity;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AuthClientViewMemberMapper.class)
public interface AuthClientViewTeamMapper {
    @Mapping(source = "id.team", target = ".")
    @Mapping(source = "id.team.teamMembers", target = "members")
    @Mapping(target = "role", ignore = true)
    AuthClientViewTeam toDto(TeamAuthClientViewEntity entity, @Context CyclicGraphContext context);

    @BeforeMapping
    default void beforeMapping(TeamAuthClientViewEntity entity, @Context CyclicGraphContext context) {
        TeamViewEntityUtils.sortChildren(entity.getId().getTeam());
    }
}
