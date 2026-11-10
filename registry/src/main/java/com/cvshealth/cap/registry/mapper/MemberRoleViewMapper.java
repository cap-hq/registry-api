// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.MemberRoleView;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberRoleViewMapper {
    @Mapping(source = "teamMember.id.teamId", target = "teamId")
    @Mapping(source = "teamMember.role", target = "role")
    @Mapping(source = "member.id", target = "id")
    @Mapping(source = "member.name", target = "name")
    @Mapping(source = "member.email", target = "email")
    @Mapping(source = "member.externalId", target = "externalId")
    @Mapping(source = "member.version", target = "version")
    @Mapping(source = "member.createUser", target = "createUser")
    @Mapping(source = "member.createTimestamp", target = "createTimestamp")
    @Mapping(source = "member.updateUser", target = "updateUser")
    @Mapping(source = "member.updateTimestamp", target = "updateTimestamp")
    MemberRoleView toDto(TeamMemberEntity teamMember, MemberEntity member);
}
