// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.TeamRoleView;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TeamRoleViewMapper {
    @Mapping(source = "teamMember.id.memberId", target = "memberId")
    @Mapping(source = "teamMember.role", target = "role")
    @Mapping(source = "team.id", target = "id")
    @Mapping(source = "team.name", target = "name")
    @Mapping(source = "team.type", target = "type")
    @Mapping(source = "team.version", target = "version")
    @Mapping(source = "team.createUser", target = "createUser")
    @Mapping(source = "team.createTimestamp", target = "createTimestamp")
    @Mapping(source = "team.updateUser", target = "updateUser")
    @Mapping(source = "team.updateTimestamp", target = "updateTimestamp")
    @Mapping(target = "slackChannelName", ignore = true)
    TeamRoleView toDto(TeamMemberEntity teamMember, TeamEntity team);
}
