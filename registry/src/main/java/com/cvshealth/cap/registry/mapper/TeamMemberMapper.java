// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.TeamMember;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TeamMemberMapper {
    @Mapping(target = "id.teamId", source = "teamId")
    @Mapping(target = "id.memberId", source = "memberId")
    TeamMemberEntity toEntity(TeamMember dto);

    @Mapping(target = "teamId", source = "id.teamId")
    @Mapping(target = "memberId", source = "id.memberId")
    TeamMember toDto(TeamMemberEntity entity);
}
